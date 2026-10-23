package de.bjrke.factorio.optimizer.common.blueprint.output

import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlueprintStringTest {

    @Test
    fun base64MatchesTheRfc4648Vectors() =
        assertTableOf<Pair<String, String>>(
            "" to "",
            "M" to "TQ==",
            "Ma" to "TWE=",
            "Man" to "TWFu",
            "Many" to "TWFueQ==",
        ) { (plain, encoded) ->
            assertEquals(encoded, base64Encode(plain.encodeToByteArray()))
        }

    @Test
    fun adler32MatchesTheKnownValue() {
        // Adler-32 of "abc" is the canonical example value
        assertEquals(0x024D0127, adler32("abc".encodeToByteArray()))
    }

    @Test
    fun theStreamStartsWithTheZlibHeader() {
        val stream = zlibStored("hello".encodeToByteArray())
        assertAll(
            { assertEquals(0x78, stream[0].toInt() and 0xFF) },
            { assertEquals(0x01, stream[1].toInt() and 0xFF) },
        )
    }

    @Test
    fun aBlueprintStringStartsWithTheVersionByte() {
        val string = blueprintString("""{"blueprint":{"item":"blueprint"}}""")
        assertTrue(string.startsWith("0"), "expected the '0' version prefix, got ${string.take(4)}")
    }
}
