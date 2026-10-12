package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.extractionCategories
import kotlin.test.Test
import kotlin.test.assertEquals

class RecipeProviderTest {

    private val dump =
        """
        {"recipe":{
          "copper-plate":{
            "type":"recipe","name":"copper-plate","categories":["smelting"],
            "energy_required":3.2,
            "ingredients":[{"type":"item","name":"copper-ore","amount":1}],
            "results":[{"type":"item","name":"copper-plate","amount":1}]},
          "tungsten-plate":{
            "type":"recipe","name":"tungsten-plate","categories":["smelting"],
            "energy_required":2,
            "ingredients":[{"type":"item","name":"tungsten-ore","amount":1}],
            "results":[{"type":"item","name":"tungsten-plate","amount":1}]}
        }}
        """

    @Test
    fun parseDumpRecipes() =
        assertEquals(
            setOf("copper-plate", "tungsten-plate"),
            parsePrototypes(dump, defaultRecipesProvider = { listOf() })
                .recipes
                .filter { it.category !in extractionCategories && it.category != Category.ROTTING }
                .map { it.name }
                .toSet(),
        )

    @Test
    fun activeModsKeepBaseAlways() {
        assertEquals(setOf(BASE_MOD), activeMods(emptySet()))
        assertEquals(setOf(BASE_MOD, SPACE_AGE_MOD), activeMods(setOf(SPACE_AGE_MOD)))
    }
}
