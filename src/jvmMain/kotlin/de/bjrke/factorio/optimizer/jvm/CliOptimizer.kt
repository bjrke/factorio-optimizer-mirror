package de.bjrke.factorio.optimizer.jvm

import de.bjrke.factorio.optimizer.common.business.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.business.Optimizer
import de.bjrke.factorio.optimizer.common.business.RecipeProvider
import de.bjrke.factorio.optimizer.common.business.SPACE_AGE_MOD
import de.bjrke.factorio.optimizer.common.business.defaultMods
import de.bjrke.factorio.optimizer.common.business.superRound
import de.bjrke.factorio.optimizer.common.model.FactoryConfig
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Surface
import de.bjrke.factorio.optimizer.common.model.factoryProductionComparator
import de.bjrke.factorio.optimizer.common.model.forSurface
import de.bjrke.factorio.optimizer.common.model.surfaceFromUrl

fun main(args: Array<String>) {
    val material = Material.of(args.firstOrNull { !it.startsWith("--") } ?: ALL_SCIENCE_PACKS)
    val mods =
        args
            .firstOrNull { it.startsWith("--mods=") }
            ?.removePrefix("--mods=")
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet() ?: defaultMods
    val surface =
        args
            .firstOrNull { it.startsWith("--planet=") }
            ?.removePrefix("--planet=")
            ?.let { surfaceFromUrl(it) } ?: Surface.NAUVIS
    val activeMods = RecipeProvider.resolveMods(mods)
    val spaceAge = SPACE_AGE_MOD in activeMods
    val factories = FactoryConfig(spaceAge).defaults
    val result =
        Optimizer(RecipeProvider.forActiveMods(mods).forSurface(surface), factories)[material]

    result.factories.entries
        .sortedWith(factoryProductionComparator)
        .map { it.value }
        .forEach { println("${superRound(it.amount)} x ${it.recipe.name} in ${it.factory.name}") }
}
