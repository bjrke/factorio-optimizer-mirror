package de.bjrke.factorio.optimizer.common.game.production

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.context.GameContext
import de.bjrke.factorio.optimizer.common.game.context.MaterialRegistry
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecipeParserIsFluidTest {

    /** A dump whose kept recipe consumes `plasma`, a fluid the dump also declares. */
    private val plasmaDump =
        """
        {"fluid":{"plasma":{}},
         "recipe":{"plasma-smelting":{
           "type":"recipe","name":"plasma-smelting","categories":["crafting"],
           "ingredients":[{"type":"fluid","name":"plasma","amount":10}],
           "results":[{"type":"item","name":"iron-plate","amount":1}]}}}
        """

    /** A dump that declares `plasma` but only keeps a solid item recipe. */
    private val solidDumpWithUnusedFluid =
        """
        {"fluid":{"plasma":{}},
         "recipe":{"iron-plate":{
           "type":"recipe","name":"iron-plate","categories":["smelting"],
           "ingredients":[{"type":"item","name":"iron-ore","amount":1}],
           "results":[{"type":"item","name":"iron-plate","amount":1}]}}}
        """

    @Test
    fun dumpFluidKeysBecomeFluidMaterials() {
        val parsed =
            parsePrototypes(
                """
                {"fluid":{"water":{},"crude-oil":{}},
                 "recipe":{"iron-plate":{
                   "type":"recipe","name":"iron-plate","categories":["smelting"],
                   "ingredients":[{"type":"item","name":"iron-ore","amount":1}],
                   "results":[{"type":"item","name":"iron-plate","amount":1}]}}}
                """,
            )

        fun Material(name: String): Material = parsed.gameCtx.materialRegistry.Material(name)

        assertAll(
            { assertTrue(Material("water").isFluid) },
            { assertTrue(Material("crude-oil").isFluid) },
            { assertFalse(Material("iron-plate").isFluid) },
        )
    }

    @Test
    fun aSuppliedRegistryMustAgreeOnDumpFluids() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                parsePrototypes(plasmaDump, gameContext(setOf("water")))
            }

        assertContains(error.message.orEmpty(), "plasma")
    }

    @Test
    fun aSuppliedRegistryThatCallsASolidAFluidIsRejected() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                parsePrototypes(
                    solidDumpWithUnusedFluid,
                    gameContext(setOf("water", "iron-plate")),
                )
            }

        assertContains(error.message.orEmpty(), "iron-plate")
    }

    @Test
    fun anUnreferencedDumpFluidNeedNotBeKnown() {
        val parsed = parsePrototypes(solidDumpWithUnusedFluid, gameContext(setOf("water")))

        assertFalse(parsed.gameCtx.materialRegistry.Material("plasma").isFluid)
    }

    @Test
    fun aSuppliedRegistryThatKnowsTheDumpFluidParses() {
        val parsed = parsePrototypes(plasmaDump, gameContext(setOf("water", "plasma")))

        assertTrue(parsed.gameCtx.materialRegistry.Material("plasma").isFluid)
    }

    private fun gameContext(fluidNames: Set<String>) = GameContext(MaterialRegistry(fluidNames))
}
