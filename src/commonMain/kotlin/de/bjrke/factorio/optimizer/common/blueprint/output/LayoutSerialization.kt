package de.bjrke.factorio.optimizer.common.blueprint.output

import de.bjrke.factorio.optimizer.common.blueprint.model.BlueprintEntity
import de.bjrke.factorio.optimizer.common.layout.entity.ChestEntity
import de.bjrke.factorio.optimizer.common.layout.entity.ChestLikeEntity
import de.bjrke.factorio.optimizer.common.layout.entity.ConveyorEntity
import de.bjrke.factorio.optimizer.common.layout.entity.InfinityChestEntity
import de.bjrke.factorio.optimizer.common.layout.entity.InfinityPipeEntity
import de.bjrke.factorio.optimizer.common.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.layout.entity.LoaderEntity
import de.bjrke.factorio.optimizer.common.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.layout.entity.PipeEntity
import de.bjrke.factorio.optimizer.common.layout.entity.PipeToGroundEntity
import de.bjrke.factorio.optimizer.common.layout.entity.Plumbing
import de.bjrke.factorio.optimizer.common.layout.entity.PoleEntity
import de.bjrke.factorio.optimizer.common.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.layout.entity.UndergroundEntity
import de.bjrke.factorio.optimizer.common.model.Direction.NORTH
import de.bjrke.factorio.optimizer.common.model.Io

/**
 * The one place a placed entity becomes the serializable [BlueprintEntity] DTO. The common part is
 * built once and each branch only names the fields it adds, so a new base field is set here alone.
 * The groups follow the entity hierarchy: underground ends first (they are also conveyors or
 * plumbing), then the plain conveyor/plumbing cases, then the stationary kinds.
 */
internal fun LayoutEntity.toBlueprintEntity(): BlueprintEntity {
    val common = BlueprintEntity(name, area)
    return when (this) {
        is MachineEntity ->
            common.copy(
                recipe = recipe,
                direction = direction.takeIf { it != NORTH },
            )
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
