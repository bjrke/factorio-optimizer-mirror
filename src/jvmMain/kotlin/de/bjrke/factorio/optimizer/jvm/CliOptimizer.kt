package de.bjrke.factorio.optimizer.jvm

import de.bjrke.factorio.optimizer.common.blueprint.blueprintLayoutDump
import de.bjrke.factorio.optimizer.common.blueprint.blueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.buildBlueprint
import de.bjrke.factorio.optimizer.common.blueprint.inspection.layoutAscii
import de.bjrke.factorio.optimizer.common.blueprint.output.blueprintString
import de.bjrke.factorio.optimizer.common.data.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.data.DEFAULT_NORMALIZE_TARGET
import de.bjrke.factorio.optimizer.common.data.superRound
import de.bjrke.factorio.optimizer.common.model.BeltEnd
import de.bjrke.factorio.optimizer.common.model.BeltTier
import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.FactoryConfig
import de.bjrke.factorio.optimizer.common.model.InserterTier
import de.bjrke.factorio.optimizer.common.model.LogisticsConfig
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.factorio.optimizer.common.model.Surface
import de.bjrke.factorio.optimizer.common.model.factoryProductionComparator
import de.bjrke.factorio.optimizer.common.model.forSurface
import de.bjrke.factorio.optimizer.common.model.surfaceFromUrl
import de.bjrke.factorio.optimizer.common.model.toJson
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlanResult
import de.bjrke.factorio.optimizer.common.service.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.service.Optimizer
import de.bjrke.factorio.optimizer.common.service.RecipeProvider

/**
 * Which artifact(s) the CLI prints. Several may be requested at once; [order] is the order they are
 * printed in (resolved settings first, the large paste-able string last), so one run can show the
 * settings, the listing, the text layout, the raw JSON and the blueprint string together.
 */
enum class Output(val blueprint: Boolean = false) {
    SETTINGS,
    LISTING,
    LAYOUT(blueprint = true),
    JSON(blueprint = true),
    BLUEPRINT(blueprint = true);

    companion object {
        val order = listOf(SETTINGS, LISTING, LAYOUT, JSON, BLUEPRINT)
    }
}

/** The command line arguments the optimizer understands. */
data class CliArgs(
    val material: String = ALL_SCIENCE_PACKS,
    val spaceAge: Boolean = true,
    val surface: Surface = Surface.NAUVIS,
    /** How a solid belt ends; `--belt-end`/`--cheats` or the URL, clamped to [surface]. */
    val beltEnd: BeltEnd = BeltEnd.CONSTANT_COMBINATOR,
    val normalizeTarget: Double = DEFAULT_NORMALIZE_TARGET,
    /**
     * The `selection` URL segment, decoded by [FactoryConfig]/[LogisticsConfig]/[surfaceFromUrl].
     * Explicit flags and [factories]/[belt]/[inserter]/[fullBelt] override it.
     */
    val selection: String? = null,
    /** Machines to switch on in addition to the URL/default selection, e.g. `--factory=foundry`. */
    val factories: List<Factory> = emptyList(),
    val belt: BeltTier? = null,
    val inserter: InserterTier? = null,
    /** `--full-belt` (both belt lanes); null keeps the URL/default choice. */
    val fullBelt: Boolean? = null,
    val beltStack: String? = null,
    val inserterStack: String? = null,
    /** The requested [Output]s; empty means the default listing. */
    val outputs: Set<Output> = emptySet(),
)

