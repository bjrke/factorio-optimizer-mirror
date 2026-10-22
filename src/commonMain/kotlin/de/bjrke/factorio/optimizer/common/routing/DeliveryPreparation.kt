package de.bjrke.factorio.optimizer.common.routing

import de.bjrke.factorio.optimizer.common.blueprint.BeltEndConnector
import de.bjrke.factorio.optimizer.common.model.BeltEnd
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Direction.NORTH
import de.bjrke.factorio.optimizer.common.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.manhattanDist
import de.bjrke.factorio.optimizer.common.planning.LayoutStage
import de.bjrke.factorio.optimizer.common.planning.Row

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
    private val planner = stage.planner
    private val rowPlacer = stage.rowPlacer

    /**
     * Places every solid source up front and returns the deliveries to route. The sinks' feeds are
     * reserved first, then the sources are placed, so a long early route cannot occupy a marker or
     * producer outlet that a later delivery needs.
     */
    fun prepareDeliveries(): List<Delivery> {
        val sinks = allSolidSinks()
        for ((material, _, sink) in sinks) reserve(sink.feed, material)
        return prepareSolidDeliveries(sinks)
    }

    private fun sinkOf(row: Row, material: Material): Sink? =
        when (val index = row.solids.indexOf(material)) {
            -1 -> null
            0 -> row.laneNearRow?.let { laneSink(row, it, material) }
            1 -> row.laneFarRow?.let { laneSink(row, it, material) }
            2 -> row.laneBelowRow?.let { laneSink(row, it, material) }
            else ->
                // any further input side-loads the lower belt from below
                row.laneBelowRow?.let {
                    val x = row.left + (index - 3)
                    Sink(Coordinate(x, it), Coordinate(x, it + 1), NORTH)
                }
        }

    private fun laneSink(row: Row, y: Int, material: Material): Sink =
        if (rowPlacer.laneDirection(row, material) == EAST) {
            Sink(Coordinate(row.left, y), Coordinate(row.left - 1, y), EAST)
        } else {
            Sink(Coordinate(row.right, y), Coordinate(row.right + 1, y), WEST)
        }

    private fun allSolidSinks() = buildList {
        for (row in rowPlacer.placedRows) {
            for (material in row.solids) {
                sinkOf(row, material)?.let { add(Triple(material, row, it)) }
            }
        }
    }

    /**
     * Turns the solid sinks into deliveries and places every source up front. An intermediate gets
     * one trunk from its producer to the farthest consumer; the nearer consumers split off that
     * belt later. An import gets one belt connector per sink, so unrelated consumers are not tied
     * to one long shared belt.
     */
    private fun prepareSolidDeliveries(sinks: List<Triple<Material, Row, Sink>>): List<Delivery> {
        val grouped = sinks.groupBy { it.first }
        val deliveries = mutableListOf<Delivery>()
        for ((material, list) in grouped.entries.sortedBy { it.key.name }) {
            val producer = planner.producerOf[material]
            if (producer != null) {
                val producerRow = rowPlacer.rows[producer] ?: continue
                val start = outputStart(producerRow, material)
                val distinctSinks = list.map { it.third }.distinctBy { it.feed }
                // the trunk reaches the farthest consumer, so it passes the nearer ones on the way
                val sorted = distinctSinks.sortedByDescending { start.manhattanDist(it.feed) }
                deliveries +=
                    Delivery(
                        material = material,
                        start = start,
                        direction = EAST,
                        sink = sorted.first(),
                        followUps = sorted.drop(1),
                    )
            } else {
                for ((_, _, sink) in list) {
                    deliveries += Delivery(material, importSource(material, sink), EAST, sink)
                }
            }
        }
        return deliveries
    }

    /** The first free belt tile east of [row]'s output lane, reserved as a source. */
    private fun outputStart(row: Row, material: Material): Coordinate {
        var x = row.right + 1
        while (x < row.right + SOURCE_SEARCH_RANGE && !isFree(Coordinate(x, row.outputLaneRow))) x++
        val start = Coordinate(x, row.outputLaneRow)
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
