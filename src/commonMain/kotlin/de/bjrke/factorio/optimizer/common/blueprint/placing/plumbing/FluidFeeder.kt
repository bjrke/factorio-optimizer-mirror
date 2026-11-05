package de.bjrke.factorio.optimizer.common.blueprint.placing.plumbing

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.FLUID_SOURCE_WIDTH
import de.bjrke.factorio.optimizer.common.blueprint.layout.canPlacePipes
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeFluidSource
import de.bjrke.factorio.optimizer.common.blueprint.placing.route.RouteRequest
import de.bjrke.factorio.optimizer.common.blueprint.placing.special.placePath
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.ImportExtension
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PortFlow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.blueprintMachineEdge
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.blueprintMachineEdgeFlow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.blueprintMachineInputPort
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.blueprintMachineOutputPort
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.prototypeFluidPorts
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.LayoutStage
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.PlacedMachine
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.Row
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.NORTH
import de.bjrke.factorio.optimizer.common.core.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.core.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.LineSide
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.manhattanDist
import de.bjrke.factorio.optimizer.common.core.model.neighbors

/** How many existing network points are tried before a fluid sink is reported unreachable. */
private const val MAX_ROUTE_STARTS = 8

/**
 * Supplies every fluid in the plan. A row whose machines take (or give) a fluid through a port the
 * table puts on the row's north (or south) edge gets one pipe **lane** on a row of its own beyond
 * the belts, sourced inline at its west end and reaching every port through an underground shaft;
 * that lane is never routed. A machine whose port is on a side edge, or that has no port table,
 * still takes the point-to-point route from a marker. The lanes and their markers go in before the
 * solid imports; the routed fallback follows them, so a solid import keeps the direct path and the
 * pipe route goes around it. The machine ports a lane carries are shared with the underground
 * stretcher through [fluidPorts].
 */
