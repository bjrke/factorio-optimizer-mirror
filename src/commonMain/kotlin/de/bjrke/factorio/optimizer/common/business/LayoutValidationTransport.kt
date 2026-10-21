package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*

/**
 * The belt and pipe checks: a belt must not run into another material's belt, nor take a foreign
 * side-load, and pipes of different fluids must never touch. Both read the same four neighbouring
 * tiles.
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

private fun checkConveyor(
    conveyor: ConveyorEntity,
    index: LayoutIndex,
    errors: MutableList<String>,
) {
    val tile = conveyor.area.topLeft
    val aheadTile = tile + conveyor.direction.step
    val ahead = index.at(aheadTile)
    if (ahead is ConveyorEntity && ahead.material != conveyor.material) {
        errors += "${conveyor.material.name} runs into ${ahead.material.name} at $aheadTile"
    }
    for (side in fourNeighbours(tile)) {
        val other = index.at(side) as? ConveyorEntity ?: continue
        if (other.direction.step == tile - side && other.material != conveyor.material) {
            errors += "foreign side-load into ${conveyor.material.name} at $tile"
        }
    }
}

private fun checkPlumbing(
    plumbing: Plumbing,
    index: LayoutIndex,
    errors: MutableList<String>,
) {
    val tile = plumbing.area.topLeft
    for (side in fourNeighbours(tile)) {
        val other = index.at(side) as? Plumbing ?: continue
        if (other.material != plumbing.material) {
            errors += "fluids ${plumbing.material.name} and ${other.material.name} touch at $side"
        }
    }
}

private fun fourNeighbours(tile: Coordinate) =
    listOf(tile.north(), tile.east(), tile.south(), tile.west())
