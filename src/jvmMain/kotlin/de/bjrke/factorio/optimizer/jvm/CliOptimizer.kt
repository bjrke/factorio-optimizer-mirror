package de.bjrke.factorio.optimizer.jvm

import de.bjrke.factorio.optimizer.common.business.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.business.Optimizer
import de.bjrke.factorio.optimizer.common.business.RecipeProvider
import de.bjrke.factorio.optimizer.common.business.superRound
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.factoryProductionComparator

private const val DEFAULT_MODS = "base"

fun main(args: Array<String>) {
    val material = Material.of(args.firstOrNull { !it.startsWith("--") } ?: ALL_SCIENCE_PACKS)
    val mods =
        args
            .firstOrNull { it.startsWith("--mods=") }
            ?.removePrefix("--mods=")
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet() ?: setOf(DEFAULT_MODS)
    val result = Optimizer(RecipeProvider.forActiveMods(mods))[material]

    result.factories.entries
        .sortedWith(factoryProductionComparator)
        .map { it.value }
        .forEach { println("${superRound(it.amount)} x ${it.recipe.name} in ${it.factory.name}") }
}
