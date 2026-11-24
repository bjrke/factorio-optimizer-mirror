package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculatorSelection
import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.indexedForTest
import de.bjrke.factorio.optimizer.common.fixtures.ironOreMiningRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.moltenIronFromLavaRecipe
import de.bjrke.factorio.optimizer.common.fixtures.nuclearFuelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.plasticBarRecipe
import de.bjrke.factorio.optimizer.common.fixtures.testLogistics
import de.bjrke.factorio.optimizer.common.fixtures.uraniumProcessingRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecipeFilterTest : BaseGameCtxTest() {

    private val logistics =
        LogisticsCalculator(
            testLogistics,
            LogisticsCalculatorSelection(),
        )

    @Test
    fun centrifugingAndSimpleChemistryAreBuildable() =
        assertAll(
            {
                assertTrue(
                    buildable(logistics, nuclearFuelRecipe.indexedForTest()),
                    "centrifuging is planned",
                )
            },
            {
                assertTrue(
                    buildable(logistics, plasticBarRecipe.indexedForTest()),
                    "simple chemistry is planned",
                )
            },
        )

    @Test
    fun aFluidByproductDoesNotNeedASecondOutputBelt() =
        assertTrue(
            buildable(logistics, moltenIronFromLavaRecipe.indexedForTest()),
            "fluid results leave through their own lanes",
        )

    @Test
    fun twoSolidResultsStillNeedAMixedOutputBelt() =
        assertFalse(
            buildable(logistics, uraniumProcessingRecipe.indexedForTest()),
            "a second solid would need a second output belt",
        )

    @Test
    fun anImportPseudoRecipeIsNeverBuilt() =
        assertAll(
            {
                assertFalse(
                    buildable(logistics, importPseudoRecipe().indexedForTest()),
                    "imports enter the blueprint from outside",
                )
            },
            {
                assertFalse(
                    buildable(logistics, ironOreMiningRecipe.indexedForTest()),
                    "extraction recipes are imported too",
                )
            },
        )

    /** Imports have no `data.raw` recipe; this stands in for the import pseudo recipe. */
    private fun importPseudoRecipe() =
        Recipe(
            name = Category.IMPORT.identifier,
            ingredients = emptyMap(),
            result = mapOf(ironPlate to 1.0),
            time = 1.0,
            categories = setOf(Category.IMPORT),
        )
}
