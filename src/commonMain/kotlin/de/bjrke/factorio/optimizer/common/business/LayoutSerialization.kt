package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*

/**
 * The one place a placed entity becomes the serializable [BlueprintEntity] DTO. The common part is
 * built once and each branch only names the fields it adds, so a new base field is set here alone.
 */
fun LayoutEntity.toBlueprintEntity(): BlueprintEntity {
    val common = BlueprintEntity(name, area)
    return when (this) {
        is MachineEntity -> common.copy(recipe = recipe)
        is InserterEntity ->
            common.copy(
                direction = direction,
                mirror = mirror,
                circuitCondition = circuitCondition,
            )
        is BeltEntity -> common.copy(direction = direction)
        is SplitterEntity -> common.copy(direction = direction)
        is UndergroundBeltEntity -> common.copy(direction = direction, type = io)
        is LoaderEntity -> common.copy(direction = direction, type = io)
        is InfinityChestEntity -> common.copy(infinity = settings)
        is InfinityPipeEntity -> common.copy(infinityPipe = settings)
        is ChestEntity ->
            common.copy(
                requestFilter = requestFilter,
                bar = bar,
                circuitConditionEnabled = circuitConditionEnabled,
            )
        is MarkerEntity -> common.copy(marker = signal.material, markerFluid = signal.fluid)
        is PipeEntity -> common
        // A pipe-to-ground carries no input/output `type` in a 2.0 blueprint: the game reads the
        // `direction` as the side the surface pipe attaches on, so the entrance of an underground
        // run faces back toward its source while the exit faces on toward the target. Internally
        // both ends are stored with the run's travel direction, so only the entrance is flipped.
        is PipeToGroundEntity ->
            common.copy(direction = if (io == Io.INPUT) direction.opposite() else direction)
        is PoleEntity -> common.copy(neighbours = neighbours)
    }
}
