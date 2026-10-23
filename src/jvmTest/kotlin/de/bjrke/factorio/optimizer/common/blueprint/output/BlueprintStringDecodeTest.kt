package de.bjrke.factorio.optimizer.common.blueprint.output

import java.util.*
import java.util.zip.Inflater
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Decodes our blueprint strings with the JDK's zlib, which is the same DEFLATE implementation
 * Factorio's importer uses. This proves the stored-block stream and the base64 wrapping are valid,
 * not just self-consistent.
 */
class BlueprintStringDecodeTest {

    private fun decode(blueprint: String): String {
        val compressed = Base64.getDecoder().decode(blueprint.substring(1))
        val inflater = Inflater()
        inflater.setInput(compressed)
        val output = ByteArray(1 shl 21)
        val size = inflater.inflate(output)
        inflater.end()
        return output.decodeToString(0, size)
    }

    @Test
    fun theJdkInflatesTheStoredStream() {
        val json = """{"blueprint":{"item":"blueprint","label":"test"}}"""
        assertEquals(json, decode(blueprintString(json)))
    }

    @Test
    fun aStringLargerThanOneStoredBlockRoundTrips() {
        // the length field of a stored block is 16 bits, so this spans several blocks
        val json = "{\"label\":\"" + "a".repeat(70_000) + "\"}"
        assertEquals(json, decode(blueprintString(json)))
    }
}
