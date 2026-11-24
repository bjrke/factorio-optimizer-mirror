package de.bjrke.factorio.optimizer.common.blueprint.planning.plan

import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.model.FactoryProduction

/**
 * The material rates of [machines], keyed by material and split out of the fluids. [machineRate] is
 * the share of a machine's full rate the row actually runs at: a plan shrunk below the requested
 * rate places fewer machines than the optimizer sized, and a band may repeat one production, so the
 * full per-machine rate would size the row's belts for a throughput the blueprint does not have.
 */
internal fun solidRates(
    machines: List<FactoryProduction>,
    rates: (FactoryProduction) -> List<Pair<Material, Double>>,
    machineRate: (FactoryProduction) -> Double = { 1.0 },
): Map<Material, Double> =
    machines
        .flatMap { production ->
            rates(production).map { (material, rate) -> material to rate * machineRate(production) }
        }
        .filterNot { (material, _) -> material.isFluid }
        .groupingBy { (material, _) -> material }
        .fold(0.0) { acc, (_, rate) -> acc + rate }

internal fun rowInputs(
    machines: List<FactoryProduction>,
    machineRate: (FactoryProduction) -> Double = { 1.0 },
) = solidRates(machines, ::inputRates, machineRate)

internal fun rowOutputs(
    machines: List<FactoryProduction>,
    machineRate: (FactoryProduction) -> Double = { 1.0 },
) = solidRates(machines, ::outputRates, machineRate)

/** The solid input/output rates of one row and the belt rows they pack into. */
internal data class RowBeltPacking(
    val inputs: Map<Material, Double>,
    val outputs: Map<Material, Double>,
    val groups: List<BeltGroup>,
)

/**
 * The belt packing of [row]: its external solid inputs and outputs at the plan's rates, each
 * input's feed direction, and the imports. Both the row placer and the plan's final band check use
 * this one function, so the check cannot disagree with the belts the layout builds.
 */
internal fun rowBeltPacking(
    row: PlannedRow,
    beltMaterials: Set<Material>,
    logistics: LogisticsCalculator,
    machineRate: (FactoryProduction) -> Double = { 1.0 },
    feedDirection: (Material) -> Direction? = { null },
): RowBeltPacking {
    val linked = row.links.mapTo(mutableSetOf()) { it.material }
    val inputs = rowInputs(row.machines, machineRate) - row.fullyInternalInputs
    val outputs = rowOutputs(row.machines, machineRate) - linked
    val directions =
        (inputs.keys - outputs.keys).associateWith { feedDirection(it) ?: Direction.EAST }
    val groups =
        packRowBelts(
            BeltPackRequest(
                rates = RowBeltRates(inputs, outputs, logistics.oneLaneCapacity()),
                directions = directions,
                imports = inputs.keys - beltMaterials,
                allowOverCapacityPairs = row.productions.size == 1,
            ),
        )
    return RowBeltPacking(inputs, outputs, groups)
}

internal fun outputRates(production: FactoryProduction) =
    production.recipe.result.entries.map { (material, amount) ->
        material to production.outputPerMachine(amount)
    }

internal fun inputRates(production: FactoryProduction) =
    production.recipe.ingredients.entries.map { (material, amount) ->
        material to production.inputPerMachine(amount)
    }