fun main(args: Array<String>) {
    val cli = parseArgs(args)
    val parsedRecipes = RecipeProvider.forActiveMods(cli.spaceAge).forSurface(cli.surface)
    val factoryConfig = FactoryConfig(cli.spaceAge)
    val factories =
        cli.factories
            .filter { it in factoryConfig.available }
            .fold(factoryConfig.fromUrl(cli.selection)) { chosen, factory ->
                factoryConfig.add(chosen, factory)
            }

    val logisticsConfig = LogisticsConfig(parsedRecipes.logistics, cli.spaceAge)
    val inserter =
        cli.inserter?.takeIf { it in logisticsConfig.availableInserters }
            ?: logisticsConfig.inserter(cli.selection)
    val belt =
        cli.belt?.takeIf { it in logisticsConfig.availableBelts }
            ?: logisticsConfig.belt(cli.selection)
    val lanes =
        if (cli.fullBelt == true) BeltTier.MAX_LANES else logisticsConfig.beltLanes(cli.selection)
    val beltStack = logisticsConfig.beltStack(cli.beltStack, inserter)
    val inserterStack = logisticsConfig.inserterStack(cli.inserterStack, inserter)
    val logistics =
        LogisticsCalculator(
            parsedRecipes.logistics,
            belt,
            inserter,
            lanes,
            beltStack,
            inserterStack,
        )

    val result = Optimizer(parsedRecipes, factories)[Material(cli.material)]

    val outputs = cli.outputs.ifEmpty { setOf(Output.LISTING) }
    val blueprintOutputs = Output.order.filter { it.blueprint && it in outputs }
    val outcome =
        if (blueprintOutputs.isEmpty()) null
        else blueprintPlan(result, logistics, cli.normalizeTarget)

    val sections = mutableListOf<() -> Unit>()
    if (Output.SETTINGS in outputs) sections += { println(settingsText(cli, factories, logistics)) }
    if (Output.LISTING in outputs) sections += { printListing(result, logistics) }
    when (outcome) {
        null -> {}
        is BlueprintPlanResult.Unavailable -> sections += {
                println("No blueprint available: ${outcome.reason}")
            }
        is BlueprintPlanResult.Planned ->
            for (output in blueprintOutputs) {
                sections += { emitBlueprint(output, outcome.plan, logistics, cli.beltEnd) }
            }
    }
    sections.forEach { it() }
}

/** The resolved configuration, so a pasted URL (or the defaults) can be read back at a glance. */
internal fun settingsText(
    cli: CliArgs,
    factories: Set<Factory>,
    logistics: LogisticsCalculator,
) =
    """
    settings
      material: ${cli.material}
      spaceAge: ${cli.spaceAge}
      surface: ${cli.surface.identifier}
      beltEnd: ${cli.beltEnd.identifier}
      normalizeTarget: ${cli.normalizeTarget}
      belt: ${logistics.belt.identifier}
      inserter: ${logistics.inserter.identifier}
      beltLanes: ${logistics.lanes}
      beltStack: ${logistics.beltStack}
      inserterStack: ${logistics.inserterStack}
      factories: ${factories.map { it.identifier }.sorted().joinToString(", ")}
    """
        .trimIndent()

/** Prints the requested blueprint [output]; the non-blueprint outputs never reach here. */
private fun emitBlueprint(
    output: Output,
    plan: BlueprintPlan,
    logistics: LogisticsCalculator,
    beltEnd: BeltEnd,
) =
    when (output) {
        Output.LAYOUT -> println(layoutAscii(blueprintLayoutDump(plan, logistics, beltEnd)))
        Output.JSON -> println(buildBlueprint(plan, logistics, beltEnd).toJson())
        Output.BLUEPRINT ->
            println(blueprintString(buildBlueprint(plan, logistics, beltEnd).toJson()))
        Output.SETTINGS,
        Output.LISTING -> Unit
    }

/** Prints the per-recipe production listing: rates, machines and how each material is moved. */
private fun printListing(result: OptimizationResult, logistics: LogisticsCalculator) {
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
        val outputStacks = if (directOutput) factory.stacksOnBelt else logistics.rates.stacksOnBelt
        val stack = if (isOutput && !outputStacks) 1 else logistics.beltStack
        val belts = "${superRound(logistics.beltCount(rate, stack))} belts"
        return if (directOutput) belts
        else "$belts + ${superRound(logistics.inserterCount(rate, ontoBelt = isOutput))} inserters"
    }

    result.factories.values.sortedWith(factoryProductionComparator).forEach { production ->
        println(
            "${superRound(production.amount)} x ${production.recipe.name} in ${production.factory.name}",
        )
        production.recipe.ingredients.keys.forEach { material ->
            val rate = production.consumption(material)
            println(
                "  in  ${material.name}: ${superRound(rate)} -> " +
                    transport(production.factory, material, rate, isOutput = false),
            )
        }
        production.recipe.result.keys.forEach { material ->
            val rate = production.production(material)
            println(
                "  out ${material.name}: ${superRound(rate)} -> " +
                    transport(production.factory, material, rate, isOutput = true),
            )
        }
    }
}
