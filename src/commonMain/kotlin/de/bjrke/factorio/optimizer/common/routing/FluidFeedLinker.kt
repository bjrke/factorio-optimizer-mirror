package de.bjrke.factorio.optimizer.common.routing

import de.bjrke.factorio.optimizer.common.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.layout.PIPE_UNDERGROUND_RANGE
import de.bjrke.factorio.optimizer.common.layout.endName
import de.bjrke.factorio.optimizer.common.layout.entity.BeltLikeEntity
import de.bjrke.factorio.optimizer.common.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.layout.entity.PipeEntity
import de.bjrke.factorio.optimizer.common.layout.entity.Plumbing
import de.bjrke.factorio.optimizer.common.layout.entity.Signal
import de.bjrke.factorio.optimizer.common.layout.markTunnel
import de.bjrke.factorio.optimizer.common.layout.placeEnd
import de.bjrke.factorio.optimizer.common.layout.placeInfinityPipe
import de.bjrke.factorio.optimizer.common.layout.placeMarker
import de.bjrke.factorio.optimizer.common.layout.placePipe
import de.bjrke.factorio.optimizer.common.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.model.Io.INPUT
import de.bjrke.factorio.optimizer.common.model.Io.OUTPUT
import de.bjrke.factorio.optimizer.common.model.east
import de.bjrke.factorio.optimizer.common.plan.FluidFeed
import de.bjrke.factorio.optimizer.common.plan.ImportExtension

/**
 * Moves the inline fluid lane markers into the common import column and joins each one to its lane
 * head with walkable pipe-to-ground runs. A run may bury itself under a belt or an inserter that
 * crosses the west corridor, so a solid route does not keep the marker out of the shared column; a
 * feed that still cannot be placed keeps its inline marker.
 */
