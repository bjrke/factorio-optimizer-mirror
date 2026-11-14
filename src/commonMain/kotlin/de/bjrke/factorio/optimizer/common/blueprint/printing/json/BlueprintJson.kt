package de.bjrke.factorio.optimizer.common.blueprint.printing.json

import de.bjrke.factorio.optimizer.common.blueprint.printing.model.Blueprint
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Serializes the blueprint to the compact JSON Factorio embeds in a blueprint string. Optional
 * fields are omitted and integral coordinates are written without a decimal part, both to keep the
 * string short.
 */
fun Blueprint.toJson(): String = blueprintJson().toString()

private fun Blueprint.blueprintJson() = buildJsonObject {
    putJsonObject("blueprint") {
        put("item", "blueprint")
        put("label", label)
        if (icons.isNotEmpty()) put("icons", iconsJson())
        put("entities", entitiesJson())
        if (wires.isNotEmpty()) put("wires", wiresJson())
    }
}

private fun Blueprint.iconsJson() = buildJsonArray {
    icons.forEachIndexed { index, icon ->
        addJsonObject {
            put("index", index + 1)
            putJsonObject("signal") {
                // a fluid target is the only icon signal whose type the game cannot infer
                if (icon.fluid) put("type", "fluid")
                put("name", icon.name)
            }
        }
    }
}

private fun Blueprint.entitiesJson() = buildJsonArray {
    entities.forEachIndexed { index, entity -> add(entity.toJson(index + 1)) }
}

private fun Blueprint.wiresJson() = buildJsonArray {
    wires.forEach { wire ->
        addJsonArray {
            add(wire.first)
            add(wire.firstConnector)
            add(wire.second)
            add(wire.secondConnector)
        }
    }
}
