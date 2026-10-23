package de.bjrke.factorio.optimizer.common.layout

import de.bjrke.factorio.optimizer.common.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.area

/**
 * The tile-to-entity lookup and the extent of a fixed entity list: the read-only counterpart of
 * [LayoutBoard] for a list that is being checked or compacted. It lives in `layout` because both
 * the invariant checks and the compaction passes read it, and neither may depend on the other.
 */
internal class LayoutIndex(val entities: List<LayoutEntity>) {
    val tiles = HashMap<Coordinate, LayoutEntity>()
    var minX = Int.MAX_VALUE
    var maxX = Int.MIN_VALUE
    var minY = Int.MAX_VALUE
    var maxY = Int.MIN_VALUE

    init {
        for (entity in entities) {
            for (tile in entity.area) {
                tiles[tile] = entity
                if (tile.x < minX) minX = tile.x
                if (tile.x > maxX) maxX = tile.x
                if (tile.y < minY) minY = tile.y
                if (tile.y > maxY) maxY = tile.y
            }
        }
    }

    val isEmpty
        get() = tiles.isEmpty()

    fun at(tile: Coordinate) = tiles[tile]
}
