package de.bjrke.factorio.optimizer.common.prototype.selection

import de.bjrke.factorio.optimizer.common.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.prototype.BeltTier
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.prototype.FactoryConfig
import de.bjrke.factorio.optimizer.common.prototype.InserterTier
import de.bjrke.factorio.optimizer.common.prototype.Surface

/**
 * The on/off flags living next to the factory codes in the single `selection` URL parameter: Space
 * Age and the use of the second belt lane. A flag is on exactly when its [code] appears in the
 * parameter.
 */
enum class SelectionFlag(val code: Char) {
    SPACE_AGE('S'),
    FULL_BELT('L');

    internal fun enabledIn(selection: String?) = selection != null && code in selection
}

/** Whether the `selection` parameter announces Space Age. */
fun spaceAgeIn(selection: String?) = SelectionFlag.SPACE_AGE.enabledIn(selection)

/**
 * Whether the `selection` parameter asks for both belt lanes instead of the single default lane.
 */
fun fullBeltIn(selection: String?) = SelectionFlag.FULL_BELT.enabledIn(selection)

/**
 * Placeholder for the `selection` URL segment when every choice is at its default. It is not a code
 * and decodes to the defaults; it only keeps the segment present so the route always matches.
 */
const val EMPTY_SELECTION = "-"

/**
 * The choices one `selection` URL segment carries. Grouping them keeps [selectionString] and its
 * callers from passing the same seven-argument tuple around; the numeric stack sizes stay separate
 * URL segments and are not part of this object.
 */
data class Selection(
    val factories: Set<Factory>,
    val spaceAge: Boolean,
    val surface: Surface,
    val fullBelt: Boolean,
    val belt: BeltTier,
    val inserter: InserterTier,
    val beltEnd: BeltEnd = BeltEnd.CONSTANT_COMBINATOR,
)

/**
 * Encodes [selection] into the single URL parameter. Only non-default choices carry a code (Space
 * Age stays "on" via [SelectionFlag.SPACE_AGE]), every option contributes its unique [code], and
 * sorting keeps the URL canonical. An all-defaults choice returns the empty string; callers
 * substitute [EMPTY_SELECTION] to keep the segment.
 */
fun selectionString(selection: Selection) = buildList {
    addAll(FactoryConfig(selection.spaceAge).toUrlParam(selection.factories).toList())
    if (selection.spaceAge) add(SelectionFlag.SPACE_AGE.code)
    selection.surface.code?.let { add(it) }
    if (selection.fullBelt) add(SelectionFlag.FULL_BELT.code)
    selection.beltEnd.code?.let { add(it) }
    selection.belt.code?.let { add(it) }
    selection.inserter.code?.let { add(it) }
}
    .sorted()
    .joinToString("")
