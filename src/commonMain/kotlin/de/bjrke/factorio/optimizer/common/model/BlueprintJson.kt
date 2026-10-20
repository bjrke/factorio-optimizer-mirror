package de.bjrke.factorio.optimizer.common.model

import kotlinx.serialization.json.*

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
        icon?.let { put("icons", iconJson(it)) }
        put("entities", entitiesJson())
        if (wires.isNotEmpty()) put("wires", wiresJson())
    }
}

private fun iconJson(icon: Material) = buildJsonArray {
    addJsonObject {
        put("index", 1)
        put("signal", buildJsonObject { put("name", icon.name) })
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
