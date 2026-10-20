package de.bjrke.factorio.optimizer.jvm

import de.bjrke.factorio.optimizer.common.business.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.business.CalculatorSetup
import de.bjrke.factorio.optimizer.common.business.calculator
import de.bjrke.factorio.optimizer.common.model.BeltEnd
import de.bjrke.factorio.optimizer.common.model.BeltTier
import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.InserterTier
import de.bjrke.factorio.optimizer.common.model.Surface
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
            { assertEquals(emptySet(), args.outputs) },
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
            { assertContains(parseArgs(arrayOf("--blueprint")).outputs, Output.BLUEPRINT) },
            { assertEquals(emptySet(), parseArgs(arrayOf("iron-plate")).outputs) },
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
            // CFSXbmr contains S (Space Age) and X (the infinity chest)
            { assertTrue(args.spaceAge) },
            { assertEquals(BeltEnd.INFINITY_CHEST, args.beltEnd) },
        )
    }

    @Test
    fun beltEndIsParsedByIdentifierOrCode() =
        assertAll(
            {
                assertEquals(
                    BeltEnd.BUFFER_CHEST,
                    parseArgs(arrayOf("--belt-end=buffer-chest")).beltEnd,
                )
            },
            { assertEquals(BeltEnd.BUFFER_CHEST, parseArgs(arrayOf("--belt-end=H")).beltEnd) },
            // --cheats still selects the infinity chest
            { assertEquals(BeltEnd.INFINITY_CHEST, parseArgs(arrayOf("--cheats")).beltEnd) },
            { assertEquals(BeltEnd.CONSTANT_COMBINATOR, parseArgs(emptyArray()).beltEnd) },
            // a chest marker needs ground, so it falls back to the label on a space platform
            {
                assertEquals(
                    BeltEnd.CONSTANT_COMBINATOR,
                    parseArgs(arrayOf("--planet=p", "--belt-end=buffer-chest")).beltEnd,
                )
            },
        )

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
            {
                assertTableOf(
                    listOf("--belt=e") to BeltTier.EXPRESS_TRANSPORT_BELT,
                    listOf("--belt=express-transport-belt") to BeltTier.EXPRESS_TRANSPORT_BELT,
                ) { (args, expected) ->
                    assertEquals(expected, parseArgs(args.toTypedArray()).belt)
                }
            },
            {
                assertEquals(
                    InserterTier.STACK_INSERTER,
                    parseArgs(arrayOf("--inserter=stack-inserter")).inserter,
                )
            },
            { assertEquals("4", parseArgs(arrayOf("--belt-stack=4")).beltStack) },
            { assertEquals("8", parseArgs(arrayOf("--inserter-stack=8")).inserterStack) },
            { assertEquals(true, parseArgs(arrayOf("--full-belt")).fullBelt) },
            { assertNull(parseArgs(emptyArray()).fullBelt) },
        )

    @Test
    fun factoryFlagsCollectMachinesByIdentifierOrCode() =
        assertTableOf(
            listOf("--factory=assembling-machine-2") to listOf(Factory.ASSEMBLING_MACHINE_2),
            listOf("--factory=F") to listOf(Factory.FOUNDRY),
            listOf("--factory=foundry", "--factory=assembling-machine-2") to
                listOf(Factory.FOUNDRY, Factory.ASSEMBLING_MACHINE_2),
        ) { (args, expected) ->
            assertEquals(expected, parseArgs(args.toTypedArray()).factories)
        }

    @Test
    fun settingsIsAnOutput() =
        assertAll(
            { assertContains(parseArgs(arrayOf("--settings")).outputs, Output.SETTINGS) },
            { assertContainsNot(parseArgs(arrayOf("--json")).outputs, Output.SETTINGS) },
        )

    @Test
    fun settingsTextListsTheResolvedConfiguration() {
        val logistics =
            calculator(
                CalculatorSetup(
                    belt = BeltTier.EXPRESS_TRANSPORT_BELT,
                    inserter = InserterTier.STACK_INSERTER,
                    lanes = 2,
                    beltStack = 4,
                    inserterStack = 6,
                ),
            )
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
                logistics = logistics,
            )

        assertAll(
            { assertTrue(text.startsWith("settings\n"), text) },
            {
                assertTableOf(
                    "  material: advanced-circuit",
                    "  beltEnd: constant-combinator",
                    "  belt: express-transport-belt",
                    "  inserter: stack-inserter",
                    "  beltLanes: 2",
                    "  beltStack: 4",
                    "  factories: assembling-machine-3, foundry",
                ) { expected ->
                    assertContains(text, expected, message = text)
                }
            },
        )
    }
}
