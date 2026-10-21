package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.BeltEnd
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.east
import de.bjrke.factorio.optimizer.common.model.west

/** Belt tiles laid east of the last row before the target's connector may start. */
private const val OUTPUT_BELT_LEAD = 3

/** How many columns east of a blocked belt a free marker spot is searched for. */
private const val MARKER_SEARCH_COLUMNS = 12

/**
 * Routes the solid belts of the layout: the target's output belt, the sink of every row input, the
 * producer trunk a material runs on and the splitters that branch off it, and the import
 * connectors. A produced material's trunk reaches its farthest consumer and the nearer consumers
 * split off it; an import gets one belt connector per sink, so unrelated consumers are not tied to
 * one long belt. The pipe routes belong to [FluidFeeder], which runs after the sources are prepared
 * and before they are routed.
 */
internal class DeliveryRouter(
    stage: LayoutStage,
    connector: BeltEndConnector,
    imports: ImportExtension,
    beltEnd: BeltEnd,
    private val failures: MutableList<String>,
) {
    private val board = stage.board
    private val plan = stage.plan
    private val logistics = stage.logistics
    private val planner = stage.planner
    private val rowPlacer = stage.rowPlacer
    private val router = stage.router
    private val connector = connector

    private val networks = DeliveryNetworks(planner.producerOf)
    private val rules = BeltRouteRules(board)
    private val splits = SplitDeliveryRouter(board, router, networks, rules, failures)

    /** Places the sinks and sources; [prepareDeliveries] delegates to it. */
    private val preparation = DeliveryPreparation(stage, connector, imports, beltEnd)

    /**
     * Places every solid source up front and returns the deliveries to route. The sinks' feeds are
     * reserved first, then the sources are placed, so a long early route cannot occupy a marker or
     * producer outlet that a later delivery needs.
     */
    fun prepareDeliveries(): List<Delivery> = preparation.prepareDeliveries()

    /**
     * Routes every delivery, shortest first so a long belt can go around a confined one. A produced
     * material's split-offs only enter the queue once its trunk exists, but then compete with the
     * remaining deliveries.
     */
    fun routeDeliveries(deliveries: List<Delivery>) {
        val pending = deliveries.toMutableList()
        while (pending.isNotEmpty()) {
            val next = pending.minBy { it.distance }
            pending.remove(next)
            val routed =
                if (next.split) splits.deliver(next.material, next.sink)
                else deliver(next.material, next.start, next.direction, next.sink)
            if (routed) {
                for (sink in next.followUps) {
                    pending += Delivery(next.material, next.start, EAST, sink, split = true)
                }
            }
        }
    }

    /**
     * Routes one belt or pipe from [start] to [sink]'s feed tile, arriving on the surface and, for
     * a belt, heading into the target. The target itself is never touched.
     */
    private fun deliver(
        material: Material,
        start: Coordinate,
        direction: Direction,
        sink: Sink,
    ): Boolean {
        if (!board.isFree(start)) {
            failures += "${material.name} from $start (start blocked)"
            return false
        }
        if (start == sink.feed) {
            board.placeSurface(material, sink.feed, sink.heading ?: direction)
            return true
        }
        val segment = router.findRoute(start, direction, sink.feed, rules.allowed(material, sink))
        if (segment == null) {
            failures += "${material.name} to ${sink.feed} (no route)"
            return false
        }

        val nodes = buildList {
            add(start)
            addAll(segment)
        }
        placePath(board, material, nodes, sink.heading)
        networks.record(material, nodes, sink)
        return true
    }

    /** Extends the target's output belt and ends it in the selected solid connector. */
    fun placeTargetOutput() {
        val targetMaterial = plan.target
        val producer = planner.producerOf[targetMaterial] ?: return
        val recipe = plan.recipes[producer]
        val row = rowPlacer.rows[producer] ?: return
        if (!recipe.recipe.result.keys.any { !logistics.isFluid(it) }) return
        var coordinate = Coordinate(row.right + 1, row.outputLaneRow)

        while (coordinate.x <= row.right + OUTPUT_BELT_LEAD && board.isFree(coordinate)) {
            board.placeBelt(coordinate, targetMaterial, EAST)
            coordinate = coordinate.east()
        }
        val fits = (0 until connector.width).all { board.isFree(coordinate.east(it)) }
        if (fits) {
            // the output label sits above the product line, just like an imported input
            connector.placeSolidConnector(targetMaterial, coordinate.x, coordinate.y, input = false)
        } else {
            val spot = markerSpot(coordinate.west())
            board.placeMarker(spot, Signal(targetMaterial))
        }
    }

    /** A free tile beside the belt at [where], so a marker never blocks the belt. */
    private fun markerSpot(where: Coordinate): Coordinate {
        val (anchorX, beltRow) = where

        for (dx in 0..MARKER_SEARCH_COLUMNS) {
            for (dy in listOf(-1, 1)) {
                val tile = Coordinate(anchorX + dx, beltRow + dy)
                if (board.isFree(tile)) return tile
            }
        }
        return where.west()
    }
}
