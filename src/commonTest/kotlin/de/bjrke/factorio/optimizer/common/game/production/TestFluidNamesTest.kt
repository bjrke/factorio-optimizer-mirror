package de.bjrke.factorio.optimizer.common.game.production

import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.TEST_FLUID_NAMES
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The fixture universe's fluid split must match the shipped dumps. A fluid the fixtures call solid
 * would ride their belts and silently test the wrong layout, and a solid they call fluid the
 * opposite, so the split is compared against both dumps here instead of at every suite.
 *
 * `TEST_FLUID_NAMES` is curated instead of derived from the dump on purpose: deriving it would
 * couple every fixture suite to the dump, so the first suite to build a registry would decode one —
 * including the light component suites that need no prototype data. This test is what keeps the
 * curated set in step with the dump.
 */
class TestFluidNamesTest : BaseGameCtxTest() {

    @Test
    fun theFixtureFluidNamesAreTheDumpsRealFluids() {
        val baseFluids = PrototypeDump.load(spaceAge = false).fluidNames
        val spaceAgeFluids = PrototypeDump.load(spaceAge = true).fluidNames
        val realFluids = (baseFluids + spaceAgeFluids).filterNotTo(mutableSetOf(), ::isPlaceholder)

        assertEquals(realFluids, TEST_FLUID_NAMES)
    }

    /**
     * The fixture universe applies that split, so a synthetic recipe sees the dump's fluid-ness.
     */
    @Test
    fun theFixtureUniverseMarksTheDumpsFluidsAsFluid() =
        assertTableOf(water to true, ironPlate to false) { (material, expected) ->
            assertEquals(expected, material.isFluid)
        }
}

/**
 * `data.raw.fluid` also lists `fluid-unknown` and the `parameter-*` placeholders: prototypes no
 * recipe reads and no fixture names, which the parser's fluid check skips for the same reason.
 */
private fun isPlaceholder(name: String) = name == "fluid-unknown" || name.startsWith("parameter-")
