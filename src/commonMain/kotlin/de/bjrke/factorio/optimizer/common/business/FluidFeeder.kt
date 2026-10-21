package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.BeltEnd
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Io.INPUT
import de.bjrke.factorio.optimizer.common.model.Io.OUTPUT
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.manhattanDist

/** The tiles of a label connector west of the source: input end, marker and output end. */
private const val PIPE_CONNECTOR_WIDTH = 3

/** How many existing network points are tried before a fluid sink is reported unreachable. */
private const val MAX_ROUTE_STARTS = 8

/**
 * Routes every fluid in the plan: a pipe cluster next to each demanding machine, one marker per
 * fluid and a pipe network grown from it. Pipes run before belts, so their cheap underground runs
 * are in place when the belts route. Owns no state of its own; the machine ports it must keep clear
 * are shared with the underground stretcher through [fluidPorts].
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
    private val planner = stage.planner
    private val rowPlacer = stage.rowPlacer
    private val router = stage.router

    /** Reserves every fluid machine edge, then routes each fluid from its marker nearest-first. */
    fun feed() {
        // reserve every fluid's machine edge first, so two fluids never pick touching edges
        val targets = mutableMapOf<Material, MutableList<Coordinate>>()
        for (row in rowPlacer.placedRows) {
            for (machine in row.machines) {
                val allocation = plan.recipes[machine.allocation]
                val fluids =
                    allocation.recipe.ingredients.keys
                        .filter { logistics.isFluid(it) }
                        .sortedBy { it.name }
                for (material in fluids) {
                    val tile = fluidEdge(row, machine, material)
                    if (tile == null) {
                        failures +=
                            "${material.name} to ${allocation.factory.identifier} (no fluid port)"
                        continue
                    }
                    board.reserve(tile, material)
                    board.setEdgeFluid(tile, material)
                    fluidPorts += tile
                    targets.getOrPut(material) { mutableListOf() }.add(tile)
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
        val producer = planner.producerOf[plan.target] ?: return
        val recipe = plan.recipes[producer]
        val row = rowPlacer.rows[producer] ?: return
        val ports =
            blueprintMachineFluidPorts(recipe.factory.identifier).filter {
                it.flow != PortFlow.INPUT
            }
        for (machine in row.machines) {
            if (plan.recipes[machine.allocation].recipe !== recipe.recipe) continue
            val left = machine.left
            val candidates =
                ports
                    .map { Coordinate(left + it.tile.x, row.factoryTop + it.tile.y) }
                    .ifEmpty {
                        (row.factoryTop until row.factoryTop + row.height).map { y ->
                            Coordinate(left + row.width, y)
                        }
                    }
            for (coordinate in candidates) {
                if (board.isFree(coordinate)) {
                    board.placePipe(coordinate, plan.target)
                    fluidPorts += coordinate
                    break
                }
            }
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
     * The tile a fluid pipe must occupy to feed one machine of [row]: one of the machine's input
     * ports (or, without a port table, the left then right edge). Null when all are taken.
     */
    private fun fluidEdge(row: Row, machine: PlacedMachine, material: Material): Coordinate? {
        val left = machine.left
        val ports =
            blueprintMachineFluidPorts(plan.recipes[machine.allocation].factory.identifier)
                .filter { it.flow != PortFlow.OUTPUT }
                .map { Coordinate(left + it.tile.x, row.factoryTop + it.tile.y) }
        val candidates = ports.ifEmpty {
            listOf(left - 1, left + row.width).flatMap { x ->
                (row.factoryTop until row.factoryTop + row.height).map { y -> Coordinate(x, y) }
            }
        }
        for (tile in candidates) {
            if (!board.isFree(tile) || board.isReserved(tile)) continue
            val clash = DIRECTION_STEPS.any { d ->
                board.edgeFluidAt(tile + d).let { it != null && it != material }
            }
            if (!clash) return tile
        }
        return null
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
        val columns = (firstColumn..source.x).toList()
        // keep the connector's buried run clear of any other fluid near the machine
        fun touchesForeignFluid(row: Int) = columns.any { x ->
            DIRECTION_STEPS.any { step ->
                val neighbor = board.fluidAt(Coordinate(x, row) + step)
                neighbor != null && neighbor != material
            }
        }
        var row = target.y
        while (
            row in imports.markerRows ||
                columns.any { !board.isFree(Coordinate(it, row)) } ||
                touchesForeignFluid(row)
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

    /** A pipe tile is free only when it touches no pipe carrying a different fluid. */
    private fun pipeAllowed(coordinate: Coordinate, fluid: Material) =
        board.isFree(coordinate) &&
            !board.isReserved(coordinate) &&
            DIRECTION_STEPS.none { d ->
                val cd = coordinate + d
                val placedFluid = board.fluidAt(cd)
                val reservedFor = board.edgeFluidAt(cd)
                (placedFluid != null && placedFluid != fluid) ||
                    (reservedFor != null && reservedFor != fluid)
            }
}
