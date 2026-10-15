package de.bjrke.factorio.optimizer.jvm

import de.bjrke.factorio.optimizer.common.business.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.model.Surface
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CliOptimizerTest {

    @Test
    fun defaultsToSpaceAgeAllSciencePacksAndNauvis() {
        val args = parseArgs(emptyArray())

        assertAll(
            { assertEquals(ALL_SCIENCE_PACKS, args.material) },
            { assertTrue(args.spaceAge) },
            { assertEquals(Surface.NAUVIS, args.surface) },
        )
    }

    @Test
    fun baseGameTurnsSpaceAgeOff() =
        assertAll(
            { assertFalse(parseArgs(arrayOf("--base-game")).spaceAge) },
            // unrelated flags keep Space Age on
            { assertTrue(parseArgs(arrayOf("--planet=vulcanus")).spaceAge) },
        )

    @Test
    fun positionalArgumentSelectsTheMaterial() {
        val args = parseArgs(arrayOf("iron-plate", "--base-game"))

        assertAll(
            { assertEquals("iron-plate", args.material) },
            { assertFalse(args.spaceAge) },
        )
    }

    @Test
    fun planetSelectsTheSurfaceByIdentifierOrCode() =
        assertTableOf(
            "vulcanus" to Surface.VULCANUS,
            "fulgora" to Surface.FULGORA,
            "p" to Surface.SPACE,
            "n" to Surface.NAUVIS,
        ) { (planet, expected) ->
            assertEquals(expected, parseArgs(arrayOf("--planet=$planet")).surface, planet)
        }
}
