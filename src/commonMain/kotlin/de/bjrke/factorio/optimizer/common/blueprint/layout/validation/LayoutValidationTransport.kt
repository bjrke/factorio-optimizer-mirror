package de.bjrke.factorio.optimizer.common.blueprint.layout.validation

import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutIndex
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.ConveyorEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LaneLoad
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.Plumbing
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.joinsAt
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.directionBetween
import de.bjrke.factorio.optimizer.common.core.model.east
import de.bjrke.factorio.optimizer.common.core.model.north
import de.bjrke.factorio.optimizer.common.core.model.south
import de.bjrke.factorio.optimizer.common.core.model.west

/**
 * The belt and pipe checks: a belt must not run into another material's belt, nor take a foreign
 * side-load, and pipes of different fluids must never touch. A mixed belt is checked lane by lane,
 * so its two T-merge feeders may enter from opposite sides while an unrelated material cannot.
 */
internal fun validateMaterials(
    index: LayoutIndex,
    entities: List<LayoutEntity>,
    errors: MutableList<String>,
) {
    for (entity in entities) {
        if (entity is ConveyorEntity) checkConveyor(entity, index, errors)
        if (entity is Plumbing) checkPlumbing(entity, index, errors)
    }
}

/**
 * Whether this conveyor feeds the tile it faces. A buried underground entrance does not: it dives
 * into its run, and the tile ahead of it is the tunnel span, so any belt there is independent.
 */
private fun ConveyorEntity.feedsAhead() = this !is UndergroundBeltEntity || io != Io.INPUT

private fun checkConveyor(
    conveyor: ConveyorEntity,
    index: LayoutIndex,
    errors: MutableList<String>,
) {
    val tile = conveyor.area.topLeft
    val aheadTile = tile + conveyor.direction.step
    val ahead = index.at(aheadTile)
    if (conveyor.feedsAhead() && ahead is ConveyorEntity) {
        checkAhead(conveyor, ahead, aheadTile, errors)
    }
    for (side in fourNeighbours(tile)) {
        val other = index.at(side) as? ConveyorEntity ?: continue
        // a buried entrance dives instead of feeding the tile it faces
        if (!other.feedsAhead()) continue
        if (other.direction.step != tile - side) continue
        checkSideLoad(conveyor, other, tile, side, errors)
    }
}

/** A belt in front of [conveyor]: a straight continuation, a head-on clash or a turn. */
private fun checkAhead(
    conveyor: ConveyorEntity,
    ahead: ConveyorEntity,
    aheadTile: Coordinate,
    errors: MutableList<String>,
) {
    when (ahead.direction) {
        conveyor.direction -> {
            if (ahead.materials != conveyor.materials) {
                errors +=
                    "${conveyor.material.name} runs into " + "${ahead.material.name} at $aheadTile"
            }
        }
        conveyor.direction.opposite() ->
            errors +=
                "${conveyor.material.name} runs head-on into ${ahead.material.name} at $aheadTile"
        else -> {
            // a turn with the same load is fine (the mixed merge tile into its lane head)
            if (ahead.materials == conveyor.materials) return
            val lane =
                ahead
                    .laneLoad()
                    .laneAt(ahead.direction, directionBetween(aheadTile, conveyor.area.topLeft))
            if (lane != conveyor.material) {
                errors +=
                    "${conveyor.material.name} side-loads " +
                        "${lane?.name ?: "the empty lane"} at $aheadTile"
            }
        }
    }
}

/** A belt feeding [conveyor] from [side]: it must enter the lane on that side. */
private fun checkSideLoad(
    conveyor: ConveyorEntity,
    other: ConveyorEntity,
    tile: Coordinate,
    side: Coordinate,
    errors: MutableList<String>,
) {
    if (other.materials == conveyor.materials) return
    val lane = conveyor.laneLoad().laneAt(conveyor.direction, directionBetween(tile, side))
    if (lane != other.material) {
        errors += "foreign side-load into ${conveyor.material.name} at $tile"
    }
}

private fun ConveyorEntity.laneLoad(): LaneLoad =
    (this as? BeltEntity)?.load ?: LaneLoad.full(material)

private fun checkPlumbing(
    plumbing: Plumbing,
    index: LayoutIndex,
    errors: MutableList<String>,
) {
    val tile = plumbing.area.topLeft
    for (side in fourNeighbours(tile)) {
        val other = index.at(side) as? Plumbing ?: continue
        // a pipe-to-ground end only joins on its mouth, so two different fluids may share a wall
        if (other.material != plumbing.material && plumbing.joinsAt(side) && other.joinsAt(tile)) {
            errors += "fluids ${plumbing.material.name} and ${other.material.name} touch at $side"
        }
    }
}

private fun fourNeighbours(tile: Coordinate) =
    listOf(tile.north(), tile.east(), tile.south(), tile.west())
