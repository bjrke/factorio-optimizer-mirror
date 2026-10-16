package de.bjrke.factorio.optimizer.jvm

import de.bjrke.factorio.optimizer.common.business.*
import de.bjrke.factorio.optimizer.common.model.*

/** The command line arguments the optimizer understands. */
data class CliArgs(
    val material: String = ALL_SCIENCE_PACKS,
    val spaceAge: Boolean = true,
    val surface: Surface = Surface.NAUVIS,
    val blueprint: Boolean = false,
    val cheats: Boolean = false,
)

/**
 * Parses the CLI arguments: a positional material plus `--base-game` (turns Space Age off),
 * `--planet=<surface>`, `--blueprint` (print a blueprint string instead of the listing) and
 * `--cheats` (add infinity-chest test inputs/outputs to the blueprint).
 */
fun parseArgs(args: Array<String>): CliArgs =
    CliArgs(
        material = args.firstOrNull { !it.startsWith("--") } ?: ALL_SCIENCE_PACKS,
        // Space Age is on by default; --base-game switches to the base-game dump.
        spaceAge = args.none { it == "--base-game" },
        surface =
            args
                .firstOrNull { it.startsWith("--planet=") }
                ?.removePrefix("--planet=")
                ?.let { surfaceFromPlanet(it) } ?: Surface.NAUVIS,
        blueprint = args.any { it == "--blueprint" },
        cheats = args.any { it == "--cheats" },
    )

fun main(args: Array<String>) {
    val cli = parseArgs(args)
    val factories = FactoryConfig(cli.spaceAge).defaults
    val result =
        Optimizer(
            RecipeProvider.forActiveMods(cli.spaceAge).forSurface(cli.surface),
            factories,
        )[Material(cli.material)]

    val logisticsConfig = LogisticsConfig(result.logistics, cli.spaceAge)
    val logistics =
        LogisticsCalculator(
            result.logistics,
            logisticsConfig.defaultBelt,
            logisticsConfig.defaultInserter,
            lanes = 1,
            beltStack = logisticsConfig.beltStack(null, logisticsConfig.defaultInserter),
            inserterStack = 1,
        )

    if (cli.blueprint) {
        when (val outcome = blueprintPlan(result, logistics)) {
            is BlueprintPlanResult.Planned ->
                println(
                    blueprintString(buildBlueprint(outcome.plan, logistics, cli.cheats).toJson())
                )
            is BlueprintPlanResult.Unavailable ->
                println("No blueprint available: ${outcome.reason}")
        }
        return
    }

    fun transport(
        factory: Factory,
        material: Material,
        rate: Double,
        isOutput: Boolean,
    ): String {
        if (logistics.isFluid(material)) {
            return logistics.pumpCount(rate)?.let { "${superRound(it)} pumps + pipe" } ?: "pipe"
        }
        // the big mining drill and the recycler drop their product straight onto the belt
        val directOutput = isOutput && factory.outputsDirectly
        val outputStacks = if (directOutput) factory.stacksOnBelt else logistics.stacksOnBelt()
        val stack = if (isOutput && !outputStacks) 1 else logistics.beltStack
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
