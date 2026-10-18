package de.bjrke.factorio.optimizer.jvm

import de.bjrke.factorio.optimizer.common.business.*
import de.bjrke.factorio.optimizer.common.model.*

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

/**
 * Parses the CLI arguments. A positional material, `--base-game` (turns Space Age off),
 * `--planet=<surface>`, `--factory=<machine>` (repeatable), `--belt=<tier>`, `--inserter=<tier>`,
 * `--full-belt`, `--belt-stack=<n>`, `--inserter-stack=<n>`, `--normalize=<x>`,
 * `--belt-end=<marker>` and its alias `--cheats` (the infinity chest) are understood; `--settings`,
 * `--listing`, `--layout`, `--json` and `--blueprint` choose the [Output]s.
 * Belt/inserter/machine/marker values are identifiers (`express-transport-belt`, `foundry`,
 * `buffer-chest`) or a single-letter code. Any argument may be replaced by a pasted web UI URL,
 * e.g. `http://localhost:8080/#/4.5/advanced-circuit/CFSXbmr/1/1`, whose segments fill in the
 * material, selection, normalize target and stack sizes. Explicit flags override it.
 */
fun parseArgs(args: Array<String>): CliArgs {
    val url = args.firstNotNullOfOrNull { parseFactorioUrl(it) }
    // a positional argument that is not the URL overrides its target
    val positional = args.firstOrNull { !it.startsWith("--") && parseFactorioUrl(it) == null }
    val spaceAge =
        when {
            args.any { it == "--base-game" } -> false
            url != null -> SelectionFlag.SPACE_AGE.enabledIn(url.selection)
            else -> true
        }
    // base game always runs on Nauvis, mirroring the web UI
    val surface =
        if (!spaceAge) Surface.NAUVIS
        else
            args
                .firstOrNull { it.startsWith("--planet=") }
                ?.removePrefix("--planet=")
                ?.let { surfaceFromPlanet(it) } ?: surfaceFromUrl(url?.selection) ?: Surface.NAUVIS
    val normalizeTarget =
        args
            .firstOrNull { it.startsWith("--normalize=") }
            ?.removePrefix("--normalize=")
            ?.let { validNormalizeTarget(it.toDoubleOrNull()) }
            ?: url?.normalizeTarget
            ?: DEFAULT_NORMALIZE_TARGET
    val outputs = buildSet {
        if (args.any { it == "--settings" }) add(Output.SETTINGS)
        if (args.any { it == "--listing" }) add(Output.LISTING)
        if (args.any { it == "--layout" }) add(Output.LAYOUT)
        if (args.any { it == "--json" }) add(Output.JSON)
        if (args.any { it == "--blueprint" }) add(Output.BLUEPRINT)
    }
    // a chest marker needs ground, so a chest choice on a space platform falls back to the label
    val beltEnd =
        (args
                .firstOrNull { it.startsWith("--belt-end=") }
                ?.removePrefix("--belt-end=")
                ?.let { BeltEnd.fromIdentifier(it) }
                ?: if (args.any { it == "--cheats" }) BeltEnd.INFINITY_CHEST
                else BeltEnd.fromSelection(url?.selection, surface))
            .takeIf { it.allowedOn(surface) } ?: BeltEnd.CONSTANT_COMBINATOR
    return CliArgs(
        material = positional ?: url?.target ?: ALL_SCIENCE_PACKS,
        spaceAge = spaceAge,
        surface = surface,
        beltEnd = beltEnd,
        normalizeTarget = normalizeTarget,
        selection = url?.selection,
        factories =
            args
                .filter { it.startsWith("--factory=") }
                .mapNotNull { machine(it.removePrefix("--factory=")) },
        belt = beltTier(args.firstOrNull { it.startsWith("--belt=") }?.removePrefix("--belt=")),
        inserter =
            inserterTier(
                args.firstOrNull { it.startsWith("--inserter=") }?.removePrefix("--inserter="),
            ),
        fullBelt = if (args.any { it == "--full-belt" }) true else null,
        beltStack =
            args.firstOrNull { it.startsWith("--belt-stack=") }?.removePrefix("--belt-stack=")
                ?: url?.beltStack,
        inserterStack =
            args
                .firstOrNull { it.startsWith("--inserter-stack=") }
                ?.removePrefix("--inserter-stack=") ?: url?.inserterStack,
        outputs = outputs,
    )
}

/** Resolves `--belt=` by prototype identifier or single-letter code. */
private fun beltTier(value: String?) = value?.let {
    BeltTier.entries.firstOrNull { entry -> entry.identifier == it } ?: BeltTier.fromSelection(it)
}

/** Resolves `--inserter=` by prototype identifier or single-letter code. */
private fun inserterTier(value: String?) = value?.let {
    InserterTier.entries.firstOrNull { entry -> entry.identifier == it }
        ?: InserterTier.fromSelection(it)
}

/** Resolves `--factory=` by prototype identifier or single-letter code. */
private fun machine(value: String?) = value?.let {
    Factory.entries.firstOrNull { entry -> entry.identifier == it }
        ?: Factory.entries.firstOrNull { entry -> entry.code?.toString() == it }
}

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
    if (Output.SETTINGS in outputs)
        sections += {
            println(settingsText(cli, factories, belt, inserter, lanes, beltStack, inserterStack))
        }
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
    belt: BeltTier,
    inserter: InserterTier,
    lanes: Int,
    beltStack: Int,
    inserterStack: Int,
) =
    """
    settings
      material: ${cli.material}
      spaceAge: ${cli.spaceAge}
      surface: ${cli.surface.identifier}
      beltEnd: ${cli.beltEnd.identifier}
      normalizeTarget: ${cli.normalizeTarget}
      belt: ${belt.identifier}
      inserter: ${inserter.identifier}
      beltLanes: $lanes
      beltStack: $beltStack
      inserterStack: $inserterStack
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
        val outputStacks = if (directOutput) factory.stacksOnBelt else logistics.stacksOnBelt()
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
