package de.bjrke.factorio.optimizer.common.model

data class OptimizationResult(
    val targetProduction: FactoryProduction,
    // one production per minted recipe slot: several recipes can produce the same material and run
    // in parallel, and each keeps the index the optimizer addressed it with.
    val factories: FullProductionMap<FactoryProduction>,
) {
    val target = targetProduction.material
}