internal class FluidFeedLinker(
    private val board: LayoutBoard,
    private val beltEnd: BeltEnd,
    private val imports: ImportExtension,
) {

    /** Moves every fluid feed's source to [column] and links it to the lane head. */
    fun link(column: Int) {
        for (feed in imports.fluidFeeds) link(feed, column)
    }

    /** Moves one feed; a feed that no longer fits west of its head keeps the inline marker. */
    private fun link(feed: FluidFeed, column: Int) {
        val sourceEnd = if (beltEnd == BeltEnd.INFINITY_CHEST) column else column + 2
        if (sourceEnd >= feed.head.x) return
        if (feed.source.first().x == column) {
            pin(feed, column)
            return
        }
        val saved = board.placed.toList()
        feed.source.forEach { board.removeAt(it) }
        if (!placeSource(feed, column)) {
            board.replaceAll(saved)
            return
        }
        if (validateLayout(board.placed).isNotEmpty()) {
            board.replaceAll(saved)
            return
        }
        imports.compactionWestBound = maxOf(imports.compactionWestBound, sourceEnd + 1)
        pin(feed, column)
    }

    /** Places the fluid source block at [column] and the walkable link to the lane head. */
    private fun placeSource(feed: FluidFeed, column: Int): Boolean {
        val row = feed.row
        val sourceTiles =
            (column..(column + if (beltEnd == BeltEnd.INFINITY_CHEST) 0 else 2)).map {
                Coordinate(it, row)
            }
        if (sourceTiles.any { !placeable(it) }) return false
        if (!linkFits(feed, sourceTiles.last().x)) return false
        if (beltEnd == BeltEnd.INFINITY_CHEST) {
            board.placeInfinityPipe(sourceTiles.single(), feed.material)
        } else {
            board.placeEnd(feed.material, sourceTiles[0], EAST, INPUT)
            board.placeMarker(sourceTiles[1], Signal(feed.material, fluid = true))
            board.placeEnd(feed.material, sourceTiles[2], EAST, OUTPUT)
            board.markTunnel(sourceTiles[0], sourceTiles[2], board.endName(feed.material))
        }
        connect(feed, sourceTiles.last().x)
        return true
    }

    /**
     * Whether the link from [sourceEnd] to the head fits: a run needs free ends, while the tiles it
     * buries may be a belt, an inserter or a pipe — the west corridor often carries a solid route
     * across the lane row, and the pipe simply passes under it.
     */
    private fun linkFits(feed: FluidFeed, sourceEnd: Int): Boolean {
        var x = sourceEnd + 1
        var fits = true
        while (fits && x < feed.head.x) {
            val last = feed.head.x - 1
            val remaining = last - x
            when {
                remaining == 0 -> {
                    fits = placeable(Coordinate(x, feed.row))
                    x++
                }
                remaining <= PIPE_UNDERGROUND_RANGE -> {
                    fits = runFits(feed.row, x, last)
                    x = feed.head.x
                }
                else -> {
                    fits = runFits(feed.row, x, x + PIPE_UNDERGROUND_RANGE)
                    x += PIPE_UNDERGROUND_RANGE + 1
                    if (fits && x < feed.head.x) {
                        fits = placeable(Coordinate(x, feed.row))
                        x++
                    }
                }
            }
        }
        return fits
    }

    /** A run's two ends stay on free ground; the tiles between may be buried under a belt. */
    private fun runFits(row: Int, west: Int, east: Int): Boolean {
        if (!placeable(Coordinate(west, row)) || !placeable(Coordinate(east, row))) return false
        return (west + 1 until east).all { x -> canBury(Coordinate(x, row)) }
    }

    /** Whether a fluid run may pass under the entity at [tile]. */
    private fun canBury(tile: Coordinate): Boolean {
        val entity = board.entityAt(tile)
        return entity == null ||
            entity is InserterEntity ||
            entity is PipeEntity ||
            entity is BeltLikeEntity
    }

    private fun placeable(tile: Coordinate) = board.isFree(tile) && !board.isReserved(tile)

    /**
     * Joins [sourceEnd] to the head with the longest walkable pipe-to-ground runs, using one plain
     * pipe where two runs have to meet. A two-tile link is a pair of adjacent ends (`pUUp`), a
     * one-tile link is the only surface pipe left.
     */
    private fun connect(feed: FluidFeed, sourceEnd: Int) {
        val row = feed.row
        var x = sourceEnd + 1
        while (x < feed.head.x) {
            val last = feed.head.x - 1
            val remaining = last - x
            when {
                remaining == 0 -> {
                    board.placePipe(Coordinate(x, row), feed.material)
                    x++
                }
                remaining <= PIPE_UNDERGROUND_RANGE -> {
                    placeRun(feed, x, last)
                    x = feed.head.x
                }
                else -> {
                    placeRun(feed, x, x + PIPE_UNDERGROUND_RANGE)
                    x += PIPE_UNDERGROUND_RANGE + 1
                    if (x < feed.head.x) {
                        board.placePipe(Coordinate(x, row), feed.material)
                        x++
                    }
                }
            }
        }
    }

    /** One horizontal pipe-to-ground run in the lane's flow direction. */
    private fun placeRun(feed: FluidFeed, west: Int, east: Int) {
        val row = feed.row
        val from = Coordinate(if (feed.flowEast) west else east, row)
        val to = Coordinate(if (feed.flowEast) east else west, row)
        val direction = if (feed.flowEast) EAST else WEST
        board.placeEnd(feed.material, from, direction, INPUT)
        board.placeEnd(feed.material, to, direction, OUTPUT)
        board.markTunnel(from, to, board.endName(feed.material))
    }

    /** Pins the marker, the link and the head (plus the lane behind it) into one column group. */
    private fun pin(feed: FluidFeed, column: Int) {
        for (x in column..feed.head.x) {
            imports.pinnedImportTiles += Coordinate(x, feed.row)
        }
        val next = feed.head.east()
        if (board.entityAt(next) is Plumbing) imports.pinnedImportTiles += next
    }
}
