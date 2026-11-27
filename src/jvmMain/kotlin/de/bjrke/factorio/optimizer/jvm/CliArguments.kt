package de.bjrke.factorio.optimizer.jvm

import de.bjrke.factorio.optimizer.common.blueprint.printing.output.parseFactorioUrl

/** Options that switch a mode on and take no value. */
private val flagOptions =
    setOf(
        "--base-game",
        "--full-belt",
        "--cheats",
        "--settings",
        "--listing",
        "--layout",
        "--json",
        "--blueprint",
    )

/** Options that need `--name=<value>`; the bare form is rejected instead of ignored. */
private val valuedOptions =
    setOf(
        "planet",
        "factory",
        "belt",
        "inserter",
        "belt-stack",
        "inserter-stack",
        "intermediates",
        "exclude",
        "normalize",
        "belt-end",
    )

/**
 * Rejects every argument the parser would otherwise ignore: an unknown `--option`, a flag with a
 * value, a valued option without `=`, a second URL or a second positional material. A misspelled
 * flag must fail instead of running a silently different configuration.
 */
internal fun validateArguments(args: Array<String>) {
    argumentProblem(args)?.let { throw CliUsageException(it) }
}

/** The first unusable argument, or null when every argument is understood. */
private fun argumentProblem(args: Array<String>): String? {
    var url = false
    var positional = false
    var problem: String? = null
    for (arg in args) {
        val isUrl = parseFactorioUrl(arg) != null
        problem =
            problem
                ?: when {
                    arg.startsWith("--") -> optionProblem(arg)
                    isUrl && url -> "Only one URL may be passed"
                    isUrl -> {
                        url = true
                        null
                    }
                    positional -> "Unexpected argument '$arg'"
                    else -> {
                        positional = true
                        null
                    }
                }
    }
    return problem
}

/** Why an `--option` argument is unusable, or null when it is one the parser reads. */
private fun optionProblem(arg: String): String? {
    val name = arg.substringBefore('=')
    val hasValue = '=' in arg
    return when {
        name in flagOptions -> if (hasValue) "$name takes no value" else null
        name.removePrefix("--") in valuedOptions ->
            if (hasValue) null else "$name needs a value: $name=<value>"
        else -> "Unknown option '$name'"
    }
}
