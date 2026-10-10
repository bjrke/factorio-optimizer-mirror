package de.bjrke.factorio.optimizer.common.business

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RecipeProviderTest {

    private val baseRecipe =
        """
        {{
        type = "recipe",
        name = "copper-plate",
        category = "smelting",
        energy_required = 3.2,
        ingredients = {{ "copper-ore", 1}},
        result = "copper-plate"
        }}
        """

    private val spaceAgeRecipe =
        """
        {{
        type = "recipe",
        name = "tungsten-plate",
        category = "smelting",
        energy_required = 2,
        ingredients = {{ "tungsten-ore", 1}},
        result = "tungsten-plate"
        }}
        """

    @Test
    fun parseModsTxt() {
        val tab = "\t"
        assertEquals(
            listOf(
                ModInfo("base", emptyList(), listOf("base/prototypes/recipe.lua")),
                ModInfo(
                    "space-age",
                    listOf("recycler", "quality"),
                    listOf("space-age/prototypes/recipe.lua"),
                ),
            ),
            parseModsTxt(
                """
                base${tab}${tab}base/prototypes/recipe.lua
                space-age${tab}recycler,quality${tab}space-age/prototypes/recipe.lua
                """
                    .trimIndent()
            ),
        )
    }

    @Test
    fun parseMultipleMods() =
        assertEquals(
            2,
            parseRecipes(
                    files =
                        mapOf(
                            "base/prototypes/recipe.lua" to baseRecipe,
                            "space-age/prototypes/recipe.lua" to spaceAgeRecipe,
                        ),
                    activeMods = setOf("base", "space-age"),
                    defaultRecipesProvider = { listOf() },
                )
                .recipes
                .size,
        )

    @Test
    fun activeModsFiltersRecipes() =
        assertTrue(
            parseRecipes(
                    files =
                        mapOf(
                            "base/prototypes/recipe.lua" to baseRecipe,
                            "space-age/prototypes/recipe.lua" to spaceAgeRecipe,
                        ),
                    activeMods = setOf("base"),
                    defaultRecipesProvider = { listOf() },
                )
                .recipes
                .all { it.name == "copper-plate" }
        )

    @Test
    fun resolveModsAddsDependencies() =
        assertEquals(
            setOf("base", "recycler", "space-age"),
            resolveMods(
                mods =
                    listOf(
                        ModInfo("base", emptyList(), emptyList()),
                        ModInfo("recycler", listOf("base"), emptyList()),
                        ModInfo("space-age", listOf("recycler"), emptyList()),
                    ),
                selected = setOf("space-age"),
            ),
        )

    @Test
    fun parseModIsCached() {
        val first = RecipeProvider.parseMod("base")
        val second = RecipeProvider.parseMod("base")
        assertSame(first, second)
    }
}
