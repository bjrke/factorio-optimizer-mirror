package de.bjrke.factorio.optimizer.common.model

import de.bjrke.factorio.optimizer.common.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.prototype.BeltTier
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.prototype.FactoryConfig
import de.bjrke.factorio.optimizer.common.prototype.InserterTier
import de.bjrke.factorio.optimizer.common.prototype.Surface
import de.bjrke.factorio.optimizer.common.prototype.selection.EMPTY_SELECTION
import de.bjrke.factorio.optimizer.common.prototype.selection.Selection
import de.bjrke.factorio.optimizer.common.prototype.selection.SelectionFlag
import de.bjrke.factorio.optimizer.common.prototype.selection.beltEndFromSelection
import de.bjrke.factorio.optimizer.common.prototype.selection.selectionString
import de.bjrke.factorio.optimizer.common.prototype.selection.surfaceFromUrl
import de.bjrke.test.assertAll
import de.bjrke.test.assertTable
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SelectionTest {

    /** Every code that can occur in the single `selection` URL parameter. */
    private val allCodes =
        Factory.entries.mapNotNull { it.code } +
            Surface.entries.mapNotNull { it.code } +
            BeltTier.entries.mapNotNull { it.code } +
            InserterTier.entries.mapNotNull { it.code } +
            SelectionFlag.entries.map { it.code } +
            BeltEnd.entries.mapNotNull { it.code }

    private val belt = BeltTier.FAST_TRANSPORT_BELT
    private val inserter = InserterTier.FAST_INSERTER

    private fun defaultSelection(spaceAge: Boolean) =
        selectionString(
            Selection(
                FactoryConfig(spaceAge).defaults,
                spaceAge,
                Surface.NAUVIS,
                fullBelt = false,
                belt,
                inserter,
            ),
        )

    @Test
    fun everySelectionCodeIsUnique() {
        val duplicates = allCodes.groupingBy { it }.eachCount().filterValues { it > 1 }.keys

        assertEquals(emptySet(), duplicates, "duplicate selection codes")
    }

    @Test
    fun defaultBaseGameSelectionIsEmpty() = assertEquals("", defaultSelection(spaceAge = false))

    @Test
    fun theEmptyPlaceholderDecodesToTheBaseGameDefaults() =
        assertAll(
            { assertFalse(SelectionFlag.SPACE_AGE.enabledIn(EMPTY_SELECTION)) },
            {
                assertEquals(
                    FactoryConfig(false).defaults,
                    FactoryConfig(false).fromUrl(EMPTY_SELECTION),
                )
            },
            { assertNull(surfaceFromUrl(EMPTY_SELECTION)) },
            { assertNull(BeltTier.fromSelection(EMPTY_SELECTION)) },
            { assertNull(InserterTier.fromSelection(EMPTY_SELECTION)) },
        )

    @Test
    fun factorySelectionRoundTrip() =
        assertTable(listOf(false, true)) { spaceAge ->
            val config = FactoryConfig(spaceAge)

            assertEquals(config.defaults, config.fromUrl(defaultSelection(spaceAge)), "$spaceAge")
        }

    @Test
    fun defaultSpaceAgeSelectionKeepsTheDefaultOnMachines() {
        val selection = defaultSelection(spaceAge = true)

        assertAll(
            { assertTrue(SelectionFlag.SPACE_AGE.enabledIn(selection)) },
            {
                assertTrue(
                    FactoryConfig(true).singles.mapNotNull { it.code }.all { it in selection },
                    "the default-on machines keep their code: $selection",
                )
            },
        )
    }

    @Test
    fun nonDefaultChoicesAreEncodedAndDecodeBack() {
        val selection =
            selectionString(
                Selection(
                    FactoryConfig(false).defaults,
                    spaceAge = false,
                    Surface.VULCANUS,
                    fullBelt = true,
                    BeltTier.EXPRESS_TRANSPORT_BELT,
                    InserterTier.BULK_INSERTER,
                ),
            )

        assertAll(
            { assertEquals("Levy", selection) },
            { assertEquals(Surface.VULCANUS, surfaceFromUrl(selection)) },
            { assertTrue(SelectionFlag.FULL_BELT.enabledIn(selection)) },
            { assertEquals(BeltTier.EXPRESS_TRANSPORT_BELT, BeltTier.fromSelection(selection)) },
            { assertEquals(InserterTier.BULK_INSERTER, InserterTier.fromSelection(selection)) },
        )
    }

    @Test
    fun flagsAreOffWhenTheirCodeIsAbsent() =
        assertTableOf<Pair<String?, Boolean>>(
            "S" to true,
            "abcSL" to true,
            "L" to false,
            null to false,
        ) { (selection, expected) ->
            assertEquals(expected, SelectionFlag.SPACE_AGE.enabledIn(selection))
        }

    private fun beltEndSelection(spaceAge: Boolean, surface: Surface, beltEnd: BeltEnd) =
        selectionString(
            Selection(
                FactoryConfig(spaceAge).defaults,
                spaceAge,
                surface,
                fullBelt = false,
                belt,
                inserter,
                beltEnd = beltEnd,
            ),
        )

    @Test
    fun theBeltEndSelectionRoundTrips() =
        assertTable(BeltEnd.entries) { beltEnd ->
            val selection = beltEndSelection(spaceAge = true, Surface.NAUVIS, beltEnd)

            assertEquals(beltEnd, beltEndFromSelection(selection, Surface.NAUVIS))
        }

    @Test
    fun aChestMarkerFallsBackToTheLabelInSpace() =
        assertTable(BeltEnd.entries) { beltEnd ->
            val selection = beltEndSelection(spaceAge = true, Surface.SPACE, beltEnd)
            val expected =
                if (beltEnd.allowedOn(Surface.SPACE)) beltEnd else BeltEnd.CONSTANT_COMBINATOR

            assertEquals(expected, beltEndFromSelection(selection, Surface.SPACE))
        }

    @Test
    fun theOldCheatCodeStillSelectsTheInfinityChest() =
        assertEquals(BeltEnd.INFINITY_CHEST, beltEndFromSelection("X", Surface.NAUVIS))
}
