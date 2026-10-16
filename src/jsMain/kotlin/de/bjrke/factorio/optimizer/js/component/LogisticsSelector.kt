package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.model.BeltTier
import de.bjrke.factorio.optimizer.common.model.InserterTier
import de.bjrke.factorio.optimizer.common.model.LogisticsConfig
import de.bjrke.factorio.optimizer.js.FactorioOptimizerProps
import emotion.react.css
import react.ChildrenBuilder
import react.FC
import react.Props
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import web.cssom.ch
import web.cssom.rem
import web.html.InputType
import web.html.checkbox
import web.html.number
import web.html.radio

external interface LogisticsSelectorStateProps : Props {
    var config: LogisticsConfig
    var selectedBelt: BeltTier
    var selectedInserter: InserterTier
    var beltLanes: Int
    var beltStack: Int
    var inserterStack: Int
    var cheats: Boolean
}

external interface LogisticsSelectorDispatchProps : Props {
    var selectBelt: (BeltTier) -> Unit
    var selectInserter: (InserterTier) -> Unit
    var selectBeltLanes: (Int) -> Unit
    var selectBeltStack: (Int) -> Unit
    var selectInserterStack: (Int) -> Unit
    var selectCheats: (Boolean) -> Unit
}

external interface LogisticsSelectorProps :
    LogisticsSelectorStateProps, LogisticsSelectorDispatchProps

/**
 * Belt and inserter tier pickers plus the use of one/the two belt lanes and the belt/inserter
 * stacking levels. The number inputs express how many items a belt slot / an inserter hand can hold
 * (Factorio's belt stacking and inserter capacity bonus).
 */
internal val logisticsSelector =
    FC<LogisticsSelectorProps>("logisticsSelector") { props ->
        for (belt in props.config.availableBelts) {
            selectableIcon(
                name = belt.identifier,
                selected = props.selectedBelt == belt,
                inputType = InputType.radio,
                groupName = "belt",
                onChange = { props.selectBelt(belt) },
            )
        }
        // factories usually feed a single lane, so a half belt is the default
        label {
            title = "belt lanes"
            css { marginLeft = 0.5.ch }
            input {
                type = InputType.checkbox
                checked = props.beltLanes == BeltTier.MAX_LANES
                onChange = {
                    props.selectBeltLanes(if (it.target.checked) BeltTier.MAX_LANES else 1)
                }
            }
            +" full belt"
        }
        for (inserter in props.config.availableInserters) {
            selectableIcon(
                name = inserter.identifier,
                selected = props.selectedInserter == inserter,
                inputType = InputType.radio,
                groupName = "inserter",
                onChange = { props.selectInserter(inserter) },
            )
        }
        // belt stacking is unlocked by the Space Age stack inserters, so the control sits right
        // next to them and is hidden without Space Age
        if (props.config.spaceAge) {
            stackInput(
                title = "belt stack",
                value = props.beltStack,
                minimum = props.config.beltStackMin(props.selectedInserter),
                maximum = props.config.beltStackMax,
                onValue = props.selectBeltStack,
            )
        }
        stackInput(
            title = "inserter stack",
            value = props.inserterStack,
            // a bulk or stack inserter never carries fewer items than its base hand size
            minimum = props.config.minStack(props.selectedInserter),
            maximum = props.config.maxStack(props.selectedInserter),
            onValue = props.selectInserterStack,
        )
        // cheat items: off by default, they only help testing a blueprint
        label {
            title = "add infinity-chest test inputs and outputs"
            css { marginLeft = 0.5.ch }
            input {
                type = InputType.checkbox
                checked = props.cheats
                onChange = { props.selectCheats(it.target.checked) }
            }
            +" test chests"
        }
    }

private fun ChildrenBuilder.stackInput(
    title: String,
    value: Int,
    minimum: Int,
    maximum: Int,
    onValue: (Int) -> Unit,
) {
    input {
        type = InputType.number
        this.title = title
        this.value = value.toString()
        this.min = minimum.toString()
        max = maximum.toString()
        css { width = 3.rem }
        onChange = { event ->
            event.target.value.toIntOrNull()?.let { onValue(it.coerceIn(minimum, maximum)) }
        }
    }
}

fun ChildrenBuilder.urlLogisticsSelector(props: FactorioOptimizerProps) {
    logisticsSelector {
        config = props.logisticsConfig
        selectedBelt = props.belt
        selectedInserter = props.inserter
        beltLanes = props.beltLanes
        beltStack = props.beltStack
        inserterStack = props.inserterStack
        cheats = props.cheats
        selectBelt = { props.changeUrl { belt = it } }
        selectInserter = { props.changeUrl { inserter = it } }
        selectBeltLanes = { props.changeUrl { beltLanes = it } }
        selectBeltStack = { props.changeUrl { beltStack = it } }
        selectInserterStack = { props.changeUrl { inserterStack = it } }
        selectCheats = { props.changeUrl { cheats = it } }
    }
}
