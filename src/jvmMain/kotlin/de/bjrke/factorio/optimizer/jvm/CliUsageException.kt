package de.bjrke.factorio.optimizer.jvm

/**
 * An option value that names no known prototype; `runCli` prints the message and exits non-zero.
 */
internal class CliUsageException(message: String) : IllegalArgumentException(message)

/**
 * Builds the rejection for [option] with an unknown [value], e.g. `Unknown --belt value 'yellow'`.
 */
internal fun unknownOption(option: String, value: String): Nothing =
    throw CliUsageException("Unknown $option value '$value'")
