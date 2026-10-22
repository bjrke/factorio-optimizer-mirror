package de.bjrke.factorio.optimizer.jvm

import de.bjrke.factorio.optimizer.common.blueprint.FactorioUrl
import de.bjrke.factorio.optimizer.common.blueprint.parseFactorioUrl
import de.bjrke.factorio.optimizer.common.data.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.data.DEFAULT_NORMALIZE_TARGET
import de.bjrke.factorio.optimizer.common.data.validNormalizeTarget
import de.bjrke.factorio.optimizer.common.model.BeltEnd
import de.bjrke.factorio.optimizer.common.model.BeltTier
import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.InserterTier
import de.bjrke.factorio.optimizer.common.model.SelectionFlag
import de.bjrke.factorio.optimizer.common.model.Surface
import de.bjrke.factorio.optimizer.common.model.surfaceFromPlanet
import de.bjrke.factorio.optimizer.common.model.surfaceFromUrl

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
    val spaceAge = resolveSpaceAge(args, url)
    val surface = resolveSurface(args, spaceAge, url)
    return CliArgs(
        material = positional ?: url?.target ?: ALL_SCIENCE_PACKS,
        spaceAge = spaceAge,
        surface = surface,
        beltEnd = resolveBeltEnd(args, url, surface),
        normalizeTarget = resolveNormalizeTarget(args, url),
        selection = url?.selection,
        factories = args.valueOptions("factory").mapNotNull { machine(it) },
        belt = beltTier(args.valueOption("belt")),
        inserter = inserterTier(args.valueOption("inserter")),
        fullBelt = if (args.any { it == "--full-belt" }) true else null,
        beltStack = args.valueOption("belt-stack") ?: url?.beltStack,
        inserterStack = args.valueOption("inserter-stack") ?: url?.inserterStack,
        outputs = resolveOutputs(args),
    )
}

/** The first value of a singular `--name=<value>` option; later repeats are ignored. */
private fun Array<String>.valueOption(name: String): String? = firstOrNull {
    it.startsWith("--$name=")
}
    ?.removePrefix("--$name=")

/** Every value of a repeatable `--name=<value>` option, in command-line order. */
private fun Array<String>.valueOptions(name: String): List<String> = filter {
    it.startsWith("--$name=")
}
    .map { it.removePrefix("--$name=") }

private fun resolveSpaceAge(args: Array<String>, url: FactorioUrl?) =
    when {
        args.any { it == "--base-game" } -> false
        url != null -> SelectionFlag.SPACE_AGE.enabledIn(url.selection)
        else -> true
    }

/** Base game always runs on Nauvis, mirroring the web UI. */
private fun resolveSurface(args: Array<String>, spaceAge: Boolean, url: FactorioUrl?) =
    if (!spaceAge) Surface.NAUVIS
    else
        surfaceFromPlanet(args.valueOption("planet"))
            ?: surfaceFromUrl(url?.selection)
            ?: Surface.NAUVIS

private fun resolveNormalizeTarget(args: Array<String>, url: FactorioUrl?) =
    args.valueOption("normalize")?.let { validNormalizeTarget(it.toDoubleOrNull()) }
        ?: url?.normalizeTarget
        ?: DEFAULT_NORMALIZE_TARGET

private fun resolveOutputs(args: Array<String>) = buildSet {
    if (args.any { it == "--settings" }) add(Output.SETTINGS)
    if (args.any { it == "--listing" }) add(Output.LISTING)
    if (args.any { it == "--layout" }) add(Output.LAYOUT)
    if (args.any { it == "--json" }) add(Output.JSON)
    if (args.any { it == "--blueprint" }) add(Output.BLUEPRINT)
}

/** A chest marker needs ground, so a chest choice on a space platform falls back to the label. */
private fun resolveBeltEnd(args: Array<String>, url: FactorioUrl?, surface: Surface): BeltEnd {
    val requested =
        args.valueOption("belt-end")?.let(BeltEnd::fromIdentifier)
            ?: if (args.any { it == "--cheats" }) BeltEnd.INFINITY_CHEST
            else BeltEnd.fromSelection(url?.selection, surface)
    return requested.takeIf { it.allowedOn(surface) } ?: BeltEnd.CONSTANT_COMBINATOR
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
