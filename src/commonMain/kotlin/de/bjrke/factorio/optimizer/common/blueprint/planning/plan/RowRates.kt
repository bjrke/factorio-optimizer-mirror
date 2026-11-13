package de.bjrke.factorio.optimizer.common.blueprint.planning.plan

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.model.FactoryProduction

/** The material rates of [machines], keyed by material and split out of the fluids. */
internal fun solidRates(
    machines: List<FactoryProduction>,
    rates: (FactoryProduction) -> List<Pair<Material, Double>>,
    logistics: LogisticsCalculator,
): Map<Material, Double> =
    machines
        .flatMap(rates)
        .filterNot { (material, _) -> logistics.isFluid(material) }
        .groupingBy { (material, _) -> material }
        .fold(0.0) { acc, (_, rate) -> acc + rate }

internal fun rowInputs(machines: List<FactoryProduction>, logistics: LogisticsCalculator) =
    solidRates(machines, ::inputRates, logistics)

internal fun rowOutputs(machines: List<FactoryProduction>, logistics: LogisticsCalculator) =
    solidRates(machines, ::outputRates, logistics)

internal fun outputRates(production: FactoryProduction) =
    production.recipe.result.entries.map { (material, amount) ->
        material to production.outputPerMachine(amount)
    }

internal fun inputRates(production: FactoryProduction) =
    production.recipe.ingredients.entries.map { (material, amount) ->
        material to production.inputPerMachine(amount)
    }
