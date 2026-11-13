package de.bjrke.factorio.optimizer.common.optimize

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.DEFAULT_INTERMEDIATE_LIMIT
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.FactoryConfig
import de.bjrke.factorio.optimizer.common.game.prototype.MAX_INTERMEDIATE_LIMIT
import de.bjrke.factorio.optimizer.common.game.prototype.MIN_INTERMEDIATE_LIMIT
import de.bjrke.factorio.optimizer.common.game.prototype.ParsedRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.factorio.optimizer.common.game.prototype.Subgroup
import de.bjrke.factorio.optimizer.common.game.prototype.unplannedRecipes
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.factorio.optimizer.common.model.OptimizationResult

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
 *
 * [maxIntermediates] bounds how many produced materials besides the target the plan may build. The
 * least-used materials are kept, so a hub such as an ore or a plate becomes an import first; the
 * target is always built and never counts.
 *
 * [excludedMaterials] are imported instead of built: every recipe that produces one is dropped
 * before the LP, so the plan neither calculates nor lays out its production. The target is never
 * excluded, and a recipe that also produces the target keeps running, so an excluded material can
 * still appear as its byproduct.
 */
class Optimizer(
    private val parsedRecipe: ParsedRecipe,
    factories: Set<Factory> = FactoryConfig().defaults,
    maxIntermediates: Int = DEFAULT_INTERMEDIATE_LIMIT,
    private val excludedMaterials: Set<Material> = emptySet(),
) {
    private val cache = mutableMapOf<Material, OptimizationResult>()
    private val machine = MachineCatalog(parsedRecipe, factories)

    /**
     * How many produced materials besides the target the plan may build. The least-used materials
     * are kept, so a hub such as an ore or a plate becomes an import first; the target is always
     * built and never counts.
     */
    private val maxIntermediates =
        maxIntermediates.coerceIn(MIN_INTERMEDIATE_LIMIT, MAX_INTERMEDIATE_LIMIT)

    /**
     * One solid-fuel recipe per surface: light oil elsewhere, heavy oil on Fulgora and ammonia on
     * Aquilo. Planning the alternatives lets the LP split solid fuel across producers, which leaves
     * the blueprint with more than one belt source for the same material. The cycle recipes are
     * excluded here too, so they are never selected, not even for their own target.
     */
    private val unplannedRecipes = unplannedRecipes(parsedRecipe.surface)

    /** Recipes that run on this surface with one of the selected machines. */
    private val enabledRecipes: List<Recipe> =
        parsedRecipe.recipes.filter {
            it.name !in unplannedRecipes && machine.findFactory(it) != null
        }

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
     * Every material reachable from [target] by following [recipes] through to their inputs. Only
     * the given recipes are considered, so forbidden forward recipes do not pull in their inputs.
     */
    private fun reachableMaterials(target: Material, recipes: List<Recipe>): List<Material> {
        val materials = mutableSetOf(target)
        var changed = true
        while (changed) {
            changed = false
            for (recipe in recipes) {
                if (recipe.result.keys.none { it in materials }) continue
                for (ingredient in recipe.ingredients.keys) {
                    if (materials.add(ingredient)) changed = true
                }
            }
        }
        return materials.toList()
    }

    /**
     * Drops the recipes of the most-used produced materials until at most [maxIntermediates]
     * remain. The target is always kept. A material used by many recipes is a hub (ores, plates)
     * and becomes an import first; the least-used materials near the target are built.
     */
    private fun limitProducedMaterials(
        target: Material,
        materials: List<Material>,
        recipes: List<Recipe>,
    ): List<Recipe> {
        val produced =
            materials.filterTo(mutableSetOf()) { material ->
                material != target && recipes.any { material in it.result.keys }
            }
        if (produced.size <= maxIntermediates) return recipes
        val kept =
            parsedRecipe.usage.order
                .asSequence()
                .filter { it in produced }
                .take(maxIntermediates)
                .toSet()
        return recipes.filter { recipe -> recipe.result.keys.any { it == target || it in kept } }
    }

    /**
     * The materials that may be imported: those no recipe produces, plus any material caught in a
     * production cycle (a filled/empty barrel pair, for example) which would otherwise make the
     * program infeasible. Materials downstream of a cycle stay producible once a cycle member may
     * be imported. The target is the exception: when a recipe produces it, it must be built, or the
     * LP would import the whole plan and leave no target factory.
     */
    private fun importableMaterials(
        target: Material,
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
            val produced = recipes.any { material in it.result.keys }
            // the target must be built when a recipe produces it: importing it would make the
            // whole plan empty, and a cycle that contains the target must be broken by producing
            // it. A target no recipe produces is still a free source.
            if (material == target) !produced else !produced || material in inCycle
        }
    }

    /**
     * Computes the machine counts for [target]. The material balance is a linear program solved in
     * two lexicographic stages so the result is the true optimum and not just any vertex: first
     * minimize imports, then total machines (which also removes the zero-cost cycling recipes the
     * simplex would otherwise leave in the basis).
     */
    private fun compute(target: Material): OptimizationResult {
        // an excluded material is imported: drop its producers, but keep a recipe that produces the
        // target even when it also produces the excluded material, so the target is always built
        val excluded = excludedMaterials - target
        val allowed =
            if (excluded.isEmpty()) allowedRecipes
            else
                allowedRecipes.filter { recipe ->
                    target in recipe.result.keys || recipe.result.keys.none { it in excluded }
                }
        val reachable = reachableMaterials(target, allowed)
        val recipes = allowed.filter {
            it.result.keys.any { material -> material in reachable }
        }
        val limitedRecipes = limitProducedMaterials(target, reachable, recipes)
        val materials = reachableMaterials(target, limitedRecipes)
        val finalRecipes = limitedRecipes.filter {
            it.result.keys.any { material -> material in materials }
        }

        // An import is only allowed for materials no recipe produces (and for recipe cycles that
        // have no base input): a producible material must be built, because importing a finished
        // composite is always "cheaper" in raw units and would otherwise import the whole target.
        val importMaterials = importableMaterials(target, materials, finalRecipes)
        val solution =
            solve(target, materials, finalRecipes, importMaterials)
                ?: error("no feasible production plan for ${target.name}")

        return OptimizationResult(
            target = target,
            recipe =
                finalRecipes
                    .filter { target in it.result.keys }
                    .maxByOrNull { solution[finalRecipes.indexOf(it)] } ?: freeSource(target),
            factories =
                factoryProductions(target, materials, finalRecipes, importMaterials, solution),
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
                            recipe.result[material]?.let { machine.outputRate(it, recipe) } ?: 0.0
                        val consumption =
                            recipe.ingredients[material]?.let { machine.inputRate(it, recipe) }
                                ?: 0.0
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
        return stage(listOf(importRow), listOf(imports.value + SLACK), machineRow)?.solution
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
        val consumption = consumptionOf(recipes, solution)
        val effectiveProducer = effectiveProducersOf(recipes, solution)
        val depth = DepthIndex(effectiveProducer)
        val productions = mutableMapOf<Recipe, FactoryProduction>()

        recipes.forEachIndexed { index, recipe ->
            val amount = solution[index]
            if (amount <= EPSILON) return@forEachIndexed
            val material = representativeOf(recipe, materials, target, consumption)
            productions[recipe] =
                productionOf(
                    material,
                    recipe,
                    machine.findFactory(recipe) ?: Factory.NO_FACTORY,
                    amount,
                    depth,
                )
        }
        importMaterials.forEachIndexed { index, material ->
            val imported = solution[recipeVariables + index]
            // the import optimization allows a small slack; only show a meaningful import
            if (imported <= IMPORT_DISPLAY_EPSILON) return@forEachIndexed
            val recipe = freeSource(material)
            productions[recipe] =
                productionOf(material, recipe, Factory.NO_FACTORY, imported, depth)
        }
        return productions
    }

    /** Total consumption per material in the LP solution, used to label multi-output recipes. */
    private fun consumptionOf(recipes: List<Recipe>, solution: DoubleArray) = buildMap {
        recipes.forEachIndexed { index, recipe ->
            val amount = solution[index]
            if (amount <= EPSILON) return@forEachIndexed
            recipe.ingredients.forEach { (material, quantity) ->
                this[material] =
                    (this[material] ?: 0.0) + machine.inputRate(quantity, recipe) * amount
            }
        }
    }

    /** The producer that actually runs for each material, used for the display depth. */
    private fun effectiveProducersOf(
        recipes: List<Recipe>,
        solution: DoubleArray,
    ): Map<Material, Recipe> = buildMap {
        recipes.forEachIndexed { index, recipe ->
            if (solution[index] <= EPSILON) return@forEachIndexed
            recipe.result.keys.forEach { result -> getOrPut(result) { recipe } }
        }
    }

    /**
     * The material a multi-output recipe is labelled with: the target, else its most-consumed
     * output.
     */
    private fun representativeOf(
        recipe: Recipe,
        materials: List<Material>,
        target: Material,
        consumption: Map<Material, Double>,
    ): Material =
        recipe.result.keys
            .filter { it in materials }
            .let { outputs ->
                if (target in outputs) target
                else
                    outputs.maxWithOrNull(compareBy({ consumption[it] ?: 0.0 }, { it.name }))
                        ?: recipe.result.keys.first()
            }

    private fun productionOf(
        material: Material,
        recipe: Recipe,
        factory: Factory,
        amount: Double,
        depth: DepthIndex,
    ) =
        FactoryProduction(
            material = material,
            recipe = recipe,
            factory = factory,
            amount = amount,
            level = depth.of(material),
            speed = machine.speedOf(recipe),
            productivity = machine.productivityOf(recipe),
        )

    operator fun get(target: Material) = cache.getOrPut(target) { compute(target) }
}

/**
 * A pseudo recipe representing an imported material (the old "chest" source). Its [Category.IMPORT]
 * category keeps it out of the blueprint's buildable rows, so the blueprint imports the material
 * instead of trying to place a machine for it.
 */
private fun freeSource(material: Material) =
    Recipe(
        name = material.name,
        ingredients = mapOf(),
        result = mapOf(material to 1.0),
        time = 1.0,
        subgroup = Subgroup(),
        categories = setOf(Category.IMPORT),
    )

/** Memoized display depth in the byproduct-aware production graph, safe on cycles. */
private class DepthIndex(private val producer: Map<Material, Recipe>) {
    private val depths = mutableMapOf<Material, Int>()
    private val path = mutableSetOf<Material>()

    fun of(material: Material): Int = depths[material] ?: enter(material)

    private fun enter(material: Material): Int {
        if (!path.add(material)) return 0
        val value =
            producer[material]?.let { recipe ->
                1 + (recipe.ingredients.keys.maxOfOrNull(::of) ?: 0)
            } ?: 1
        path.remove(material)
        depths[material] = value
        return value
    }
}
