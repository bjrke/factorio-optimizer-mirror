package de.bjrke.factorio.optimizer.common.model

/**
 * The on/off flags living next to the factory codes in the single `selection` URL parameter: Space
 * Age and the use of the second belt lane. A flag is on exactly when its [code] appears in the
 * parameter.
 */
enum class SelectionFlag(val code: Char) {
    SPACE_AGE('S'),
    FULL_BELT('L');

    fun enabledIn(selection: String?) = selection != null && code in selection
}

/**
 * Placeholder for the `selection` URL segment when every choice is at its default. It is not a code
 * and decodes to the defaults; it only keeps the segment present so the route always matches.
 */
const val EMPTY_SELECTION = "-"

/**
 * Encodes the choices into the single `selection` URL parameter. Only non-default choices carry a
 * code (Space Age stays "on" via [SelectionFlag.SPACE_AGE]), every option contributes its unique
 * [code], and sorting keeps the URL canonical. An all-defaults choice returns the empty string;
 * callers substitute [EMPTY_SELECTION] to keep the segment.
 *
 * The numeric stack sizes stay separate URL segments and are not part of this string.
 */
fun selectionString(
    factories: Set<Factory>,
    spaceAge: Boolean,
    surface: Surface,
    fullBelt: Boolean,
    belt: Belt,
    inserter: Inserter,
) =
    buildList {
            addAll(FactoryConfig(spaceAge).toUrlParam(factories).toList())
            if (spaceAge) add(SelectionFlag.SPACE_AGE.code)
            surface.code?.let { add(it) }
            if (fullBelt) add(SelectionFlag.FULL_BELT.code)
            belt.code?.let { add(it) }
            inserter.code?.let { add(it) }
        }
        .sorted()
        .joinToString("")
