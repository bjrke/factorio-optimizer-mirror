package de.bjrke.factorio.optimizer.jvm

import de.bjrke.factorio.optimizer.common.core.model.Material

/**
 * An option value that names no known prototype; `runCli` prints the message and exits non-zero.
 */
internal class CliUsageException(message: String) : IllegalArgumentException(message)

/**
 * Builds the rejection for [option] with an unknown [value], e.g. `Unknown --belt value 'yellow'`.
 */
internal fun unknownOption(option: String, value: String): Nothing =
    throw CliUsageException("Unknown $option value '$value'")

/**
 * Resolves the explicit `--exclude` names against the parsed universe. An unknown name is a usage
 * error: silently ignoring it would build the very material the user asked to leave out.
 */
internal fun resolveExclusions(names: List<String>, known: List<Material>): Set<Material> {
    val byName = known.associateBy { it.name }
    val unknown = names.filterNot(byName::containsKey)
    if (unknown.isNotEmpty()) {
        val label = if (unknown.size == 1) "material" else "materials"
        throw CliUsageException("Unknown $label ${unknown.joinToString(", ") { "'$it'" }}")
    }
    return names.mapTo(mutableSetOf()) { byName.getValue(it) }
}
