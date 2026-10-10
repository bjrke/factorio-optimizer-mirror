package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.factorio.optimizer.common.model.Factories.defaultFactories

class Optimizer(
    private val parsedRecipe: ParsedRecipe = parseRecipes(),
    private val factories: Set<Factory> = defaultFactories,
) {
    private val visited = mutableSetOf<Material>()

    private val cache = mutableMapOf<Material, OptimizationResult>()

    /** Materials that at least one enabled recipe can produce, i.e. not a free "chest" source. */
    private val producibleMaterials: Set<Material> =
        parsedRecipe.recipes
            .filter { findFactory(it) != null }
            .flatMapTo(mutableSetOf()) { it.result.keys }

    /**
     * Materials an enabled extraction recipe (drill/pump/tower) produces directly, i.e. the
     * cheapest inputs. Recipes built from these are preferred over ones needing processed
     * intermediates, e.g. spoilage rots from yumako/jellynut rather than from agricultural science
     * packs.
     */
    private val rawMaterials: Set<Material> =
        parsedRecipe.recipes
            .filter { it.category in extractionCategories && findFactory(it) != null }
            .flatMapTo(mutableSetOf()) { it.result.keys }

    private fun findFactory(recipe: Recipe) = factories.find {
        it.categories.contains(recipe.category)
    }

    private fun uncached(target: Material): OptimizationResult {
        val candidates =
            parsedRecipe.recipes.filter { findFactory(it) != null && it.result.containsKey(target) }

        val recipe =
            candidates.minWithOrNull(
                compareBy(
                    // avoid recipes that would pull an ingredient from a free "chest" source,
                    // e.g. prefer lava -> molten-iron over chest iron-ore -> molten-iron on
                    // Vulcanus
                    { recipe -> recipe.ingredients.keys.count { it !in producibleMaterials } },
                    // then build from raw resources (drill/pump/pumpjack/tower) instead of
                    // processing chains, e.g. spoilage from jellynut rather than science packs
                    { recipe -> recipe.ingredients.keys.count { it !in rawMaterials } },
                    // then prefer the raw source (drill/pump/pumpjack) over processing recipes
                    { recipe -> if (recipe.category in extractionCategories) 0 else 1 },
                    { it.name },
                )
            )
                ?: Recipe(
                    name = target.name,
                    ingredients = mapOf(),
                    result = mapOf(target to 1.0),
                    time = 1.0,
                    subgroup = Subgroup(),
                    category = Category.NO_CATEGORY,
                )

        val factory = findFactory(recipe) ?: Factory.NO_FACTORY

        val factoryProductions = mutableMapOf<Material, FactoryProduction>()

        fun addFactory(
            material: Material,
            recipe: Recipe,
            factory: Factory,
            amount: Double,
            level: Int,
        ) {
            val current =
                factoryProductions[material] ?: FactoryProduction(recipe, factory, 0.0, level)
            factoryProductions[material] = current + amount
        }

        val add = visited.add(target)

        val outputAmount = recipe.result[target]!!
        val outputTime = recipe.time / factory.speed

        if (add) { // cycle protection
            recipe.ingredients.forEach { (material, amount) ->
                val requirement = amount / outputAmount
                val nested = this[material]

                nested.factories.forEach { (nestedMaterial, nestedFactories) ->
                    addFactory(
                        nestedMaterial,
                        nestedFactories.recipe,
                        nestedFactories.factory,
                        requirement * nestedFactories.amount,
                        nestedFactories.level,
                    )
                }
            }
            visited.remove(target)
        }

        addFactory(
            target,
            recipe,
            factory,
            outputTime / outputAmount,
            1 + (factoryProductions.values.maxOfOrNull(FactoryProduction::level) ?: 0),
        )

        return OptimizationResult(
            target = target,
            factories = factoryProductions.toMap(),
            recipe = recipe,
        )
    }

    operator fun get(target: Material) =
        cache.getOrPut(target) {
            uncached(target)
        }
}
