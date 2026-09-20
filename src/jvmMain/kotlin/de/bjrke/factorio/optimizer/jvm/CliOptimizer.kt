package de.bjrke.factorio.optimizer.jvm

import de.bjrke.factorio.optimizer.common.business.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.business.Optimizer
import de.bjrke.factorio.optimizer.common.business.superRound
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.factoryProductionComparator

fun main(args: Array<String>) {
    val material = Material.of(if (args.isEmpty()) ALL_SCIENCE_PACKS else args[0])
    val result = Optimizer()[material]

    result.factories.entries
        .sortedWith(factoryProductionComparator)
        .map { it.value }
        .forEach { println("${superRound(it.amount)} x ${it.recipe.name} in ${it.factory.name}") }
}
