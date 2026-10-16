package de.bjrke.factorio.optimizer.jvm

import de.bjrke.factorio.optimizer.common.business.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.model.BeltTier
import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.InserterTier
import de.bjrke.factorio.optimizer.common.model.Surface
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CliOptimizerTest {

    private val url = "http://localhost:8080/#/4.5/advanced-circuit/CFSXbmr/1/1"

    @Test
    fun defaultsToSpaceAgeAllSciencePacksAndNauvis() {
        val args = parseArgs(emptyArray())

        assertAll(
            { assertEquals(ALL_SCIENCE_PACKS, args.material) },
            { assertTrue(args.spaceAge) },
            { assertEquals(Surface.NAUVIS, args.surface) },
            { assertTrue(args.outputs.isEmpty()) },
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
    fun outputFlagsAreNotExclusive() =
        assertAll(
            {
                assertEquals(
                    setOf(Output.LAYOUT, Output.JSON),
                    parseArgs(arrayOf("--json", "--layout")).outputs,
                )
            },
            { assertTrue(Output.BLUEPRINT in parseArgs(arrayOf("--blueprint")).outputs) },
            { assertTrue(parseArgs(arrayOf("iron-plate")).outputs.isEmpty()) },
        )

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

    @Test
    fun aPastedUrlProvidesMaterialSelectionAndStacks() {
        val args = parseArgs(arrayOf(url))

        assertAll(
            { assertEquals("advanced-circuit", args.material) },
            { assertEquals(4.5, args.normalizeTarget) },
            { assertEquals("CFSXbmr", args.selection) },
            { assertEquals("1", args.beltStack) },
            { assertEquals("1", args.inserterStack) },
            // CFSXbmr contains S (Space Age) and X (cheats)
            { assertTrue(args.spaceAge) },
            { assertTrue(args.cheats) },
        )
    }

    @Test
    fun aBaseGameUrlHasNoSpaceAgeFlag() =
        assertAll(
            { assertFalse(parseArgs(arrayOf("#/4.5/advanced-circuit/-/1/1")).spaceAge) },
            // and always runs on Nauvis
            {
                assertEquals(
                    Surface.NAUVIS,
                    parseArgs(arrayOf("#/4.5/advanced-circuit/-/1/1")).surface,
                )
            },
        )

    @Test
    fun theUrlSurfaceCodeSelectsTheSurface() {
        val args = parseArgs(arrayOf("#/2/advanced-circuit/Sv/1/1"))

        assertAll(
            { assertEquals(2.0, args.normalizeTarget) },
            { assertEquals(Surface.VULCANUS, args.surface) },
        )
    }

    @Test
    fun explicitFlagsOverrideTheUrl() =
        assertAll(
            // a positional material wins over the URL target
            { assertEquals("iron-plate", parseArgs(arrayOf(url, "iron-plate")).material) },
            // --base-game wins over the URL's Space Age flag
            { assertFalse(parseArgs(arrayOf(url, "--base-game")).spaceAge) },
            // --planet= wins over the URL's surface code
            {
                assertEquals(
                    Surface.FULGORA,
                    parseArgs(arrayOf(url, "--planet=fulgora")).surface,
                )
            },
            // --normalize= wins over the URL's normalize target
            { assertEquals(3.0, parseArgs(arrayOf(url, "--normalize=3")).normalizeTarget) },
        )

    @Test
    fun logisticsFlagsAreParsedByIdentifierOrCode() =
        assertAll(
            { assertEquals(BeltTier.EXPRESS_TRANSPORT_BELT, parseArgs(arrayOf("--belt=e")).belt) },
            {
                assertEquals(
                    BeltTier.EXPRESS_TRANSPORT_BELT,
                    parseArgs(arrayOf("--belt=express-transport-belt")).belt,
                )
            },
            {
                assertEquals(
                    InserterTier.STACK_INSERTER,
                    parseArgs(arrayOf("--inserter=stack-inserter")).inserter,
                )
            },
            { assertEquals(true, parseArgs(arrayOf("--full-belt")).fullBelt) },
            { assertEquals(null, parseArgs(emptyArray()).fullBelt) },
            { assertEquals("4", parseArgs(arrayOf("--belt-stack=4")).beltStack) },
            { assertEquals("8", parseArgs(arrayOf("--inserter-stack=8")).inserterStack) },
        )

    @Test
    fun factoryFlagsCollectMachinesByIdentifierOrCode() =
        assertAll(
            {
                assertEquals(
                    listOf(Factory.ASSEMBLING_MACHINE_2),
                    parseArgs(arrayOf("--factory=assembling-machine-2")).factories,
                )
            },
            { assertEquals(listOf(Factory.FOUNDRY), parseArgs(arrayOf("--factory=F")).factories) },
            {
                assertEquals(
                    listOf(Factory.FOUNDRY, Factory.ASSEMBLING_MACHINE_2),
                    parseArgs(
                            arrayOf(
                                "--factory=foundry",
                                "--factory=assembling-machine-2",
                            ),
                        )
                        .factories,
                )
            },
        )

    @Test
    fun settingsIsAnOutput() =
        assertAll(
            { assertTrue(Output.SETTINGS in parseArgs(arrayOf("--settings")).outputs) },
            { assertFalse(Output.SETTINGS in parseArgs(arrayOf("--json")).outputs) },
        )

    @Test
    fun settingsTextListsTheResolvedConfiguration() {
        val text =
            settingsText(
                parseArgs(
                    arrayOf(
                        "advanced-circuit",
                        "--belt=express-transport-belt",
                        "--inserter=stack-inserter",
                        "--full-belt",
                        "--belt-stack=4",
                    ),
                ),
                factories = setOf(Factory.ASSEMBLING_MACHINE_3, Factory.FOUNDRY),
                belt = BeltTier.EXPRESS_TRANSPORT_BELT,
                inserter = InserterTier.STACK_INSERTER,
                lanes = 2,
                beltStack = 4,
                inserterStack = 6,
            )

        assertAll(
            { assertTrue(text.startsWith("settings\n"), text) },
            { assertTrue(text.contains("  material: advanced-circuit"), text) },
            { assertTrue(text.contains("  belt: express-transport-belt"), text) },
            { assertTrue(text.contains("  inserter: stack-inserter"), text) },
            { assertTrue(text.contains("  beltLanes: 2"), text) },
            { assertTrue(text.contains("  beltStack: 4"), text) },
            { assertTrue(text.contains("  factories: assembling-machine-3, foundry"), text) },
        )
    }
}
