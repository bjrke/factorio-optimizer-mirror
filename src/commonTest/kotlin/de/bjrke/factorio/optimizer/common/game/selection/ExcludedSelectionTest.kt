package de.bjrke.factorio.optimizer.common.game.selection

import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.copperCable
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

class ExcludedSelectionTest : BaseGameCtxTest() {

    @Test
    fun theEmptyPlaceholderDecodesToNoExclusions() {
        assertAll(
            { assertEquals(emptySet(), materialRegistry.excludedMaterialsFromUrl(null)) },
            { assertEquals(emptySet(), materialRegistry.excludedMaterialsFromUrl("")) },
            {
                assertEquals(
                    emptySet(),
                    materialRegistry.excludedMaterialsFromUrl(NO_EXCLUSIONS),
                )
            },
        )
    }

    @Test
    fun materialsRoundTripInSortedOrder() {
        val materials = setOf(ironPlate, copperCable, gear)

        val segment = excludedMaterialsToUrl(materials)

        assertAll(
            { assertEquals("copper-cable,iron-gear-wheel,iron-plate", segment) },
            {
                assertEquals(
                    materials,
                    materialRegistry.excludedMaterialsFromUrl(segment),
                )
            },
        )
    }

    @Test
    fun theEmptySetEncodesToThePlaceholder() =
        assertEquals(NO_EXCLUSIONS, excludedMaterialsToUrl(emptySet()))

    @Test
    fun blankEntriesAreIgnored() {
        val gear = Material("gear")
        val pipe = Material("pipe")
        assertTableOf(
            "gear" to setOf(gear),
            "gear,,pipe" to setOf(gear, pipe),
            "gear,-,pipe" to setOf(gear, pipe),
        ) { (segment, expected) ->
            assertEquals(expected, materialRegistry.excludedMaterialsFromUrl(segment))
        }
    }

    @Test
    fun anUnknownNameIsDroppedInsteadOfInterned() {
        val sizeBefore = materialRegistry.size

        assertAll(
            {
                assertEquals(
                    emptySet(),
                    materialRegistry.excludedMaterialsFromUrl("not-a-material"),
                )
            },
            // a typo must not grow the process-wide registry
            { assertEquals(sizeBefore, materialRegistry.size) },
            // and it must not hide the names that do exist next to it
            {
                assertEquals(
                    setOf(gear),
                    materialRegistry.excludedMaterialsFromUrl("not-a-material,${gear.name}"),
                )
            },
        )
    }
}
