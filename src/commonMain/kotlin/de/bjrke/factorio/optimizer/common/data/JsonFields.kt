package de.bjrke.factorio.optimizer.common.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

internal fun JsonObject?.primitive(name: String) = this?.get(name) as? JsonPrimitive

internal fun JsonObject?.string(name: String) = primitive(name)?.contentOrNull

internal fun JsonObject?.number(name: String) = primitive(name)?.doubleOrNull

internal fun JsonObject?.boolean(name: String) = primitive(name)?.booleanOrNull

internal fun JsonObject?.table(name: String) =
    (this?.get(name) as? JsonObject) ?: JsonObject(emptyMap())

internal fun JsonObject?.array(name: String) =
    (this?.get(name) as? JsonArray) ?: JsonArray(emptyList())

internal fun JsonObject?.objects(name: String) = array(name).filterIsInstance<JsonObject>()

internal fun JsonObject?.strings(name: String) =
    array(name).filterIsInstance<JsonPrimitive>().mapNotNull(JsonPrimitive::contentOrNull)

internal inline fun <reified R> JsonObject.filterValueIsInstance(): List<Pair<String, R>> =
    mapNotNull { (k, v) ->
        if (v is R) (k to v) else null
    }
