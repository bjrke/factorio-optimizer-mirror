package de.bjrke.factorio.optimizer.common.blueprint.layout.validation

import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutIndex
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.core.model.Coordinate

/**
 * The invariant checks a compacted layout has to keep. It is deliberately absolute rather than a
 * diff: the original layout satisfies these, so any corridor that keeps them cannot merge two
 * materials, split a fluid network, misalign an underground run, leave an inserter without a source
 * or target, starve a splitter or loop a belt back into itself.
 *
 * Each check group lives in its own file (`LayoutValidationTransport.kt`,
 * `LayoutValidationUnderground.kt`, `LayoutValidationInserter.kt`, `LayoutValidationFlow.kt`) and
 * appends to the shared error list; their order is the order the errors are reported in.
 */
internal fun validateLayout(entities: List<LayoutEntity>): List<String> {
    val errors = mutableListOf<String>()
    val index = validateOverlaps(entities, errors)
    validateMaterials(index, entities, errors)
    validateUndergrounds(index, entities, errors)
    validateInserters(index, entities, errors)
    validateBeltFlow(index, entities, errors)
    return errors
}

/**
 * Fills [errors] with one entry per tile that two entities claim, and returns the tile index of
 * [entities] for the other checks.
 */
private fun validateOverlaps(
    entities: List<LayoutEntity>,
    errors: MutableList<String>,
): LayoutIndex {
    val tiles = HashMap<Coordinate, LayoutEntity>()
    for (entity in entities) {
        for (tile in entity.area) {
            val previous = tiles.put(tile, entity)
            if (previous != null && previous !== entity) errors += "overlap at $tile"
        }
    }
    return LayoutIndex(entities)
}
