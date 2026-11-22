package de.bjrke.factorio.optimizer.common.blueprint.placing.routing

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.IMPORT_FEED
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutIssue
import de.bjrke.factorio.optimizer.common.blueprint.layout.issue
import de.bjrke.factorio.optimizer.common.blueprint.placing.special.BeltEndConnector
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.ImportExtension
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.ImportFeed
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.LayoutStage
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.Row
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.RowBelt
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.east
import de.bjrke.factorio.optimizer.common.core.model.manhattanDist
import de.bjrke.factorio.optimizer.common.core.model.west

/** How far past a row's output lane a free source is searched for. */
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
)

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
    private val failures: MutableList<LayoutIssue> = mutableListOf(),
) {
    private val board = stage.board
    private val plan = stage.plan
    private val rowPlacer = stage.rowPlacer
    private val turnFeeds = MixedTurnFeeds(board) { material -> plan.producerOf(material) != null }

    /**
     * Places every solid source up front and returns the deliveries to route, imports first. The
     * sinks' feeds are reserved first, then the sources are placed, so a long early route cannot
     * occupy a marker or producer outlet that a later delivery needs.
     */
    fun prepareDeliveries(): SolidDeliveries {
        val sinks = allSolidSinks()
        for ((material, _, sink) in sinks) {
            reserve(sink.feed, material)
            // the feed takes its items from behind, so no other material's route may sit there
            sink.heading?.let { board.reserve(sink.feed - it.step, material, approach = true) }
        }
        return prepareSolidDeliveries(sinks)
    }

    private fun sinkOf(row: Row, belt: RowBelt): Sink =
        if (belt.direction == EAST)
            Sink(Coordinate(row.left, belt.y), Coordinate(row.left - 1, belt.y), EAST)
        else Sink(Coordinate(row.right, belt.y), Coordinate(row.right + 1, belt.y), WEST)

    /**
     * One input belt has one sink; a mixed belt has two, one on each side of its T merge. The
     * straight feeder enters from the lane's source side. The turn feeder either feeds the merge
     * from its open side at [sideLoadFeed] (a produced trunk on an outer lane) or uses the U-turn:
     * it enters the turn tile head-on from its own row, because the tile beside the turn is the
     * lane row that a fluid link may claim; only when another U-turn already owns that gap tile
     * does it keep the side feed.
     *
     * The head-on choice is deduplicated only against an identical head-on tile. Two U-turns whose
     * turns sit directly next to each other (a factory one or two tiles high) would instead each
     * pick the other's turn tile, which this check cannot see; no such row exists today, and one
     * would report a side-load from `validateLayout` rather than lose the feed silently.
     */
    private fun sinksOf(
        row: Row,
        belt: RowBelt,
        uniqueHeadOn: Set<Coordinate>,
        sideLoadFeed: Coordinate?,
    ): List<Pair<Material, Sink>> {
        val merge = belt.merge ?: return listOf(belt.material to sinkOf(row, belt))
        val mergeRow = merge.mergeRow(belt.y)
        val straightFeed =
            if (belt.direction == EAST) Coordinate(merge.column - 1, mergeRow)
            else Coordinate(merge.column + 1, mergeRow)
        val straightTarget = Coordinate(merge.column, mergeRow)
        val straightDirection = if (belt.direction == EAST) EAST else WEST
        if (sideLoadFeed != null) {
            return listOf(
                merge.straightMaterial to Sink(straightTarget, straightFeed, straightDirection),
                merge.turnMaterial to Sink(straightTarget, sideLoadFeed, merge.approachDirection),
            )
        }
        val turnRow = merge.turnRow(belt.y)
        val headOnTile = Coordinate(merge.turnColumn, turnRow + if (merge.above) -1 else 1)
        // two mixed belts whose turns face each other share the one tile between them; neither may
        // take it head-on, so both keep the side feed on the turn row
        val headOnFeed = headOnTile in uniqueHeadOn
        val turnFeed = if (headOnFeed) headOnTile else Coordinate(merge.column, turnRow)
        val turnHeading = if (headOnFeed) merge.mergeDirection else straightDirection
        val turnTarget = Coordinate(merge.turnColumn, turnRow)
        return listOf(
            merge.straightMaterial to Sink(straightTarget, straightFeed, straightDirection),
            merge.turnMaterial to Sink(turnTarget, turnFeed, turnHeading),
        )
    }

    private fun allSolidSinks(): List<Triple<Material, Row, Sink>> {
        val inputBelts =
            rowPlacer.placedRows.flatMap { row ->
                row.belts.filter { it.io == Io.INPUT }.map { row to it }
            }
        val sideLoadFeeds = turnFeeds.sideLoadFeeds(inputBelts)
        val uniqueHeadOn = turnFeeds.uniqueHeadOn(inputBelts, sideLoadFeeds)
        return inputBelts.flatMapIndexed { index, (row, belt) ->
            val merge = belt.merge
            val sideLoad = sideLoadFeeds[index]
            if (merge != null && sideLoad == null && turnFeeds.sideLoadsTurn(belt)) {
                // RowPlacer left the U-turn tiles out, so without an upstream tile there is no
                // feed. Keep the straight half and report the lost turn material.
                failures +=
                    Coordinate(merge.column, belt.y)
                        .issue(
                            "${merge.turnMaterial.name} has no free side-load tile on the mixed belt " +
                                "at (${merge.column}, ${belt.y})",
                        )
                return@flatMapIndexed sinksOf(row, belt, uniqueHeadOn, sideLoadFeed = null)
                    .filter { (material, _) -> material != merge.turnMaterial }
                    .map { (material, sink) -> Triple(material, row, sink) }
            }
            val sinks = sinksOf(row, belt, uniqueHeadOn, sideLoad)
            if (merge != null && sideLoad == null) {
                val turnFeed =
                    sinks.first { (material, _) -> material == merge.turnMaterial }.second
                pinMerge(imports, belt, turnFeed.feed)
            }
            sinks.map { (material, sink) -> Triple(material, row, sink) }
        }
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
        // use the plan's own source list, so the layout and the reported plan cannot disagree
        val imports = grouped.filter { it.key in plan.sourceMaterials }
        val produced = grouped.filter { it.key !in plan.sourceMaterials }
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
        val producerRow = rowPlacer.producerRow(material)
        if (producerRow == null) {
            failures +=
                list
                    .first()
                    .third
                    .target
                    .issue(
                        "${material.name} has no producer row",
                    )
            return@flatMap emptyList()
        }
        val outputBelt =
            producerRow.belts.firstOrNull { it.io == Io.OUTPUT && it.material == material }
        if (outputBelt == null) {
            failures +=
                producerRow.box.issue("${material.name} has no output belt on its producer row")
            return@flatMap emptyList()
        }

        val source = outputStart(outputBelt, producerRow, material) ?: return@flatMap emptyList()

        val distinctSinks = list.map { it.third }.distinctBy { it.feed }
        // the trunk reaches the farthest consumer, so it passes the nearer ones on the way
        val sorted = distinctSinks.sortedByDescending { source.start.manhattanDist(it.feed) }
        listOf(
            Delivery(
                material = material,
                start = source.start,
                direction = source.direction,
                sink = sorted.first(),
                followUps = sorted.drop(1),
            ),
        )
    }

    /** A produced source: the first free tile past the product belt, and the way it leaves. */
    private class Source(val start: Coordinate, val direction: Direction)

    /**
     * The first free belt tile beyond [row]'s output lane, in the lane's own direction, reserved as
     * a source. A westbound product belt is fed from its west end, so the source is searched west.
     */
    private fun outputStart(outputBelt: RowBelt, row: Row, material: Material) =
        when (val direction = outputBelt.direction) {
            EAST -> {
                var start = Coordinate(row.right + 1, outputBelt.y)
                while (start.x < row.right + SOURCE_SEARCH_RANGE && !isFree(start)) {
                    start = start.east()
                }
                reserve(start, material)
                Source(start, EAST)
            }
            WEST -> {
                var start = Coordinate(row.left - 1, outputBelt.y)
                while (start.x > row.left - SOURCE_SEARCH_RANGE && !isFree(start)) {
                    start = start.west()
                }
                reserve(start, material)
                Source(start, WEST)
            }
            else -> {
                failures += row.box.issue("did not expect a belt to $direction")
                null
            }
        }

    /**
     * A solid belt connector feeding one imported sink: a constant-combinator label, an infinity
     * chest or a chest marker. The first free tile is reserved, so a route placed earlier cannot
     * block this source.
     */
    private fun importSource(material: Material, sink: Sink): Coordinate {
        // the marker extends west of the feed, so every design starts its belt on the same column
        val columns = IMPORT_FEED - connector.width..IMPORT_FEED
        var row = sink.feed.y
        var attempts = 0
        while (
            (row in imports.markerRows || columns.any { !isFree(Coordinate(it, row)) }) &&
                attempts++ < MAX_SOURCE_ROW_ATTEMPTS
        ) row++
        imports.markerRows += row
        connector.placeSolidConnector(material, IMPORT_FEED, row, input = true)
        val start = Coordinate(IMPORT_FEED, row)
        reserve(start, material)
        imports.importFeeds += ImportFeed(material, start, sink.feed)
        // a chest marker is powered, so its poles may sit in the import extension
        if (!beltEnd.isChest) imports.poleWestBound = maxOf(imports.poleWestBound, start.x)
        imports.compactionWestBound = maxOf(imports.compactionWestBound, sink.target.x)
        return start
    }

    private fun isFree(coordinate: Coordinate) = board.isFree(coordinate)

    private fun reserve(coordinate: Coordinate, material: Material) =
        board.reserve(coordinate, material)
}

/** The merge group the trimmer and compaction must keep together, including its U-turn feed. */
private fun pinMerge(imports: ImportExtension, belt: RowBelt, turnFeed: Coordinate) {
    val merge = belt.merge ?: return
    imports.pinnedImportTiles += Coordinate(merge.column, belt.y)
    imports.pinnedImportTiles += Coordinate(merge.column, merge.mergeRow(belt.y))
    imports.pinnedImportTiles += Coordinate(merge.turnColumn, merge.turnRow(belt.y))
    imports.pinnedImportTiles += turnFeed
    imports.pinnedImportTiles += Coordinate(merge.turnColumn, merge.mergeRow(belt.y))
}
