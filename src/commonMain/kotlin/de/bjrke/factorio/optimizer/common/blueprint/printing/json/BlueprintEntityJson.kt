package de.bjrke.factorio.optimizer.common.blueprint.printing.json

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.ChestEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.CircuitCondition
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityChestEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityPipeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LoaderEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeToGroundEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PoleEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.SplitterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.facingDirection
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.Material
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * One placed entity as the blueprint wire format writes it. [entityNumber] is its one-based
 * position in the blueprint; the layout entity itself carries every field, so this is the only
 * place that knows how the domain maps onto the wire.
 */
internal fun LayoutEntity.toJson(entityNumber: Int) = buildJsonObject {
    val entity = this@toJson
    put("entity_number", entityNumber)
    put("name", entity.name)
    put("position", entity.positionJson())
    entity.facingDirection
        ?.takeIf { it != Direction.NORTH }
        ?.let {
            put("direction", it.blueprintNumber)
        }
    if (entity is MachineEntity) put("recipe", entity.recipe)
    entity.wireType()?.let { put("type", it.typeName) }
    entity.putSettings(this)
    if (entity is PoleEntity && entity.neighbours.isNotEmpty()) {
        put("neighbours", entity.neighbours.neighboursJson())
    }
    entity.controlBehaviorJson()?.let { put("control_behavior", it) }
}

private fun LayoutEntity.positionJson() = buildJsonObject {
    put("x", jsonNumber(area.topLeft.x + area.width / 2.0))
    put("y", jsonNumber(area.topLeft.y + area.height / 2.0))
}

/** An underground belt and a loader write their input/output side; a pipe-to-ground has none. */
private fun LayoutEntity.wireType(): Io? =
    when (this) {
        is UndergroundBeltEntity -> io
        is LoaderEntity -> io
        is MachineEntity,
        is BeltEntity,
        is SplitterEntity,
        is InserterEntity,
        is PipeEntity,
        is PipeToGroundEntity,
        is InfinityPipeEntity,
        is InfinityChestEntity,
        is ChestEntity,
        is MarkerEntity,
        is PoleEntity -> null
    }

/** The 2.0 convention doubles the cardinal values to 0/4/8/12. */
private val Direction.blueprintNumber: Int
    get() =
        when (this) {
            Direction.NORTH -> 0
            Direction.EAST -> 4
            Direction.SOUTH -> 8
            Direction.WEST -> 12
        }

private val Io.typeName: String
    get() =
        when (this) {
            Io.INPUT -> "input"
            Io.OUTPUT -> "output"
        }

private fun List<Int>.neighboursJson() = buildJsonArray {
    forEach { add(JsonPrimitive(it)) }
}

/** The one control behavior an entity writes. */
private fun LayoutEntity.controlBehaviorJson(): JsonObject? =
    when (this) {
        is ChestEntity ->
            circuitConditionEnabled?.let { enabled ->
                buildJsonObject { put("circuit_condition_enabled", enabled) }
            }
        is InserterEntity -> circuitCondition?.controlBehaviorJson()
        is MarkerEntity -> markerControlBehavior(signal.material, signal.fluid)
        is MachineEntity,
        is BeltEntity,
        is UndergroundBeltEntity,
        is SplitterEntity,
        is LoaderEntity,
        is PipeEntity,
        is PipeToGroundEntity,
        is InfinityPipeEntity,
        is InfinityChestEntity,
        is PoleEntity -> null
    }

private fun CircuitCondition.controlBehaviorJson() = buildJsonObject {
    put("circuit_enabled", true)
    putJsonObject("circuit_condition") {
        putJsonObject("first_signal") { put("name", name) }
        put("constant", constant)
        put("comparator", comparator)
    }
}

/**
 * The control behavior that makes a constant combinator display [material]. 2.0 models constant
 * combinators as logistic sections, each holding its filters.
 */
private fun markerControlBehavior(material: Material, fluid: Boolean) = buildJsonObject {
    put("is_on", true)
    putJsonObject("sections") {
        putJsonArray("sections") {
            addJsonObject {
                put("index", 1)
                putJsonArray("filters") {
                    addJsonObject {
                        put("index", 1)
                        // match what the game writes for a constant combinator;
                        // a filter without a concrete quality is not displayed
                        if (fluid) put("type", "fluid")
                        put("name", material.prototypeName)
                        put("quality", "normal")
                        put("comparator", "=")
                        put("count", 1)
                    }
                }
            }
        }
    }
}

/** Integral values are written as integers so e.g. `1.0` becomes the shorter `1`. */
private fun jsonNumber(value: Double): JsonPrimitive =
    if (value == value.toLong().toDouble()) JsonPrimitive(value.toLong()) else JsonPrimitive(value)
