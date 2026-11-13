package de.bjrke.factorio.optimizer.common.game.selection

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

class ExcludedSelectionTest {

    @Test
    fun theEmptyPlaceholderDecodesToNoExclusions() =
        assertAll(
            { assertEquals(emptySet(), excludedMaterialsFromUrl(null)) },
            { assertEquals(emptySet(), excludedMaterialsFromUrl("")) },
            { assertEquals(emptySet(), excludedMaterialsFromUrl(NO_EXCLUSIONS)) },
        )

    @Test
    fun materialsRoundTripInSortedOrder() {
        val materials = setOf(Material("iron-plate"), Material("copper-cable"), Material("gear"))

        val segment = excludedMaterialsToUrl(materials)

        assertAll(
            { assertEquals("copper-cable,gear,iron-plate", segment) },
            { assertEquals(materials, excludedMaterialsFromUrl(segment)) },
        )
    }

    @Test
    fun theEmptySetEncodesToThePlaceholder() =
        assertEquals(NO_EXCLUSIONS, excludedMaterialsToUrl(emptySet()))

    @Test
    fun blankEntriesAreIgnored() =
        assertTableOf(
            "gear" to setOf(Material("gear")),
            "gear,,pipe" to setOf(Material("gear"), Material("pipe")),
            "gear,-,pipe" to setOf(Material("gear"), Material("pipe")),
        ) { (segment, expected) ->
            assertEquals(expected, excludedMaterialsFromUrl(segment))
        }
}
