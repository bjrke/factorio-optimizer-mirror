package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.factorio.optimizer.common.model.FactoryConfig

private const val EPSILON = 1e-7
private const val SLACK = 1e-6
private const val IMPORT_DISPLAY_EPSILON = 1e-4

/**
 * Works out how many machines of each recipe are needed to produce one unit per second of a target.
 *
 * Instead of picking a single "cheapest" recipe per material, the production network is modelled as
 * a linear program: every recipe is a variable, every material a balance constraint (`production +
 * imports >= consumption + demand`) and the objective minimizes, in order, (1) imported materials,
 * (2) total machines. This lets several recipes serve the same material in parallel and credits the
 * byproducts of multi-output recipes, which is how e.g. Fulgora actually works: scrap recycling
 * yields gears, batteries and processing units, and all of them are recycled into the demanded
 * intermediates.
 */
class Optimizer(
    private val parsedRecipe: ParsedRecipe = RecipeProvider.forActiveMods(),
    private val factories: Set<Factory> = FactoryConfig().defaults,
) {
    private val cache = mutableMapOf<Material, OptimizationResult>()

    /** Recipes that run on this surface with one of the selected machines. */
    private val enabledRecipes: List<Recipe> =
        parsedRecipe.recipes.filter { findFactory(it) != null }

    /**
     * Materials a recycling recipe yields. Crafting them would model production that does not
     * happen: on Fulgora they are recovered by recycling, so a forward recipe for e.g. iron-plate
     * or copper-cable is not allowed. Empty on surfaces without a recycler.
     */
    private val recyclingSupplied: Set<Material> =
        enabledRecipes
            .filter { Category.RECYCLING in it.categories }
            .flatMapTo(mutableSetOf()) { it.result.keys }

    private val allowedRecipes: List<Recipe> = enabledRecipes.filter { recipe ->
        Category.RECYCLING in recipe.categories ||
            recipe.result.keys.none { it in recyclingSupplied }
    }

    /**
     * The machine's categories, from the dump when present, otherwise the curated [Factory]
     * fallback.
     */
    private fun categoriesOf(factory: Factory) =
        parsedRecipe.machineStats[factory.identifier]?.categories ?: factory.categories

    private fun speedOfFactory(factory: Factory) =
        parsedRecipe.machineStats[factory.identifier]?.speed ?: factory.speed

    private fun productivityOfFactory(factory: Factory) =
        parsedRecipe.machineStats[factory.identifier]?.productivity ?: factory.productivity

    /**
     * The machine that runs [recipe]: of all selected machines that accept any of the recipe's
     * categories, prefer the Space Age speciality machines, then the one with the highest
     * productivity, then speed. The identifier keeps the choice deterministic.
     */
    private fun findFactory(recipe: Recipe) =
        factories
            .filter { factory -> categoriesOf(factory).any { it in recipe.categories } }
            .maxWithOrNull(
                compareBy(
                    { it.preferred },
                    { productivityOfFactory(it) },
                    { speedOfFactory(it) },
                    { it.identifier },
                )
            )

    private fun speedOf(recipe: Recipe) = findFactory(recipe)?.let(::speedOfFactory) ?: 1.0

    private fun productivityOf(recipe: Recipe) =
        findFactory(recipe)?.let(::productivityOfFactory) ?: 0.0

    /** Units of [amount] consumed per second by one machine running [recipe]. */
    private fun inputRate(amount: Double, recipe: Recipe) = speedOf(recipe) * amount / recipe.time

    /**
     * Units of [amount] produced per second by one machine running [recipe]; the machine's built-in
     * productivity bonus increases the output without increasing the inputs.
     */
    private fun outputRate(amount: Double, recipe: Recipe) =
        speedOf(recipe) * (1.0 + productivityOf(recipe)) * amount / recipe.time

    /**
     * Every material reachable from [target] by following recipes through to their inputs. Only
     * [allowedRecipes] are considered, so forbidden forward recipes do not pull in their inputs.
     */
    private fun reachableMaterials(target: Material): List<Material> {
        val materials = mutableSetOf(target)
        var changed = true
        while (changed) {
            changed = false
            for (recipe in allowedRecipes) {
                if (recipe.result.keys.none { it in materials }) continue
                for (ingredient in recipe.ingredients.keys) {
                    if (materials.add(ingredient)) changed = true
                }
            }
        }
        return materials.toList()
    }

    /**
     * The materials that may be imported: those no recipe produces, plus any material caught in a
     * production cycle (e.g. Fulgora's `coal-synthesis` <-> `carbon` loop) which would otherwise
     * make the program infeasible. Materials downstream of a cycle stay producible once a cycle
     * member may be imported.
     */
    private fun importableMaterials(
        materials: List<Material>,
        recipes: List<Recipe>,
    ): List<Material> {
        val dependency = materials.associateWithTo(mutableMapOf()) { mutableSetOf<Material>() }
        for (recipe in recipes) {
            for (result in recipe.result.keys) {
                dependency.getOrPut(result) { mutableSetOf() }.addAll(recipe.ingredients.keys)
            }
        }
        val inCycle =
            materials.filterTo(mutableSetOf()) { start ->
                val seen = mutableSetOf<Material>()
                val stack = ArrayDeque(dependency.getValue(start))
                while (stack.isNotEmpty()) {
                    val material = stack.removeLast()
                    if (!seen.add(material)) continue
                    stack.addAll(dependency[material].orEmpty())
                }
                start in seen
            }
        return materials.filter { material ->
            recipes.none { material in it.result.keys } || material in inCycle
        }
    }

    /**
     * Computes the machine counts for [target]. The material balance is a linear program solved in
     * two lexicographic stages so the result is the true optimum and not just any vertex: first
     * minimize imports, then total machines (which also removes the zero-cost cycling recipes the
     * simplex would otherwise leave in the basis).
     */
    private fun compute(target: Material): OptimizationResult {
        val materials = reachableMaterials(target)
        val recipes = allowedRecipes.filter {
            it.result.keys.any { material -> material in materials }
        }

        // An import is only allowed for materials no recipe produces (and for recipe cycles that
        // have no base input): a producible material must be built, because importing a finished
        // composite is always "cheaper" in raw units and would otherwise import the whole target.
        val importMaterials = importableMaterials(materials, recipes)
        val solution =
            solve(target, materials, recipes, importMaterials)
                ?: error("no feasible production plan for ${target.name}")

        return OptimizationResult(
            target = target,
            recipe =
                recipes
                    .filter { target in it.result.keys }
                    .maxByOrNull { solution[recipes.indexOf(it)] } ?: freeSource(target),
            factories = factoryProductions(target, materials, recipes, importMaterials, solution),
            logistics = parsedRecipe.logistics,
        )
    }

    /**
     * Solves the material balance as a linear program minimizing, in order, imported materials and
     * total machines. Returns `null` if the program is infeasible.
     *
     * Each objective is a separate program: the next stage constrains the previous one to its
     * optimum (plus [SLACK]) and minimizes the next. This lexicographic optimum removes the
     * zero-cost cycling recipes the simplex would otherwise leave in the basis, and is numerically
     * more robust than weighting the objectives together.
     */
    private fun solve(
        target: Material,
        materials: List<Material>,
        recipes: List<Recipe>,
        importMaterials: List<Material>,
    ): DoubleArray? {
        val recipeVariables = recipes.size
        val variables = recipeVariables + importMaterials.size

        val demandRows =
            Array(materials.size) { row ->
                val material = materials[row]
                DoubleArray(variables) { column ->
                    if (column < recipeVariables) {
                        val recipe = recipes[column]
                        val production =
                            recipe.result[material]?.let { outputRate(it, recipe) } ?: 0.0
                        val consumption =
                            recipe.ingredients[material]?.let { inputRate(it, recipe) } ?: 0.0
                        consumption - production
                    } else if (importMaterials[column - recipeVariables] == material) {
                        -1.0
                    } else {
                        0.0
                    }
                }
            }
        val demandBounds =
            DoubleArray(materials.size) { if (materials[it] == target) -1.0 else 0.0 }

        val importRow = DoubleArray(variables) { if (it >= recipeVariables) 1.0 else 0.0 }
        val machineRow = DoubleArray(variables) { if (it < recipeVariables) 1.0 else 0.0 }

        fun stage(
            extraRows: List<DoubleArray>,
            extraBounds: List<Double>,
            cost: DoubleArray,
        ): LinearProgramResult.Optimal? {
            val result =
                LinearProgram(
                        (demandRows.toList() + extraRows).toTypedArray(),
                        (demandBounds.toList() + extraBounds).toDoubleArray(),
                        cost,
                    )
                    .minimize()
            return result as? LinearProgramResult.Optimal
        }

        val imports = stage(emptyList(), emptyList(), importRow) ?: return null
        val cheapest =
            stage(listOf(importRow), listOf(imports.value + SLACK), machineRow) ?: return null
        return cheapest.solution
    }

    /** Builds the per-recipe machine counts from the LP solution, one entry per running recipe. */
    private fun factoryProductions(
        target: Material,
        materials: List<Material>,
        recipes: List<Recipe>,
        importMaterials: List<Material>,
        solution: DoubleArray,
    ): Map<Recipe, FactoryProduction> {
        val recipeVariables = recipes.size

        // total consumption per material in the LP solution, used to label multi-output recipes
        val consumption = mutableMapOf<Material, Double>()
        recipes.forEachIndexed { index, recipe ->
            val amount = solution[index]
            if (amount <= EPSILON) return@forEachIndexed
            recipe.ingredients.forEach { (material, quantity) ->
                consumption[material] =
                    (consumption[material] ?: 0.0) + inputRate(quantity, recipe) * amount
            }
        }

        fun representativeOf(recipe: Recipe): Material {
            val outputs = recipe.result.keys.filter { it in materials }
            if (target in outputs) return target
            return outputs.maxWithOrNull(compareBy({ consumption[it] ?: 0.0 }, { it.name }))
                ?: recipe.result.keys.first()
        }

        // depth of a material in the (byproduct-aware) production graph, used for display ordering
        val effectiveProducer = mutableMapOf<Material, Recipe>()
        recipes.forEachIndexed { index, recipe ->
            if (solution[index] <= EPSILON) return@forEachIndexed
            recipe.result.keys.forEach { result -> effectiveProducer.getOrPut(result) { recipe } }
        }
        val depths = mutableMapOf<Material, Int>()
        fun depth(material: Material, path: MutableSet<Material>): Int {
            depths[material]?.let {
                return it
            }
            if (!path.add(material)) return 0
            val producer = effectiveProducer[material]
            val value =
                if (producer == null) 1
                else 1 + (producer.ingredients.keys.maxOfOrNull { depth(it, path) } ?: 0)
            path.remove(material)
            depths[material] = value
            return value
        }

        val productions = mutableMapOf<Recipe, FactoryProduction>()
        recipes.forEachIndexed { index, recipe ->
            val amount = solution[index]
            if (amount <= EPSILON) return@forEachIndexed
            val representative = representativeOf(recipe)
            productions[recipe] =
                FactoryProduction(
                    material = representative,
                    recipe = recipe,
                    factory = findFactory(recipe) ?: Factory.NO_FACTORY,
                    amount = amount,
                    level = depth(representative, mutableSetOf()),
                    speed = speedOf(recipe),
                    productivity = productivityOf(recipe),
                )
        }
        importMaterials.forEachIndexed { index, material ->
            val imported = solution[recipeVariables + index]
            // the import optimization allows a small slack; only show a meaningful import
            if (imported <= IMPORT_DISPLAY_EPSILON) return@forEachIndexed
            val recipe = freeSource(material)
            productions[recipe] =
                FactoryProduction(
                    material = material,
                    recipe = recipe,
                    factory = Factory.NO_FACTORY,
                    amount = imported,
                    level = depth(material, mutableSetOf()),
                )
        }
        return productions
    }

    /** A pseudo recipe representing an imported material (the old "chest" source). */
    private fun freeSource(material: Material) =
        Recipe(
            name = material.name,
            ingredients = mapOf(),
            result = mapOf(material to 1.0),
            time = 1.0,
            subgroup = Subgroup(),
            categories = setOf(Category.NO_CATEGORY),
        )

    operator fun get(target: Material) = cache.getOrPut(target) { compute(target) }
}
