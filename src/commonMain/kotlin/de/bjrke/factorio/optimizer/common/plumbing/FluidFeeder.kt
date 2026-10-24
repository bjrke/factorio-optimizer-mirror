package de.bjrke.factorio.optimizer.common.plumbing

import de.bjrke.factorio.optimizer.common.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.layout.canPlacePipes
import de.bjrke.factorio.optimizer.common.layout.endName
import de.bjrke.factorio.optimizer.common.layout.entity.PipeEntity
import de.bjrke.factorio.optimizer.common.layout.entity.Signal
import de.bjrke.factorio.optimizer.common.layout.markTunnel
import de.bjrke.factorio.optimizer.common.layout.placeEnd
import de.bjrke.factorio.optimizer.common.layout.placeInfinityPipe
import de.bjrke.factorio.optimizer.common.layout.placeMarker
import de.bjrke.factorio.optimizer.common.layout.placePipe
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Io.INPUT
import de.bjrke.factorio.optimizer.common.model.Io.OUTPUT
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.manhattanDist
import de.bjrke.factorio.optimizer.common.model.neighbors
import de.bjrke.factorio.optimizer.common.plan.ImportExtension
import de.bjrke.factorio.optimizer.common.plan.PortFlow
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineEdge
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineEdgeFlow
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineFluidPorts
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineInputPort
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineOutputPort
import de.bjrke.factorio.optimizer.common.planning.LayoutStage
import de.bjrke.factorio.optimizer.common.planning.LineSide
import de.bjrke.factorio.optimizer.common.planning.PlacedMachine
import de.bjrke.factorio.optimizer.common.planning.Row
import de.bjrke.factorio.optimizer.common.planning.RowFluidLane
import de.bjrke.factorio.optimizer.common.route.RouteRequest
import de.bjrke.factorio.optimizer.common.special.placePath

/** The tiles of a label connector west of the source: input end, marker and output end. */
private const val PIPE_CONNECTOR_WIDTH = 3

/** How many existing network points are tried before a fluid sink is reported unreachable. */
private const val MAX_ROUTE_STARTS = 8

