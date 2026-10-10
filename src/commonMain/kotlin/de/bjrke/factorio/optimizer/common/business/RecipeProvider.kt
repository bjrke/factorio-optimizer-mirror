package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.ParsedRecipe
import de.bjrke.factorio.optimizer.common.model.Recipe

fun resolveMods(mods: List<ModInfo>, selected: Set<String>) = buildSet {
    val known = mods.map(ModInfo::name).toSet()

    fun addMod(mod: String) {
        if (mod in known && add(mod))
            mods.firstOrNull { it.name == mod }?.dependencies?.forEach(::addMod)
    }

    selected.forEach(::addMod)
}

object RecipeProvider {

    private val files: Map<String, String> by lazy { loadRecipeFiles() }
    private val mods: List<ModInfo> by lazy { loadMods() }

    private val cache = mutableMapOf<String, ParsedRecipe>()

    fun availableMods() = mods.map(ModInfo::name)

    fun parseMod(mod: String) =
        cache.getOrPut(mod) {
            parseRecipes(
                files = files.filterKeys { it.substringBefore('/') == mod },
                activeMods = setOf(mod),
                defaultRecipesProvider = { listOf() },
            )
        }

    fun resolveMods(selected: Set<String>) = resolveMods(mods, selected)

    fun forActiveMods(selected: Set<String>): ParsedRecipe {
        val activeMods = resolveMods(selected).sorted()
        val materials = mutableSetOf<Material>()
        val recipes = mutableListOf<Recipe>()

        for (mod in activeMods) {
            val parsed = parseMod(mod)
            materials += parsed.materials
            recipes += parsed.recipes
        }

        recipes += defaultRecipes(Material::of)
        recipes.forEach { recipe ->
            materials += recipe.ingredients.keys
            materials += recipe.result.keys
        }

        return ParsedRecipe(
            materials = materials.sortedBy(Material::name),
            recipes = recipes.sortedBy(Recipe::name),
        )
    }
}
