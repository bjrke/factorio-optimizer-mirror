package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculatorSelection
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.testLogistics
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecipeFilterTest {

    private val solid = Material("solid")
    private val otherSolid = Material("other-solid")
    private val fluid = Material("fluid")
    private val logistics =
        LogisticsCalculator(
            testLogistics.copy(fluids = setOf(fluid)),
            LogisticsCalculatorSelection(),
        )

    private fun canBuild(result: Map<Material, Double>, categories: Set<Category>) =
        buildable(logistics, Recipe("test", emptyMap(), result, 1.0, categories))

    @Test
    fun aFluidByproductDoesNotNeedASecondOutputBelt() =
        assertTrue(
            canBuild(mapOf(solid to 1.0, fluid to 1.0), setOf(Category.CRAFTING)),
            "fluid results leave through their own lanes",
        )

    @Test
    fun twoSolidResultsStillNeedAMixedOutputBelt() =
        assertFalse(
            canBuild(mapOf(solid to 1.0, otherSolid to 1.0), setOf(Category.CRAFTING)),
            "a second solid would need a second output belt",
        )

    @Test
    fun anImportPseudoRecipeIsNeverBuilt() =
        assertAll(
            {
                assertFalse(
                    canBuild(mapOf(solid to 1.0), setOf(Category.IMPORT)),
                    "imports enter the blueprint from outside",
                )
            },
            {
                assertFalse(
                    canBuild(mapOf(solid to 1.0), setOf(Category.MINING_BASIC_SOLID)),
                    "extraction recipes are imported too",
                )
            },
        )
}
