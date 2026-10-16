package de.bjrke.factorio.optimizer.common.model

import de.bjrke.test.assertAll
import de.bjrke.test.assertTable
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
            SelectionFlag.entries.map { it.code }

    private val belt = BeltTier.FAST_TRANSPORT_BELT
    private val inserter = InserterTier.FAST_INSERTER

    private fun defaultSelection(spaceAge: Boolean) =
        selectionString(
            FactoryConfig(spaceAge).defaults,
            spaceAge,
            Surface.NAUVIS,
            fullBelt = false,
            belt,
            inserter,
        )

    @Test
    fun everySelectionCodeIsUnique() {
        val duplicates = allCodes.groupingBy { it }.eachCount().filterValues { it > 1 }.keys

        assertTrue(duplicates.isEmpty(), "duplicate selection codes: $duplicates")
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
                FactoryConfig(false).defaults,
                spaceAge = false,
                Surface.VULCANUS,
                fullBelt = true,
                BeltTier.EXPRESS_TRANSPORT_BELT,
                InserterTier.BULK_INSERTER,
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
        assertAll(
            { assertTrue(SelectionFlag.SPACE_AGE.enabledIn("S")) },
            { assertTrue(SelectionFlag.SPACE_AGE.enabledIn("abcSL")) },
            { assertFalse(SelectionFlag.SPACE_AGE.enabledIn("L")) },
            { assertFalse(SelectionFlag.SPACE_AGE.enabledIn(null)) },
        )
}
