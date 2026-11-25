package de.bjrke.factorio.optimizer.common.model

import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.copperCable
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.game.prototype.Subgroup
import de.bjrke.factorio.optimizer.common.game.prototype.SurfaceCondition
import de.bjrke.factorio.optimizer.common.game.prototype.SurfaceProperty
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class IndexedRecipeTest : BaseGameCtxTest() {

    @Test
    fun aRecipeIndexCannotBeNegative() {
        assertFailsWith<IllegalArgumentException> { RecipeIndex(-1) }
    }

    @Test
    fun anIndexedRecipeCarriesEveryRecipeField() {
        val recipe =
            Recipe(
                name = "copy-check",
                ingredients = mapOf(gear to 2.0, copperCable to 3.0),
                result = mapOf(ironPlate to 1.0),
                time = 1.5,
                categories = setOf(Category.CRAFTING, Category.ELECTROMAGNETICS),
                subgroup = Subgroup("copy-check"),
                surfaceConditions = listOf(SurfaceCondition(SurfaceProperty.GRAVITY, 10..20)),
            )

        val indexed = IndexedRecipe.of(RecipeIndex(0), recipe)

        assertAll(
            { assertEquals(recipe.name, indexed.name) },
            { assertEquals(recipe.ingredients, indexed.ingredients) },
            { assertEquals(recipe.result, indexed.result) },
            { assertEquals(recipe.time, indexed.time) },
            { assertEquals(recipe.categories, indexed.categories) },
            { assertEquals(recipe.subgroup, indexed.subgroup) },
            { assertEquals(recipe.surfaceConditions, indexed.surfaceConditions) },
        )
    }

    @Test
    fun identityIsTheIndexNotTheFields() {
        val first = IndexedRecipe.of(RecipeIndex(7), ironPlateRecipe)
        val second = IndexedRecipe.of(RecipeIndex(7), ironGearWheelRecipe)

        assertAll(
            { assertEquals(first, second) },
            { assertEquals(first.hashCode(), second.hashCode()) },
            { assertNotEquals(first, IndexedRecipe.of(RecipeIndex(8), ironPlateRecipe)) },
        )
    }

    @Test
    fun anIndexedRecipeFindsTheMapEntryOfTheSameSlot() {
        val key = IndexedRecipe.of(RecipeIndex(3), ironPlateRecipe)
        val lookup = IndexedRecipe.of(RecipeIndex(3), ironGearWheelRecipe)
        val values = mapOf(key to "found")

        assertEquals("found", values[lookup])
    }
}