internal class FluidFeeder(
    stage: LayoutStage,
    private val beltEnd: BeltEnd,
    private val imports: ImportExtension,
    /** The machine-port tiles a fluid must reach; an underground run never swallows one. */
    private val fluidPorts: MutableSet<Coordinate>,
    private val failures: MutableList<String>,
) {
    private val board = stage.board
    private val plan = stage.plan
    private val logistics = stage.logistics
    private val rowPlacer = stage.rowPlacer
    private val router = stage.router
    private val lanes = FluidLaneFeed(board, imports, beltEnd, logistics)

    /** The routed fallback prepared by [placeImports], routed by [routeFluids]. */
    private var groups: List<PipeGroup> = emptyList()

    /**
     * Places every fluid lane and source marker and reserves the machine edges a lane cannot reach.
     * Routing those groups is left to [routeFluids], after the solid imports are laid.
     */
    fun placeImports() {
        // the row lanes go in first: they are placed before any routed pipe, so a route cannot take
        // a tile of one, and every port a lane reaches is left to the lane
        // a lane that could not reach a port leaves it free, so the loop below routes it
        LineSide.entries.forEach { side -> lanes.place(side, rowPlacer.placedRows) }
        // reserve every fluid's machine edge first, so two fluids never pick touching edges
        val targets = mutableMapOf<Material, MutableList<FluidSink>>()
        for (row in rowPlacer.placedRows) {
            for (machine in row.machines) {
                val fluids = lanes.liquidsOf(machine)
                // one port per fluid: a machine that already reserved a port for a fluid is fed
                val fed = mutableMapOf<Material, Coordinate>()
                for (material in fluids) {
                    val lanePort = lanes.portFor(row, machine, material, Io.INPUT)
                    if (lanePort != null) {
                        fed[material] = lanePort
                        continue
                    }
                    val port = fluidEdge(row, machine, material, fed.keys)
                    if (port == null) {
                        failures +=
                            "${material.name} to ${machine.production.factory.identifier} " +
                                "(no fluid port)"
                        continue
                    }
                    fed[material] = port
                    board.reserve(port, material)
                    board.setEdgeFluid(port, material)
                    fluidPorts += port
                    // every port reaching this point is not lane-fed, so it is routed
                    targets
                        .getOrPut(material) { mutableListOf() }
                        .add(FluidSink(port, portEdge(row, machine, port)))
                }
            }
        }
        // prepare a source per fluid up front, then route the groups nearest-first
        groups =
            targets.entries
                .sortedBy { it.key.name }
                .map { (material, sinks) ->
                    val first = sinks.first()
                    val source = fluidMarker(material, first.port, edge = first.edge, io = Io.INPUT)
                    board.reserve(source, material)
                    PipeGroup(material, source, sinks.map { it.port })
                }
                .sortedBy { it.nearestDistance }
    }

    /** Routes the fallback groups [placeImports] prepared, nearest-first. */
    fun routeFluids() {
        groups.forEach(::routePipeGroup)
    }

    /**
     * Joins every drain lane to the feed lane of the same fluid on the same row, preferring the
     * row's east side, so a machine's product supplies its own input instead of leaving the
     * blueprint. Runs after the other routes so the link can go around what they placed.
     */
    fun connectLanes() {
        for (connection in lanes.connections()) {
            if (connection.drainTail == connection.feedTail) continue
            val segment =
                fluidRoute(connection.drainTail, EAST, connection.feedTail, connection.material)
            if (segment == null) {
                failures +=
                    "${connection.material.name} from its drain lane " +
                        "(no pipe route to its feed lane)"
                continue
            }
            placePath(board, connection.material, listOf(connection.drainTail) + segment)
        }
    }

    /** Connects a fluid output pipe to each target machine, so its product has somewhere to go. */
    fun placeTargetOutput() {
        if (!logistics.isFluid(plan.target)) return
        val row = rowPlacer.producerRow(plan.target) ?: return
        val producer = row.plannedRow.producerOf(plan.target) ?: return
        for (machine in row.machines) {
            if (machine.production.recipe === producer.recipe) {
                connectTargetOutput(row, machine)
            }
        }
    }

    /** Routes the target fluid from one producer's output port to a marker beside it. */
    private fun connectTargetOutput(row: Row, machine: PlacedMachine) {
        // a lane on the machine's output edge already drains the product
        if (lanes.portFor(row, machine, plan.target, Io.OUTPUT) != null) return
        val port = blueprintMachineOutputPort(machine.production.factory) ?: return
        val coordinate =
            Coordinate(machine.left + port.tile.x, row.factoryTop + port.tile.y).takeIf {
                board.isFree(it) && !board.isReserved(it)
            } ?: return
        board.reserve(coordinate, plan.target)
        board.setEdgeFluid(coordinate, plan.target)
        fluidPorts += coordinate
        val source =
            fluidMarker(
                plan.target,
                coordinate,
                io = Io.OUTPUT,
                edge = portEdge(row, machine, coordinate),
            )
        board.reserve(source, plan.target)
        val segment = fluidRoute(source, EAST, coordinate, plan.target)
        if (segment == null) {
            failures +=
                "${plan.target.name} from ${machine.production.factory.identifier} " +
                    "(no pipe route)"
            return
        }
        placePath(board, plan.target, listOf(source) + segment)
    }

    /** One routed fluid sink: its machine port tile and the side of the machine that port is on. */
    private class FluidSink(val port: Coordinate, val edge: Direction)

    /** One fluid to lay: its marker tile, the pipe network grown from it and the taps to reach. */
    private class PipeGroup(
        val material: Material,
        val source: Coordinate,
        val sinks: List<Coordinate>,
    ) {
        val nearestDistance
            get() = sinks.minOf { it.manhattanDist(source) }
    }

    /**
     * Grows this fluid's network to every sink, each time tapping the nearest point that already
     * carries the fluid. A pipe connects on any side, so no splitter is needed.
     */
    private fun routePipeGroup(group: PipeGroup) {
        val network = mutableListOf(group.source)
        val pending = group.sinks.toMutableList()
        while (pending.isNotEmpty()) {
            val sink = pending.minBy { candidate -> network.minOf { it.manhattanDist(candidate) } }
            val candidates =
                network
                    .filter { it == group.source || board.entityAt(it) is PipeEntity }
                    .sortedBy { it.manhattanDist(sink) }
            var routed = false
            for (start in candidates.take(MAX_ROUTE_STARTS)) {
                if (start == sink) {
                    pending.remove(sink)
                    routed = true
                    break
                }
                val segment = fluidRoute(start, EAST, sink, group.material) ?: continue
                placePath(board, group.material, listOf(start) + segment)
                network += listOf(start) + segment
                pending.remove(sink)
                routed = true
                break
            }
            if (!routed) {
                failures += "${group.material.name} to $sink (no pipe route)"
                pending.remove(sink)
            }
        }
    }

    /**
     * The tiles of [machine] a fluid can enter through, in row coordinates: the ports its edge flow
     * leaves open to an inflow, so a top or both-way port. A machine without a port table falls
     * back to its left then right edge, which flows both ways.
     */
    private fun inletTiles(row: Row, machine: PlacedMachine): List<Coordinate> {
        val factory = machine.production.factory
        val ports =
            prototypeFluidPorts(factory)
                .filter {
                    blueprintMachineEdgeFlow(
                        factory,
                        it,
                        blueprintMachineEdge(factory, it),
                    ) != PortFlow.OUTPUT
                }
                .map { Coordinate(machine.left + it.tile.x, row.factoryTop + it.tile.y) }
                .distinct()
        // a machine without a port table takes fluid on either side edge
        return ports.ifEmpty {
            listOf(machine.left - 1, machine.left + row.width).flatMap { x ->
                (row.factoryTop until row.factoryTop + row.height).map { y -> Coordinate(x, y) }
            }
        }
    }

    /**
     * The tile a fluid pipe must occupy to feed the [material] a machine of [row] takes, or null
     * when this machine needs no further port. One port per fluid is the rule: [fed] are the fluids
     * this machine already reserved a port for, so a fluid among them is fed and a machine that
     * takes several fluids gets one port each (the chemical plant's two top corners). The first
     * fluid takes the machine's own input port, which the inserter placer keeps clear; a further
     * fluid moves on to the next free inlet.
     */
    private fun fluidEdge(
        row: Row,
        machine: PlacedMachine,
        material: Material,
        fed: Set<Material>,
    ): Coordinate? {
        if (material in fed) return null
        val inlets =
            inletTiles(row, machine).filter { board.edgeFluidAt(it) in listOf(null, material) }
        val preferred =
            blueprintMachineInputPort(machine.production.factory)?.let {
                Coordinate(machine.left + it.tile.x, row.factoryTop + it.tile.y)
            }
        return inlets.firstOrNull {
            it == preferred && board.isFree(it) && !board.isReserved(it)
        } ?: inlets.firstOrNull { board.isFree(it) && !board.isReserved(it) }
    }

    /**
     * Places a fluid marker and returns the free tile its pipe lane starts on. A sink marker takes
     * the fluid away instead of supplying it: its cheat pipe is unfiltered and deletes what enters.
     */
    private fun fluidMarker(
        material: Material,
        target: Coordinate,
        io: Io,
        /**
         * The side of the machine the target port is on; the marker row is searched away from it.
         */
        edge: Direction = SOUTH,
    ): Coordinate {
        // The connector sits just west of the sink, so the fluid only runs a short lane. A solid
        // import keeps a label at the far west and is trimmed after routing; a fluid route is not
        // straight, so its marker is placed near the machine from the start instead.
        val source = Coordinate(target.x - 1, target.y)
        // the connector tiles west of the source: a single infinity pipe or the input/label/output
        // triple that hides the fluid under the combinator (a chest cannot carry a fluid)
        val firstColumn =
            if (beltEnd == BeltEnd.INFINITY_CHEST) source.x - 1 else source.x - FLUID_SOURCE_WIDTH
        // keep the connector's buried run clear of any other fluid near the machine, and off a
        // marker row a previous import already claimed; search outward from the port, so a north
        // port keeps its marker north and a south port keeps it south
        val step = if (edge == NORTH) -1 else 1
        var row = target.y
        while (
            row in imports.markerRows ||
                !(firstColumn..source.x).all { board.canPlacePipes(Coordinate(it, row), material) }
        ) row += step
        imports.markerRows += row
        // the cheat variant replaces the label with a single infinity pipe: a filter supplies the
        // fluid, while an unfiltered pipe deletes it, which is what a sink needs
        board.placeFluidSource(beltEnd, Coordinate(source.x - 1, row), material, io)
        imports.compactionWestBound = maxOf(imports.compactionWestBound, target.x)
        return Coordinate(source.x, row)
    }

    private fun fluidRoute(
        from: Coordinate,
        direction: Direction,
        target: Coordinate,
        fluid: Material,
    ) =
        router.route(
            RouteRequest(
                from,
                direction,
                target,
                { tile, _ -> tile == target || pipeAllowed(tile, fluid) },
                router.pipeProfile,
                // a segment grows out of an existing plain pipe, which cannot become a tunnel end:
                // it has to leave the network on the surface for one tile before it may dive
                strictStart = true,
            ),
        )

    /**
     * Whether a routed pipe of [fluid] may use [coordinate]: the tile can take a pipe of that
     * fluid, no other route has reserved it, and no reserved edge beside it belongs to a different
     * fluid.
     */
    private fun pipeAllowed(coordinate: Coordinate, fluid: Material) =
        board.canPlacePipes(coordinate, fluid) &&
            !board.isReserved(coordinate) &&
            coordinate.neighbors.none {
                val reservedFor = board.edgeFluidAt(it)
                reservedFor != null && reservedFor != fluid
            }
}

/**
 * The side of [machine] the port tile [port] sits on: a north port is above the row, a south port
 * below it, and a west/east port beside it. The marker of a routed fluid goes outward on that side,
 * so a north-edge import is fed from the north instead of around the row.
 */
private fun portEdge(row: Row, machine: PlacedMachine, port: Coordinate) =
    when {
        port.y < row.factoryTop -> NORTH
        port.y >= row.factoryTop + row.height -> SOUTH
        port.x < machine.left -> WEST
        else -> EAST
    }
