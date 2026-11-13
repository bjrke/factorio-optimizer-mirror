package de.bjrke.factorio.optimizer.common.game.production

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

/** The SI suffix of an energy or power value, as the factor to its base unit (joule or watt). */
private val energyUnits =
    mapOf(
        "J" to 1.0,
        "kJ" to 1e3,
        "MJ" to 1e6,
        "GJ" to 1e9,
        "TJ" to 1e12,
        "W" to 1.0,
        "kW" to 1e3,
        "MW" to 1e6,
        "GW" to 1e9,
    )

/**
 * The base-unit value of an energy or power string: `"4MJ"` reads as 4e6, `"90kW"` as 9e4. The game
 * writes `fuel_value` as an energy and `energy_usage` as a power, both with an SI suffix, so one
 * parser reads either.
 */
internal fun JsonObject?.energyValue(name: String): Double? {
    val text = string(name) ?: return null
    val number = text.takeWhile { it.isDigit() || it == '.' }
    val unit = energyUnits[text.removePrefix(number)] ?: return null
    return number.toDoubleOrNull()?.times(unit)
}

internal inline fun <reified R> JsonObject.filterValueIsInstance(): List<Pair<String, R>> =
    mapNotNull { (k, v) ->
        if (v is R) (k to v) else null
    }
