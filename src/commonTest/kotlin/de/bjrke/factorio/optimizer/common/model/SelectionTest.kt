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
            Belt.entries.mapNotNull { it.code } +
            Inserter.entries.mapNotNull { it.code } +
            SelectionFlag.entries.map { it.code }

    private val belt = Belt.FAST_TRANSPORT_BELT
    private val inserter = Inserter.FAST_INSERTER

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
            { assertNull(Belt.fromSelection(EMPTY_SELECTION)) },
            { assertNull(Inserter.fromSelection(EMPTY_SELECTION)) },
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
                Belt.EXPRESS_TRANSPORT_BELT,
                Inserter.BULK_INSERTER,
            )

        assertAll(
            { assertEquals("Levy", selection) },
            { assertEquals(Surface.VULCANUS, surfaceFromUrl(selection)) },
            { assertTrue(SelectionFlag.FULL_BELT.enabledIn(selection)) },
            { assertEquals(Belt.EXPRESS_TRANSPORT_BELT, Belt.fromSelection(selection)) },
            { assertEquals(Inserter.BULK_INSERTER, Inserter.fromSelection(selection)) },
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
