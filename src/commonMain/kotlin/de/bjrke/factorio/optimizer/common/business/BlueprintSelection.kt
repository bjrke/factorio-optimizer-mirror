package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Recipe
import de.bjrke.factorio.optimizer.common.model.extractionCategories

/**
 * Complex or cyclic processes that are usually done elsewhere and therefore left out of a
 * blueprint; their products become imports. Only kept when the target itself comes from one of
 * them. Oil refining and cracking (and their bio/cryo Space Age shortcuts) pull in long fluid
 * chains, so e.g. heavy oil and petroleum gas derivatives like plastic are imported. Simple
 * chemical-plant recipes (lubricant, sulfur, ...) are *not* excluded; only their fluid inputs are.
 */
private val externallyProcessedCategories =
    setOf(
        Category.OIL_PROCESSING,
        Category.CENTRIFUGING,
        Category.ORGANIC,
        Category.CRYOGENICS,
        Category.RECYCLING,
        Category.CRUSHING,
        Category.ASTEROID_COLLECTION,
    )

/** Classic smelters are left out (their plates are imported); the foundry is kept. */
private val classicSmelters =
    setOf(Factory.STONE_FURNACE, Factory.STEEL_FURNACE, Factory.ELECTRIC_FURNACE)

/**
 * The factory a plan starts from: the one producing the target [recipe]. Null means none does. The
 * target is always buildable as such, so this is not narrowed by [buildable].
 */
internal fun planTarget(
    factories: Collection<FactoryProduction>,
    recipe: Recipe,
): FactoryProduction? = factories.firstOrNull { it.recipe.name == recipe.name }

/**
 * The recipes a plan builds, in the order the target's inputs are followed: the target first, then
 * each input that has exactly one buildable producer, depth first, inputs by material name. A
 * recipe is never built twice, and the walk stops once [maxRecipes] recipes are included.
 */
internal fun includedRecipes(
    factories: Collection<FactoryProduction>,
    target: FactoryProduction,
    maxRecipes: Int,
): List<FactoryProduction> {
    val singleProducer = singleProducers(factories, target.recipe)

    val included = mutableListOf<FactoryProduction>()
    fun include(factory: FactoryProduction) {
        if (included.any { it.recipe === factory.recipe } || included.size >= maxRecipes) return
        included += factory
        // build every input that has a single producer, until the recipe cap is reached
        factory.recipe.ingredients.keys
            .filter { it in singleProducer }
            .sortedBy { it.name }
            .forEach { include(singleProducer.getValue(it)) }
    }
    include(target)
    return included
}

/**
 * Whether a factory can stand in the blueprint. The target's producer is always kept, even when it
 * would otherwise be left out; every other place-dependent recipe (mining/pumping/harvesting),
 * classic smelter and complex process is skipped.
 */
private fun buildable(factory: FactoryProduction, target: Recipe): Boolean {
    if (factory.factory == Factory.NO_FACTORY) return false
    if (factory.recipe.name == target.name) return true
    if (factory.recipe.categories.any { it in extractionCategories }) return false
    if (factory.factory in classicSmelters) return false
    if (factory.recipe.categories.any { it in externallyProcessedCategories }) return false
    // a recipe yielding several materials would need a mixed output belt, not supported yet
    if (factory.recipe.result.size > 1) return false
    return true
}

/**
 * The one buildable producer of each material among [factories]. A material with exactly one
 * producer can be built and fed on, even to several consumers: the layout shares one belt from the
 * producer when more than one recipe takes it.
 */
private fun singleProducers(
    factories: Collection<FactoryProduction>,
    target: Recipe,
): Map<Material, FactoryProduction> =
    factories
        .filter { buildable(it, target) }
        .flatMap { factory -> factory.recipe.result.keys.map { it to factory } }
        .groupBy({ it.first }, { it.second })
        .mapNotNull { (material, producers) -> producers.singleOrNull()?.let { material to it } }
        .toMap()

/**
 * The external inputs the [included] recipes consume but none of them produces, with the rate the
 * imports have to supply at [scale]: one lane per material, by name. Whether a material moves as a
 * fluid is asked from the [logistics] the plan was optimized with.
 */
internal fun plannedSources(
    included: List<FactoryProduction>,
    scale: Double,
    logistics: LogisticsCalculator,
): List<PlannedSource> {
    val producers = materialsToFactories(included) { recipe.result.keys }
    val consumers = materialsToFactories(included) { recipe.ingredients.keys }
    return consumers
        .filterKeys { it !in producers }
        .map { (material, factories) ->
            PlannedSource(
                material = material,
                rate = factories.sumOf { it.consumption(material) } * scale,
                fluid = logistics.isFluid(material),
            )
        }
        .sortedBy { it.material.name }
}

/**
 * Every material the [included] recipes make (or take, as [materials] selects), with the recipes
 * that make (take) it. In recipe order, and first-seen material order within a recipe.
 */
private fun materialsToFactories(
    included: List<FactoryProduction>,
    materials: FactoryProduction.() -> Set<Material>,
): Map<Material, List<FactoryProduction>> =
    buildMap<Material, MutableList<FactoryProduction>> {
        for (factory in included) {
            factory.materials().forEach { getOrPut(it) { mutableListOf() }.add(factory) }
        }
    }
