package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*

/**
 * The one place a placed entity becomes the serializable [BlueprintEntity] DTO. The common part is
 * built once and each branch only names the fields it adds, so a new base field is set here alone.
 * The groups follow the entity hierarchy: underground ends first (they are also conveyors or
 * plumbing), then the plain conveyor/plumbing cases, then the stationary kinds.
 */
fun LayoutEntity.toBlueprintEntity(): BlueprintEntity {
    val common = BlueprintEntity(name, area)
    return when (this) {
        is MachineEntity -> common.copy(recipe = recipe)
        is MarkerEntity -> common.copy(marker = signal.material, markerFluid = signal.fluid)
        is PoleEntity -> common.copy(neighbours = neighbours)
        is UndergroundEntity -> undergroundDto(common)
        is ConveyorEntity -> common.copy(direction = direction)
        is Plumbing -> plumbingDto(common)
        is ChestLikeEntity -> chestDto(common)
        is InserterEntity ->
            common.copy(
                direction = direction,
                mirror = mirror,
                circuitCondition = circuitCondition,
            )
        is LoaderEntity -> common.copy(direction = direction, type = io)
    }
}

private fun UndergroundEntity.undergroundDto(common: BlueprintEntity) =
    when (this) {
        is UndergroundBeltEntity -> common.copy(direction = direction, type = io)
        // A pipe-to-ground carries no input/output `type` in a 2.0 blueprint: the game reads the
        // `direction` as the side the surface pipe attaches on, so the entrance of an underground
        // run faces back toward its source while the exit faces on toward the target. Internally
        // both ends are stored with the run's travel direction, so only the entrance is flipped.
        is PipeToGroundEntity ->
            common.copy(direction = if (io == Io.INPUT) direction.opposite() else direction)
    }

private fun Plumbing.plumbingDto(common: BlueprintEntity) =
    when (this) {
        is InfinityPipeEntity -> common.copy(infinityPipe = settings)
        is PipeEntity -> common
        else -> common
    }

private fun ChestLikeEntity.chestDto(common: BlueprintEntity) =
    when (this) {
        is InfinityChestEntity -> common.copy(infinity = settings)
        is ChestEntity ->
            common.copy(
                requestFilter = requestFilter,
                bar = bar,
                circuitConditionEnabled = circuitConditionEnabled,
            )
    }