/**
 * Supplies every fluid in the plan. A row whose machines take (or give) a fluid through a port the
 * table puts on the row's north (or south) edge gets one pipe **lane** on a row of its own beyond
 * the belts, sourced inline at its west end and reaching every port through an underground shaft;
 * that lane is never routed. A machine whose port is on a side edge, or that has no port table,
 * still takes the point-to-point route from a marker. Pipes run before the deliveries, so their
 * cheap underground runs are in place when the belts route. The machine ports a lane carries are
 * shared with the underground stretcher through [fluidPorts].
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

    /** Reserves every fluid machine edge, then routes each fluid from its marker nearest-first. */
    fun feed() {
        // the row lanes go in first: they are placed before any routed pipe, so a route cannot take
        // a tile of one, and every port a lane reaches is left to the lane
        val laneModel = RowFluidLane(logistics)
        // a lane that could not reach a port leaves it free, so the loop below routes it
        LineSide.entries.forEach { side -> lanes.place(laneModel, side, rowPlacer.placedRows) }
        // reserve every fluid's machine edge first, so two fluids never pick touching edges
        val targets = mutableMapOf<Material, MutableList<Coordinate>>()
        for (row in rowPlacer.placedRows) {
            for (machine in row.machines) {
                val plannedRecipe = machine.plannedRecipe
                val fluids = lanes.liquidsOf(machine)
                // one port per fluid: a machine that already reserved a port for a fluid is fed
                val fed = mutableMapOf<Material, Coordinate>()
                for (material in fluids) {
                    val lanePort = lanes.portFor(row, machine, material)
                    if (lanePort != null) {
                        fed[material] = lanePort
                        continue
                    }
                    val port = fluidEdge(row, machine, material, fed.keys)
                    if (port == null) {
                        failures +=
                            "${material.name} to ${plannedRecipe.factory.identifier} " +
                                "(no fluid port)"
                        continue
                    }
                    fed[material] = port
                    board.reserve(port, material)
                    board.setEdgeFluid(port, material)
                    fluidPorts += port
                    // every port reaching this point is not lane-fed, so it is routed
                    targets.getOrPut(material) { mutableListOf() }.add(port)
                }
            }
        }
        // prepare a source per fluid up front, then route the groups nearest-first
        val groups =
            targets.entries
                .sortedBy { it.key.name }
                .map { (material, sinks) ->
                    val source = fluidMarker(material, sinks.first())
                    board.reserve(source, material)
                    PipeGroup(material, source, sinks)
                }
                .sortedBy { it.nearestDistance }
        for (group in groups) routePipeGroup(group)
    }

    /** Connects a fluid output pipe to each target machine, so its product has somewhere to go. */
    fun placeTargetOutput() {
        if (!logistics.isFluid(plan.target)) return
        val row = rowPlacer.producerRow(plan.target) ?: return
        val producer = row.plannedRow.producerOf(plan.target) ?: return
        for (machine in row.machines) {
            if (machine.plannedRecipe.recipe !== producer.recipe) continue
            val coordinate = productEdge(row, machine) ?: continue
            if (!board.isFree(coordinate) || board.isReserved(coordinate)) continue
            board.placePipe(coordinate, plan.target)
            fluidPorts += coordinate
        }
    }

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
        val identifier = machine.plannedRecipe.factory.identifier
        val ports =
            blueprintMachineFluidPorts(identifier)
                .filter {
                    blueprintMachineEdgeFlow(
                        identifier,
                        it,
                        blueprintMachineEdge(identifier, it),
                    ) != PortFlow.OUTPUT
                }
                .map { Coordinate(machine.left + it.tile.x, row.factoryTop + it.tile.y) }
                .distinct()
        return ports.ifEmpty { sideEdges(row, machine) }
    }

    /** Whether [tile] can still take a pipe. */
    private fun available(tile: Coordinate) = board.isFree(tile) && !board.isReserved(tile)

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
        val identifier = machine.plannedRecipe.factory.identifier
        val inlets =
            inletTiles(row, machine).filter { board.edgeFluidAt(it) in listOf(null, material) }
        val preferred =
            blueprintMachineInputPort(identifier)?.let {
                Coordinate(machine.left + it.tile.x, row.factoryTop + it.tile.y)
            }
        return inlets.firstOrNull { it == preferred && available(it) }
            ?: inlets.firstOrNull { available(it) }
    }

    /**
     * The left then right edge of a machine without a port table, where any tile can take fluid.
     */
    private fun sideEdges(row: Row, machine: PlacedMachine) =
        listOf(machine.left - 1, machine.left + row.width).flatMap { x ->
            (row.factoryTop until row.factoryTop + row.height).map { y -> Coordinate(x, y) }
        }

    /**
     * The tile a fluid product leaves one machine of [row] through: the machine's output port, or
     * null when it is taken.
     */
    private fun productEdge(row: Row, machine: PlacedMachine): Coordinate? {
        val port =
            blueprintMachineOutputPort(machine.plannedRecipe.factory.identifier) ?: return null
        val tile = Coordinate(machine.left + port.tile.x, row.factoryTop + port.tile.y)
        return tile.takeIf { available(it) }
    }

    /** Places a fluid marker and returns the free tile its pipe lane starts on. */
    private fun fluidMarker(material: Material, target: Coordinate): Coordinate {
        // The connector sits just west of the sink, so the fluid only runs a short lane. A solid
        // import keeps a label at the far west and is trimmed after routing; a fluid route is not
        // straight, so its marker is placed near the machine from the start instead.
        val source = Coordinate(target.x - 1, target.y)
        // the connector tiles west of the source: a single infinity pipe or the input/label/output
        // triple that hides the fluid under the combinator (a chest cannot carry a fluid)
        val firstColumn =
            if (beltEnd == BeltEnd.INFINITY_CHEST) source.x - 1 else source.x - PIPE_CONNECTOR_WIDTH
        // keep the connector's buried run clear of any other fluid near the machine, and off a
        // marker row a previous import already claimed
        var row = target.y
        while (
            row in imports.markerRows ||
                !(firstColumn..source.x).all { board.canPlacePipes(Coordinate(it, row), material) }
        ) row++
        imports.markerRows += row
        // the cheat variant replaces the label with a single infinity pipe that supplies the fluid
        if (beltEnd == BeltEnd.INFINITY_CHEST) {
            board.placeInfinityPipe(Coordinate(source.x - 1, row), material)
            imports.compactionWestBound = maxOf(imports.compactionWestBound, target.x)
            return Coordinate(source.x, row)
        }
        // a pipe-to-ground run under the label, so the label does not block the fluid
        board.placeEnd(material, Coordinate(source.x - PIPE_CONNECTOR_WIDTH, row), EAST, INPUT)
        board.placeMarker(Coordinate(source.x - 2, row), Signal(material, fluid = true))
        board.placeEnd(material, Coordinate(source.x - 1, row), EAST, OUTPUT)
        board.markTunnel(
            Coordinate(source.x - PIPE_CONNECTOR_WIDTH, row),
            Coordinate(source.x - 1, row),
            board.endName(material),
        )
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
