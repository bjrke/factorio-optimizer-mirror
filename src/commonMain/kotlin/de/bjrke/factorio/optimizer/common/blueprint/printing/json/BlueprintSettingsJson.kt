package de.bjrke.factorio.optimizer.common.blueprint.printing.json

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.ChestEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityChestEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityPipeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityPipeSettings
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinitySettings
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LoaderEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeToGroundEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PoleEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.RequestFilter
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.SplitterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundBeltEntity
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** The stock an infinity chest keeps available, matching what the game writes into a blueprint. */
private const val INFINITY_STOCK = 100

/** Writes the machine, chest and inserter settings in their blueprint field order. */
internal fun LayoutEntity.putSettings(json: JsonObjectBuilder) {
    when (this) {
        is InfinityChestEntity -> json.put("infinity_settings", settings.chestJson())
        // an unfiltered infinity pipe is a sink: it writes no settings, so the game deletes what
        // reaches it
        is InfinityPipeEntity -> settings?.let { json.put("infinity_settings", it.pipeJson()) }
        is ChestEntity -> {
            requestFilter?.let { json.put("request_filters", it.requestFiltersJson()) }
            bar?.let { json.put("bar", it) }
        }
        is InserterEntity -> if (mirror) json.put("mirror", true)
        is MachineEntity,
        is MarkerEntity,
        is PoleEntity,
        is BeltEntity,
        is UndergroundBeltEntity,
        is SplitterEntity,
        is LoaderEntity,
        is PipeEntity,
        is PipeToGroundEntity -> Unit
    }
}

private fun InfinitySettings.chestJson() = buildJsonObject {
    put("remove_unfiltered_items", removeUnfiltered)
    item?.let { item ->
        putJsonArray("filters") {
            addJsonObject {
                put("name", item.prototypeName)
                put("count", INFINITY_STOCK)
                put("mode", "at-least")
                put("index", 1)
            }
        }
    }
}

private fun InfinityPipeSettings.pipeJson() = buildJsonObject {
    put("name", fluid.prototypeName)
    put("percentage", percentage)
    put("temperature", temperature)
    put("mode", mode)
}

private fun RequestFilter.requestFiltersJson() = buildJsonObject {
    putJsonArray("sections") {
        addJsonObject {
            put("index", 1)
            putJsonArray("filters") {
                addJsonObject {
                    put("index", 1)
                    put("name", name)
                    put("quality", "normal")
                    put("comparator", "=")
                    put("count", count)
                }
            }
        }
    }
    if (trashNotRequested) put("trash_not_requested", true)
}
