package de.bjrke.factorio.optimizer.common.blueprint.placing.routing

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.ConveyorEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LaneLoad
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeBelt
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeSurface
import de.bjrke.factorio.optimizer.common.blueprint.layout.reserveRow
import de.bjrke.factorio.optimizer.common.blueprint.placing.special.BeltEndConnector
import de.bjrke.factorio.optimizer.common.blueprint.placing.special.placePath
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.ImportExtension
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.LayoutStage
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.Material

/** How often one delivery is routed again after a path wanted two facings on one belt tile. */
private const val ROUTE_ATTEMPTS = 4

/**
 * Routes the solid belts of the layout: the target's output belt, the sink of every row input, the
 * producer trunk a material runs on and the splitters that branch off it, and the import
 * connectors. A produced material's trunk reaches its farthest consumer and the nearer consumers
 * split off it; an import gets one belt connector per sink, so unrelated consumers are not tied to
 * one long belt. The fluid lanes and markers belong to [FluidFeeder], whose routed fallback runs
 * between the import and the produced deliveries.
 */
internal class DeliveryRouter(
    stage: LayoutStage,
    private val connector: BeltEndConnector,
    imports: ImportExtension,
    beltEnd: BeltEnd,
    private val failures: MutableList<String>,
) {
    private val board = stage.board
    private val plan = stage.plan
    private val rowPlacer = stage.rowPlacer
    private val router = stage.router

    private val networks = DeliveryNetworks(plan.beltMaterials)
    private val rules = BeltRouteRules(board)
    private val splits = SplitDeliveryRouter(board, router, networks, rules, failures)

    /** Places the sinks and sources; [prepareDeliveries] delegates to it. */
    private val preparation = DeliveryPreparation(stage, connector, imports, beltEnd, failures)

    /**
     * Places every solid source up front and returns the deliveries to route. The sinks' feeds are
     * reserved first, then the sources are placed, so a long early route cannot occupy a marker or
     * producer outlet that a later delivery needs.
     */
    fun prepareDeliveries(): SolidDeliveries = preparation.prepareDeliveries()

    /**
     * Routes the imported inputs before any produced delivery. An import gets the direct path along
     * its marker row; a produced trunk then tunnels under it instead of forcing the import to turn.
     */
    fun routeImports(deliveries: List<Delivery>) = routeGroup(deliveries)

    /**
     * Routes the produced deliveries, shortest first so a long belt can go around a confined one. A
     * produced material's split-offs only enter the queue once its trunk exists, but then compete
     * with the remaining deliveries.
     */
    fun routeProduced(deliveries: List<Delivery>) = routeGroup(deliveries)

    private fun routeGroup(deliveries: List<Delivery>) {
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
        load: LaneLoad? = null,
    ): Boolean {
        if (!board.isFree(start)) {
            failures += "${material.name} from $start (start blocked)"
            return false
        }
        if (start == sink.feed) {
            val heading = sink.heading ?: direction
            if (load != null && !board.isFluid(material)) {
                board.placeBelt(sink.feed, load, heading)
            } else {
                board.placeSurface(material, sink.feed, heading)
            }
            return true
        }
        // a route can come back onto a tile it already used and want another facing there; that
        // would drop the belt an earlier tunnel exit empties onto, so those tiles are excluded and
        // the route is searched again
        val blocked = mutableSetOf<Coordinate>()
        repeat(ROUTE_ATTEMPTS) {
            val segment =
                router.findRoute(
                    start,
                    direction,
                    sink.feed,
                    rules.allowed(material, sink, blocked),
                ) ?: return fail(material, sink)
            val nodes = buildList {
                add(start)
                addAll(segment)
            }
            val conflicts = placePath(board, material, nodes, sink.heading, load)
            if (conflicts.isEmpty()) {
                networks.record(material, nodes, sink)
                return true
            }
            blocked += conflicts
        }
        return fail(material, sink)
    }

    private fun fail(material: Material, sink: Sink): Boolean {
        failures += "${material.name} to ${sink.feed} (no route)"
        return false
    }

    /** Reserves the product row east of its placed belt, before any route runs. */
    fun reserveTargetOutput() {
        val targetMaterial = plan.target
        if (board.isFluid(targetMaterial)) return
        val row = rowPlacer.producerRow(targetMaterial)
        val outputBelt =
            row?.belts?.firstOrNull { it.io == Io.OUTPUT && it.material == targetMaterial }
                ?: return
        board.reserveRow(outputBelt.y, row.right + 1, targetMaterial)
    }

    /**
     * Places the target's output connector east of everything and connects it to the product belt.
     * The other feeds are already routed, so the connector no longer widens their search area; the
     * product belt is the only route left and may tunnel under a crossing belt.
     */
    fun placeTargetOutput() {
        val targetMaterial = plan.target
        // a fluid target leaves through the pipe output FluidFeeder places
        if (board.isFluid(targetMaterial)) return
        val endBelt =
            board.placed
                .filterIsInstance<BeltEntity>()
                .filter { it.material == targetMaterial }
                .maxByOrNull { it.area.bottomRight.x }
                ?: run {
                    failures += "${targetMaterial.name} has no output belt on its row"
                    return
                }
        val rowY = endBelt.area.topLeft.y
        // the connector's feed tile must be free for the connecting belt
        var east = board.maxX + 1
        while (!board.isFree(Coordinate(east - 1, rowY))) east++
        // the product row is no longer a routing wall once the other feeds are fixed
        board.rowReservations.clear()
        connector.placeSolidConnector(targetMaterial, east, rowY, input = false)
        val start = endBelt.area.bottomRight.x + 1
        // a loader at [east] takes directly from the belt at [east] - 1; only the tiles before
        // its feed tile need a route
        if (start < east) {
            deliver(
                targetMaterial,
                Coordinate(start, rowY),
                EAST,
                Sink(Coordinate(east, rowY), Coordinate(east - 1, rowY), EAST),
                load = endBelt.load,
            )
        }
        val connectorEnd = east + connector.width
        val eastOfConnector =
            board.placed.filterIsInstance<ConveyorEntity>().filter {
                it.area.topLeft.x >= connectorEnd
            }
        if (eastOfConnector.isNotEmpty()) {
            failures += "${targetMaterial.name} has a belt east of its output connector"
        }
    }
}
