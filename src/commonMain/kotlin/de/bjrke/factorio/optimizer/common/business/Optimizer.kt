package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.factorio.optimizer.common.model.defaultFactories

class Optimizer(
    private val parsedRecipe: ParsedRecipe = parseRecipes(),
    private val factories: Set<Factory> = defaultFactories,
) {
    private val cache = mutableMapOf<Material, OptimizationResult>()

    private val selectedRecipes = mutableMapOf<Material, Recipe>()

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

    private fun speedOf(recipe: Recipe) = findFactory(recipe)?.speed ?: 1.0

    /** Units of [amount] of an item per second, for one machine running [recipe]. */
    private fun rate(amount: Double, recipe: Recipe) = speedOf(recipe) * amount / recipe.time

    /**
     * The cheapest recipe producing [target], or a free "chest" source when nothing can make it.
     * The ranking only depends on the recipe and the enabled machines, never on amounts, so the
     * choice can be cached across targets.
     */
    private fun selectRecipe(target: Material): Recipe =
        selectedRecipes.getOrPut(target) {
            parsedRecipe.recipes
                .filter { findFactory(it) != null && it.result.containsKey(target) }
                .minWithOrNull(
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
        }

    /**
     * Computes the machine counts for [target] by solving the material flow over the selected
     * recipes. Keying by recipe (not material) means a recipe that produces several needed outputs,
     * e.g. advanced metallic asteroid crushing yielding iron and copper ore, runs once sized for
     * the output that is needed most instead of appearing once per output.
     */
    private fun compute(target: Material): OptimizationResult {
        val rootRecipe = selectRecipe(target)

        // Selected producer per reachable material.
        val producers = mutableMapOf(target to rootRecipe)
        fun producerOf(material: Material) = producers.getOrPut(material) { selectRecipe(material) }

        // Ingredient edges per recipe, with back-edges dropped to break cycles (a recipe that
        // transitively consumes its own output does not feed itself).
        val ingredients = mutableMapOf<Recipe, List<Material>>()
        val path = mutableSetOf<Recipe>()
        val done = mutableSetOf<Recipe>()
        fun visit(recipe: Recipe) {
            if (recipe in done) return
            path.add(recipe)
            val kept = recipe.ingredients.keys.filter { producerOf(it) !in path }
            ingredients[recipe] = kept
            kept.forEach { visit(producerOf(it)) }
            path.remove(recipe)
            done.add(recipe)
        }
        visit(rootRecipe)

        // Materials each recipe is the selected producer of.
        val outputs = producers.entries.groupBy({ it.value }, { it.key })

        // Fixed-point: demand(m) is the rate m has to be supplied, and each recipe sizes itself for
        // its most demanded output. The graph is acyclic, so this increases monotonically until it
        // settles.
        val machines = ingredients.keys.associateWithTo(mutableMapOf()) { 0.0 }
        val demand = mutableMapOf<Material, Double>()
        var changed: Boolean
        var iterations = 0
        do {
            demand.clear()
            demand[target] = 1.0
            ingredients.forEach { (recipe, kept) ->
                val running = machines.getValue(recipe)
                kept.forEach { ingredient ->
                    demand[ingredient] =
                        (demand[ingredient] ?: 0.0) +
                            running * rate(recipe.ingredients.getValue(ingredient), recipe)
                }
            }
            changed = false
            ingredients.keys.forEach { recipe ->
                val wanted =
                    outputs[recipe]?.maxOfOrNull {
                        (demand[it] ?: 0.0) / rate(recipe.result.getValue(it), recipe)
                    } ?: 0.0
                if (wanted != machines.getValue(recipe)) {
                    machines[recipe] = wanted
                    changed = true
                }
            }
        } while (changed && ++iterations <= ingredients.size + 2)

        // Display level mirrors the old dependency-tree height: leaves are 1, the target is
        // deepest.
        val levels = mutableMapOf<Material, Int>()
        fun levelOf(material: Material): Int =
            levels.getOrPut(material) {
                1 + (ingredients[producerOf(material)]?.maxOfOrNull(::levelOf) ?: 0)
            }

        val factoryProductions = mutableMapOf<Material, FactoryProduction>()
        ingredients.keys.forEach { recipe ->
            val running = machines.getValue(recipe)
            if (running <= 0.0) return@forEach
            val materials = outputs.getValue(recipe)
            val representative =
                materials.firstOrNull { it == target }
                    ?: materials.maxByOrNull { demand[it] ?: 0.0 }!!
            factoryProductions[representative] =
                FactoryProduction(
                    recipe = recipe,
                    factory = findFactory(recipe) ?: Factory.NO_FACTORY,
                    amount = running,
                    level = levelOf(representative),
                )
        }

        return OptimizationResult(
            target = target,
            factories = factoryProductions,
            recipe = rootRecipe,
        )
    }

    operator fun get(target: Material) =
        cache.getOrPut(target) {
            compute(target)
        }
}
