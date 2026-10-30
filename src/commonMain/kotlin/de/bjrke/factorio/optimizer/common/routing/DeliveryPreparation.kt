package de.bjrke.factorio.optimizer.common.routing

import de.bjrke.factorio.optimizer.common.beltend.BeltEndConnector
import de.bjrke.factorio.optimizer.common.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.east
import de.bjrke.factorio.optimizer.common.model.manhattanDist
import de.bjrke.factorio.optimizer.common.plan.ImportExtension
import de.bjrke.factorio.optimizer.common.plan.ImportFeed
import de.bjrke.factorio.optimizer.common.planning.LayoutStage
import de.bjrke.factorio.optimizer.common.planning.Row
import de.bjrke.factorio.optimizer.common.planning.RowBelt

/** How far east of a row a free output source is searched for. */
private const val SOURCE_SEARCH_RANGE = 60

/** How many rows an imported source may be shifted down to avoid an occupied marker row. */
private const val MAX_SOURCE_ROW_ATTEMPTS = 50

/**
 * Where a material enters a row's lane: the route ends on [feed], the one tile next to [target],
 * and faces [heading] so its belt or pipe points at the lane. [target] is already placed and is
 * never overwritten.
 */
internal class Sink(
    val target: Coordinate,
    val feed: Coordinate,
    /** The direction the feed faces, or null for a pipe, which has no facing. */
    val heading: Direction?,
) {
    val feedRow
        get() = feed.y
}

/**
 * One belt or pipe route: fill [sink] from [start], leaving in [direction]. A produced material's
 * first delivery carries the other sinks as [followUps]; they split off its belt once it is laid. A
 * [split] delivery starts on an existing belt of its material.
 */
internal class Delivery(
    val material: Material,
    val start: Coordinate,
    val direction: Direction,
    val sink: Sink,
    val followUps: List<Sink> = emptyList(),
    val split: Boolean = false,
) {
    val distance
        get() = start.manhattanDist(sink.feed)
}

/**
 * The prepared solid routes, split by priority: [imports] feed inputs the plan does not produce and
 * are routed before the [produced] trunks.
 */
internal class SolidDeliveries(val imports: List<Delivery>, val produced: List<Delivery>)

/**
 * Prepares the solid deliveries: the sink of every row input and the source that feeds it. Every
 * source is placed up front, so a long early route cannot occupy a marker or producer outlet that a
 * later delivery needs. Routing the prepared deliveries belongs to [DeliveryRouter].
 */
internal class DeliveryPreparation(
    stage: LayoutStage,
    private val connector: BeltEndConnector,
    private val imports: ImportExtension,
    private val beltEnd: BeltEnd,
) {
    private val board = stage.board
    private val rowPlacer = stage.rowPlacer

    /**
     * Places every solid source up front and returns the deliveries to route, imports first. The
     * sinks' feeds are reserved first, then the sources are placed, so a long early route cannot
     * occupy a marker or producer outlet that a later delivery needs.
     */
    fun prepareDeliveries(): SolidDeliveries {
        val sinks = allSolidSinks()
        for ((material, _, sink) in sinks) reserve(sink.feed, material)
        return prepareSolidDeliveries(sinks)
    }

    private fun sinkOf(row: Row, belt: RowBelt): Sink =
        if (belt.direction == EAST)
            Sink(Coordinate(row.left, belt.y), Coordinate(row.left - 1, belt.y), EAST)
        else Sink(Coordinate(row.right, belt.y), Coordinate(row.right + 1, belt.y), WEST)

    private fun allSolidSinks() =
        rowPlacer.placedRows.flatMap { row ->
            row.belts
                .filter { it.io == Io.INPUT }
                .map { belt -> Triple(belt.material, row, sinkOf(row, belt)) }
        }

    /**
     * Turns the solid sinks into deliveries and places every source up front. Imports are prepared
     * first: their markers claim the free rows and the trimmer later moves them to one column. An
     * intermediate then gets one trunk from its producer to the farthest consumer; the nearer
     * consumers split off that belt later. An import gets one belt connector per sink, so unrelated
     * consumers are not tied to one long shared belt.
     */
    private fun prepareSolidDeliveries(sinks: List<Triple<Material, Row, Sink>>): SolidDeliveries {
        val grouped = sinks.groupBy { it.first }.entries.sortedBy { it.key.name }
        val imports = grouped.filter { rowPlacer.producerRow(it.key) == null }
        val produced = grouped.filter { rowPlacer.producerRow(it.key) != null }
        return SolidDeliveries(importDeliveries(imports), producedDeliveries(produced))
    }

    private fun importDeliveries(
        imports: List<Map.Entry<Material, List<Triple<Material, Row, Sink>>>>,
    ) = imports.flatMap { (material, list) ->
        list.map { (_, _, sink) ->
            Delivery(material, importSource(material, sink), EAST, sink)
        }
    }

    private fun producedDeliveries(
        produced: List<Map.Entry<Material, List<Triple<Material, Row, Sink>>>>,
    ) = produced.flatMap { (material, list) ->
        val producerRow = rowPlacer.producerRow(material) ?: return@flatMap emptyList()
        val start = outputStart(producerRow, material) ?: return@flatMap emptyList()
        val distinctSinks = list.map { it.third }.distinctBy { it.feed }
        // the trunk reaches the farthest consumer, so it passes the nearer ones on the way
        val sorted = distinctSinks.sortedByDescending { start.manhattanDist(it.feed) }
        listOf(
            Delivery(
                material = material,
                start = start,
                direction = EAST,
                sink = sorted.first(),
                followUps = sorted.drop(1),
            ),
        )
    }

    /** The first free belt tile east of [row]'s output lane, reserved as a source. */
    private fun outputStart(row: Row, material: Material): Coordinate? {
        val outputBelt =
            row.belts.firstOrNull { it.io == Io.OUTPUT && it.material == material } ?: return null
        var start = Coordinate(row.right + 1, outputBelt.y)
        while (start.x < row.right + SOURCE_SEARCH_RANGE && !isFree(start)) start = start.east()
        reserve(start, material)
        return start
    }

    /**
     * A solid belt connector feeding one imported sink: a constant-combinator label, an infinity
     * chest or a chest marker. The first free tile is reserved, so a route placed earlier cannot
     * block this source.
     */
    private fun importSource(material: Material, sink: Sink): Coordinate {
        val width = connector.width
        val columns = connector.left..(connector.left + width)
        var row = sink.feedRow
        var attempts = 0
        while (
            (row in imports.markerRows || columns.any { !isFree(Coordinate(it, row)) }) &&
                attempts++ < MAX_SOURCE_ROW_ATTEMPTS
        ) row++
        imports.markerRows += row
        connector.placeSolidConnector(material, connector.left, row, input = true)
        val start = Coordinate(connector.left + width, row)
        reserve(start, material)
        imports.importFeeds += ImportFeed(material, start, row, sink.feed)
        // a chest marker is powered, so its poles may sit in the import extension
        if (!beltEnd.isChest) imports.poleWestBound = maxOf(imports.poleWestBound, start.x)
        imports.compactionWestBound = maxOf(imports.compactionWestBound, sink.target.x)
        return start
    }

    private fun isFree(coordinate: Coordinate) = board.isFree(coordinate)

    private fun reserve(coordinate: Coordinate, material: Material) =
        board.reserve(coordinate, material)
}
