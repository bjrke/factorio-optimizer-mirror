package de.bjrke.factorio.optimizer.jvm

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.printing.output.FactorioUrl
import de.bjrke.factorio.optimizer.common.blueprint.printing.output.parseFactorioUrl
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.production.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.game.production.DEFAULT_NORMALIZE_TARGET
import de.bjrke.factorio.optimizer.common.game.production.validNormalizeTarget
import de.bjrke.factorio.optimizer.common.game.prototype.BeltTier
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.InserterTier
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.factorio.optimizer.common.game.prototype.validIntermediateLimit
import de.bjrke.factorio.optimizer.common.game.selection.beltEndFromIdentifier
import de.bjrke.factorio.optimizer.common.game.selection.beltEndFromSelection
import de.bjrke.factorio.optimizer.common.game.selection.excludedMaterialsFromUrl
import de.bjrke.factorio.optimizer.common.game.selection.spaceAgeIn
import de.bjrke.factorio.optimizer.common.game.selection.surfaceFromPlanet
import de.bjrke.factorio.optimizer.common.game.selection.surfaceFromUrl

/**
 * Parses the CLI arguments. A positional material, `--base-game` (turns Space Age off),
 * `--planet=<surface>`, `--factory=<machine>` (repeatable), `--belt=<tier>`, `--inserter=<tier>`,
 * `--full-belt`, `--belt-stack=<n>`, `--inserter-stack=<n>`, `--intermediates=<n>`,
 * `--exclude=<material>` (repeatable), `--normalize=<x>`, `--belt-end=<marker>` and its alias
 * `--cheats` (the infinity chest) are understood; `--settings`, `--listing`, `--layout`, `--json`
 * and `--blueprint` choose the [Output]s. An explicit belt/inserter/planet/machine/marker value
 * must be an exact identifier (`express-transport-belt`, `foundry`, `buffer-chest`) or exactly one
 * code character; anything else fails with [CliUsageException]. Any argument may be replaced by a
 * pasted web UI URL, e.g. `http://localhost:8080/#/4.5/advanced-circuit/CFSXbmr/1/1/10`, whose
 * segments fill in the material, selection, normalize target, stack sizes, intermediate limit and
 * excluded materials. Explicit flags override it.
 */
fun parseArgs(args: Array<String>): CliArgs {
    val url = args.firstNotNullOfOrNull { parseFactorioUrl(it) }
    // a positional argument that is not the URL overrides its target
    val positional = args.firstOrNull { !it.startsWith("--") && parseFactorioUrl(it) == null }
    val spaceAge = resolveSpaceAge(args, url)
    val surface = resolveSurface(args, spaceAge, url)
    // explicit --exclude flags replace the URL's list, so a pasted URL can be narrowed
    val excluded = args.valueOptions("exclude").filter { it.isNotEmpty() }
    return CliArgs(
        material = positional ?: url?.target ?: ALL_SCIENCE_PACKS,
        spaceAge = spaceAge,
        surface = surface,
        beltEnd = resolveBeltEnd(args, url, surface),
        normalizeTarget = resolveNormalizeTarget(args, url),
        selection = url?.selection,
        factories = args.valueOptions("factory").map { machine(it) },
        belt = beltTier(args.valueOption("belt")),
        inserter = inserterTier(args.valueOption("inserter")),
        fullBelt = if (args.any { it == "--full-belt" }) true else null,
        beltStack = args.valueOption("belt-stack") ?: url?.beltStack,
        inserterStack = args.valueOption("inserter-stack") ?: url?.inserterStack,
        intermediates =
            validIntermediateLimit(
                args.valueOption("intermediates") ?: url?.intermediates,
            ),
        excludedMaterials =
            if (excluded.isEmpty()) excludedMaterialsFromUrl(url?.excluded)
            else excluded.mapTo(mutableSetOf()) { Material(it) },
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
    if (args.any { it == "--base-game" }) false else spaceAgeIn(url?.selection)

/** Base game always runs on Nauvis, mirroring the web UI. */
private fun resolveSurface(args: Array<String>, spaceAge: Boolean, url: FactorioUrl?): Surface {
    val requested =
        args.valueOption("planet")?.let { value ->
            surfaceFromPlanet(value) ?: unknownOption("--planet", value)
        }
    return when {
        !spaceAge -> Surface.NAUVIS
        requested != null -> requested
        else -> surfaceFromUrl(url?.selection) ?: Surface.NAUVIS
    }
}

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
        args.valueOption("belt-end")?.let { value ->
            beltEndFromIdentifier(value) ?: unknownOption("--belt-end", value)
        }
            ?: if (args.any { it == "--cheats" }) BeltEnd.INFINITY_CHEST
            else beltEndFromSelection(url?.selection, surface)
    return requested.takeIf { it.allowedOn(surface) } ?: BeltEnd.CONSTANT_COMBINATOR
}

/** Resolves `--belt=` strictly by prototype identifier or exactly one code character. */
private fun beltTier(value: String?) = value?.let {
    BeltTier.entries.firstOrNull { entry ->
        entry.identifier == it || (it.length == 1 && entry.code == it[0])
    } ?: unknownOption("--belt", it)
}

/** Resolves `--inserter=` strictly by prototype identifier or exactly one code character. */
private fun inserterTier(value: String?) = value?.let {
    InserterTier.entries.firstOrNull { entry ->
        entry.identifier == it || (it.length == 1 && entry.code == it[0])
    } ?: unknownOption("--inserter", it)
}

/** Resolves `--factory=` strictly by prototype identifier or exactly one code character. */
private fun machine(value: String) =
    Factory.entries.firstOrNull { entry ->
        entry.identifier == value || entry.code?.toString() == value
    } ?: unknownOption("--factory", value)
