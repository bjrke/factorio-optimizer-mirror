package de.bjrke.factorio.optimizer.common.game.prototype

import de.bjrke.factorio.optimizer.common.blueprint.planning.band.buildable
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.game.production.RecipeLoader
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import de.bjrke.test.assertTable
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/** The explicit recipe exclusions the optimizer and the blueprint planner share. */
class RecipePolicyTest {

    private val allSolidFuel =
        setOf(
            "solid-fuel-from-light-oil",
            "solid-fuel-from-heavy-oil",
            "solid-fuel-from-petroleum-gas",
            "solid-fuel-from-ammonia",
        )

    /**
     * The cycles the policy leaves in the LP for now: the Gleba bacteria and pentapod recipes breed
     * from their own product. Any other self-consuming recipe the planner would build needs a name
     * in `unplannedRecipes` or an entry here.
     */
    private val knownSelfConsumingRecipes =
        setOf(
            "copper-bacteria-cultivation",
            "iron-bacteria-cultivation",
            "pentapod-egg",
        )

    @Test
    fun onlyTheSurfaceSolidFuelRecipeStaysPlanned() =
        assertTable(
            listOf(
                Surface.NAUVIS to "solid-fuel-from-light-oil",
                Surface.VULCANUS to "solid-fuel-from-light-oil",
                Surface.GLEBA to "solid-fuel-from-light-oil",
                Surface.FULGORA to "solid-fuel-from-heavy-oil",
                Surface.AQUILO to "solid-fuel-from-ammonia",
            ),
        ) { (surface, chosen) ->
            val banned = unplannedRecipes(surface)
            assertEquals(allSolidFuel - chosen, banned intersect allSolidFuel)
        }

    @Test
    fun theSurfaceBansTheCoalOrCarbonRecipeItDoesNotNeed() =
        assertTable(
            listOf(
                Surface.NAUVIS to "coal-synthesis",
                Surface.VULCANUS to "coal-synthesis",
                Surface.GLEBA to "carbon",
                Surface.FULGORA to "carbon",
                Surface.AQUILO to "carbon",
                Surface.SPACE to "carbon",
            ),
        ) { (surface, unneeded) ->
            val banned = unplannedRecipes(surface)
            assertAll(
                { assertContains(banned, unneeded) },
                {
                    assertContainsNot(
                        banned,
                        if (unneeded == "carbon") "coal-synthesis" else "carbon",
                    )
                },
            )
        }

    @Test
    fun spaceBansEverySolidFuelRecipe() {
        val banned = unplannedRecipes(Surface.SPACE)
        assertEquals(allSolidFuel, banned intersect allSolidFuel)
    }

    @Test
    fun theCyclesAreBannedOnEverySurface() =
        assertTable(Surface.entries) { surface ->
            val banned = unplannedRecipes(surface)
            assertAll(
                { assertContains(banned, "fish-breeding") },
                { assertContains(banned, "nutrients-from-fish") },
                { assertContains(banned, "kovarex-enrichment-process") },
            )
        }

    @Test
    fun everySelfConsumingRecipeIsGatedOrKnown() =
        assertEquals(knownSelfConsumingRecipes, selfConsumingRecipesThePolicyAllows())

    /**
     * Every self-consuming recipe [buildable] accepts, on any surface, minus the policy's bans. A
     * new loop appears here, and a loop that gets gated disappears from it.
     */
    private fun selfConsumingRecipesThePolicyAllows() = buildSet {
        for (surface in Surface.entries) {
            val parsed = RecipeLoader.forActiveMods().forSurface(surface)
            val logistics = LogisticsCalculator(parsed.logistics)
            val banned = unplannedRecipes(surface)
            parsed.recipes
                .filter { recipe -> recipe.result.keys.any { it in recipe.ingredients } }
                .filter { recipe -> buildable(logistics, recipe) }
                .map { it.name }
                .filter { it !in banned }
                .forEach { add(it) }
        }
    }
}
