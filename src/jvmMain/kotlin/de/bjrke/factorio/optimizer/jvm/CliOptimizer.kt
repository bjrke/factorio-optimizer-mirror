package de.bjrke.factorio.optimizer.jvm

import de.bjrke.factorio.optimizer.common.business.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.business.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.business.Optimizer
import de.bjrke.factorio.optimizer.common.business.RecipeProvider
import de.bjrke.factorio.optimizer.common.business.SPACE_AGE_MOD
import de.bjrke.factorio.optimizer.common.business.defaultMods
import de.bjrke.factorio.optimizer.common.business.superRound
import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.FactoryConfig
import de.bjrke.factorio.optimizer.common.model.LogisticsConfig
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

    val logisticsConfig = LogisticsConfig(result.logistics, spaceAge)
    val logistics =
        LogisticsCalculator(
            result.logistics,
            logisticsConfig.defaultBelt,
            logisticsConfig.defaultInserter,
            lanes = 1,
            beltStack = 1,
            inserterStack = 1,
        )

    fun transport(
        factory: Factory,
        material: Material,
        rate: Double,
        isOutput: Boolean,
    ): String {
        if (logistics.isFluid(material)) {
            return logistics.pumpCount(rate)?.let { "${superRound(it)} pumps + pipe" } ?: "pipe"
        }
        // mines and the recycler drop their product straight onto the belt
        val directOutput = isOutput && factory.outputsDirectly
        val stack =
            if (isOutput && !directOutput && !logistics.stacksOnBelt()) 1 else logistics.beltStack
        val belts = "${superRound(logistics.beltCount(rate, stack))} belts"
        return if (directOutput) belts
        else "$belts + ${superRound(logistics.inserterCount(rate, ontoBelt = isOutput))} inserters"
    }

    result.factories.values.sortedWith(factoryProductionComparator).forEach { production ->
        println(
            "${superRound(production.amount)} x ${production.recipe.name} in ${production.factory.name}"
        )
        production.recipe.ingredients.keys.forEach { material ->
            val rate = production.consumption(material)
            println(
                "  in  ${material.name}: ${superRound(rate)} -> " +
                    transport(production.factory, material, rate, isOutput = false)
            )
        }
        production.recipe.result.keys.forEach { material ->
            val rate = production.production(material)
            println(
                "  out ${material.name}: ${superRound(rate)} -> " +
                    transport(production.factory, material, rate, isOutput = true)
            )
        }
    }
}
