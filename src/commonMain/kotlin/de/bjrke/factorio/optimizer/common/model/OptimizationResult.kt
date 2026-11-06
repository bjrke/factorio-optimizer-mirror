package de.bjrke.factorio.optimizer.common.model

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Logistics
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import kotlin.math.max

data class FactoryProduction(
    // the demanded material this recipe entry is labeled with; a recipe producing several needed
    // materials (e.g. scrap recycling) is labeled with the one it mostly supplies
    val material: Material,
    val recipe: Recipe,
    val factory: Factory,
    val amount: Double = 0.0,
    val level: Int,
    // the stats the optimizer actually sized the machine with, derived from the prototype dump
    // when available; the curated [Factory] values are only the fallback
    val speed: Double = factory.speed,
    val productivity: Double = factory.productivity,
) {

    // crafts per second at the machine's speed, before productivity
    private val rate by lazy {
        val time = recipe.time
        if (time <= 0.0) 0.0 else speed / time
    }

    fun production(material: Material) = amount * outputPerMachine(material)

    fun consumption(material: Material) = amount * inputPerMachine(material)

    /**
     * Items of [material] one machine of this production makes per second. The productivity bonus
     * counts: that is what a direct-fed consumer actually receives, so it belongs in the ratio.
     */
    internal fun outputPerMachine(material: Material = this.material) =
        outputPerMachine(recipe.result[material] ?: 0.0)

    /** Items per second one machine makes from [items] per craft, productivity included. */
    internal fun outputPerMachine(items: Double) = items * rate * (1.0 + productivity)

    /** Items per second one machine takes of [items] per craft. */
    internal fun inputPerMachine(items: Double) = items * rate

    /** Items of [material] one machine of this production takes per second. */
    internal fun inputPerMachine(material: Material) =
        inputPerMachine(recipe.ingredients[material] ?: 0.0)

    override fun toString() = recipe.name
}

data class OptimizationResult(
    val target: Material,
    val recipe: Recipe,
    // keyed by recipe: several recipes can produce the same material and run in parallel
    val factories: Map<Recipe, FactoryProduction>,
    // the logistics prototypes the calculator uses to turn rates into belts/inserters/pumps
    val logistics: Logistics = Logistics(),
)

val factoryProductionComparator =
    compareBy<FactoryProduction>(
        { max(it.recipe.ingredients.size, it.recipe.result.size) },
        { it.level },
        { it.factory },
        { it.recipe.name },
    )
