package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.extractionCategories
import de.bjrke.test.assertTableOf
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
            parsePrototypes(dump)
                .recipes
                .filter {
                    it.categories.none(extractionCategories::contains) &&
                        Category.SPOILING !in it.categories
                }
                .map { it.name }
                .toSet(),
        )

    @Test
    fun activeModsKeepBaseAlways() =
        assertTableOf(
            emptySet<String>() to setOf(BASE_MOD),
            setOf(SPACE_AGE_MOD) to setOf(BASE_MOD, SPACE_AGE_MOD),
        ) { (mods, expected) ->
            assertEquals(expected, activeMods(mods))
        }
}
