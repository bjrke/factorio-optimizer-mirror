package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.production.validNormalizeTarget
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.factorio.optimizer.common.model.factoryProductionComparator
import de.bjrke.factorio.optimizer.common.util.superRound
import de.bjrke.factorio.optimizer.js.FactorioOptimizerProps
import emotion.react.css
import react.ChildrenBuilder
import react.FC
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.span
import react.useEffect
import react.useState
import web.cssom.Border
import web.cssom.Color
import web.cssom.Display
import web.cssom.LineStyle
import web.cssom.VerticalAlign
import web.cssom.Visibility
import web.cssom.ch
import web.cssom.px
import web.cssom.rem
import web.cssom.rgb
import web.html.InputType
import web.html.checkbox
import web.html.number
import web.mouse.AUXILIARY
import web.mouse.MouseButton

external interface OptimizationResultPresenterStateProps : Props {
    var optimizationResult: OptimizationResult
    var logistics: LogisticsCalculator
    var normalizeTarget: Double
    /** Materials the plan imports instead of building; their import row carries a checked box. */
    var excludedMaterials: Set<Material>
}

external interface OptimizationResultPresenterDispatchProps : Props {
    var changeMaterial: (Material) -> Unit
    /** A click on an amount or icon: pins the amount and pushes one history entry. */
    var changeNormalizeTo: (Double) -> Unit
    /**
     * A committed amount edit (Enter or blur): replaces its history entry instead of one per edit.
     */
    var editNormalizeTo: (Double) -> Unit
    /** Adds or removes [Material] from the exclusions; `true` excludes it from the plan. */
    var changeExcluded: (Material, Boolean) -> Unit
}

external interface OptimizationResultPresenterProps :
    OptimizationResultPresenterStateProps, OptimizationResultPresenterDispatchProps

/** `MouseEvent.button` value of the middle mouse button. */
private val MIDDLE_BUTTON = MouseButton.AUXILIARY

/**
 * The amount of one derived row value as an editable field. It shows `value × normalizeTarget` and
 * writes the URL only when the edit is committed (Enter or blur): a keystroke must neither replan
 * the factory nor rewrite the field's caret mid-number. A committed value the display cannot show
 * (empty, zero, or so large the product is not finite) is discarded and the field falls back.
 */
private val derivedNumberField =
    FC<DerivedNumberFieldProps>("derivedNumberField") { props ->
        val displayed = superRound(props.value * props.normalizeTarget).toString()
        val (text, setText) = useState(displayed)
        // an icon click or another row changes the plan under the field: follow it
        useEffect(displayed) { setText(displayed) }

        fun commit() {
            val typed = text.toDoubleOrNull()
            // the caller stores a plan scale, so the stored normalize target becomes value / typed
            if (typed == null || validNormalizeTarget(typed / props.value) == null) {
                setText(displayed)
                return
            }
            props.edit(props.value / typed)
        }

        input {
            type = InputType.number
            min = "0"
            title = props.title
            css {
                width = 4.rem
                if (props.wide) marginRight = 1.ch else marginRight = 0.25.rem
            }
            value = text
            onChange = { setText(it.target.value) }
            onKeyDown = { event ->
                if (event.key == "Enter") {
                    event.preventDefault()
                    commit()
                }
            }
            onBlur = { commit() }
        }
    }

external interface DerivedNumberFieldProps : Props {
    /**
     * The row's value before the plan scale; the field shows it multiplied by the normalize target.
     */
    var value: Double
    var normalizeTarget: Double
    var title: String?
    /**
     * The amount field sits next to a material and keeps a wider gap than a belt/inserter count.
     */
    var wide: Boolean

    /**
     * Receives the plan scale, the value [OptimizationResultPresenterDispatchProps.editNormalizeTo]
     * expects.
     */
    var edit: (Double) -> Unit
}

private fun ChildrenBuilder.amountInput(
    props: OptimizationResultPresenterProps,
    amount: Double,
) {
    derivedNumberField {
        value = amount
        normalizeTarget = props.normalizeTarget
        title = null
        wide = true
        edit = props.editNormalizeTo
    }
}

/**
 * A clickable icon for an amount: a left click pins the amount, a middle click (when the icon
 * stands for a [material]) selects that material as the new target.
 */
private fun ChildrenBuilder.amountIcon(
    props: OptimizationResultPresenterProps,
    name: String,
    amount: Double,
    material: Material? = null,
) {
    a {
        css { marginRight = 1.ch }
        onClick = { props.changeNormalizeTo(amount) }
        if (material != null) {
            // a middle click selects the material and must not start the browser's autoscroll
            onMouseDown = {
                if (it.button == MIDDLE_BUTTON) {
                    it.preventDefault()
                    props.changeMaterial(material)
                }
            }
        }
        icon(name)
    }
}

private fun ChildrenBuilder.amountInputIcon(
    props: OptimizationResultPresenterProps,
    name: String,
    amount: Double,
    material: Material? = null,
) {
    amountInput(props, amount)
    amountIcon(props, name, amount, material)
}

// fixed widths so a row is exactly as wide no matter whether it carries a belt+inserter, a
// pump+pipe or (for recipes without ingredients) nothing at all
private val LOGISTICS_ITEM_WIDTH = 6.75.rem

/** Fixed input/output column width; empty and fluid columns are padded to match. */
private val COLUMN_WIDTH = 24.rem

/**
 * The exclusion checkbox of one production row. A built row offers to exclude its material; an
 * import row only unchecks to build the material again. The target is never excludable, and a
 * material that is already imported has nothing to exclude, so their box is hidden but keeps its
 * space; the wrapper carries the reason on hover, because a hidden box cannot be hovered. Keeping
 * the space matters: a missing box would narrow the row's inline group and break the alignment of
 * the columns.
 */
private fun ChildrenBuilder.exclusionCheckbox(
    props: OptimizationResultPresenterProps,
    material: Material,
    imported: Boolean,
) {
    val excluded = material in props.excludedMaterials
    val target = material == props.optimizationResult.target
    val enabled = !target && (!imported || excluded)
    span {
        title =
            when {
                excluded -> "Include ${material.prototypeName} in the blueprint"
                target -> "The target cannot be excluded from the blueprint"
                imported ->
                    "${material.prototypeName} is already imported, so there is nothing to exclude"
                else -> "Exclude ${material.prototypeName} from the blueprint"
            }
        css {
            display = Display.inlineBlock
            verticalAlign = VerticalAlign.middle
            marginRight = 0.25.rem
        }
        input {
            type = InputType.checkbox
            checked = excluded
            disabled = !enabled
            css {
                // the reserved space is the point: an unavailable box stays in the layout but out
                // of sight, and the wrapper keeps its reason reachable on hover
                visibility = if (enabled) Visibility.visible else Visibility.hidden
                verticalAlign = VerticalAlign.middle
            }
            onChange = { event -> props.changeExcluded(material, event.target.checked) }
        }
    }
}

/**
 * A bordered box around a group of input fields that belong together. [inline] boxes hug their
 * content and sit in the row (the factory), while the default boxes fill their column (materials).
 */
private fun ChildrenBuilder.groupBox(inline: Boolean = false, content: ChildrenBuilder.() -> Unit) {
    div {
        css {
            border = Border(1.px, LineStyle.solid, rgb(120, 120, 120))
            // the theme token wins; the literal is the light fallback for contexts without the
            // stylesheet tokens (e.g. component tests)
            borderColor = Color("var(--border, #787878)")
            borderRadius = 0.25.rem
            padding = 0.25.rem
            marginBottom = 0.25.rem
            marginRight = 0.25.rem
            if (inline) {
                display = Display.inlineBlock
                verticalAlign = VerticalAlign.middle
            }
        }
        content()
    }
}

/** A material's amount plus its belt and inserter counts, boxed together. */
private fun ChildrenBuilder.materialGroup(
    props: OptimizationResultPresenterProps,
    factory: Factory,
    material: Material,
    amount: Double,
    isOutput: Boolean,
) {
    groupBox {
        amountInputIcon(props, material.prototypeName, amount, material)
        materialLogistics(props, factory, material, amount, isOutput)
    }
}

/**
 * A derived logistics count (belt, inserter, pump) as an editable field next to its clickable icon.
 * Editing it or clicking the icon scales the whole plan through the normalize target, exactly like
 * the material amounts, so e.g. a belt can be pinned to one.
 */
private fun ChildrenBuilder.logisticsInputIcon(
    props: OptimizationResultPresenterProps,
    name: String,
    unit: String,
    amount: Double,
    rotation: Int? = null,
    flipHorizontal: Boolean = false,
) {
    derivedNumberField {
        value = amount
        normalizeTarget = props.normalizeTarget
        title = unit
        wide = false
        edit = props.editNormalizeTo
    }
    a {
        css { marginRight = 0.5.rem }
        if (amount > 0.0) {
            onClick = { props.changeNormalizeTo(amount) }
        }
        icon(name) {
            this.rotation = rotation
            this.flipHorizontal = flipHorizontal
        }
    }
}

private fun ChildrenBuilder.pipeMarker() {
    span {
        css {
            display = Display.inlineBlock
            width = LOGISTICS_ITEM_WIDTH
        }
        // the icon carries the "pipe" title used as the marker
        icon("pipe")
    }
}

/**
 * The logistics needed to move [material] at [rate] items/fluid per second: a belt and an inserter
 * for solids, a pump plus a pipe marker for fluids. The counts are editable like the material
 * amounts, and [LogisticsCalculator.transport] decides the stacking and whether an inserter is
 * needed at all.
 */
private fun ChildrenBuilder.materialLogistics(
    props: OptimizationResultPresenterProps,
    factory: Factory,
    material: Material,
    rate: Double,
    isOutput: Boolean,
) {
    val calculator = props.logistics
    val counts = calculator.transport(factory, material, rate, isOutput)
    if (material.isFluid) {
        logisticsInputIcon(props, "pump", "pumps", counts.pumps)
        pipeMarker()
        return
    }

    logisticsInputIcon(
        props,
        calculator.belt.identifier,
        "belts",
        counts.belts,
        // an input belt feeds the machine (clockwise), an output belt carries the product away
        rotation = if (isOutput) -90 else 90,
    )
    if (!counts.directOutput) {
        logisticsInputIcon(
            props,
            calculator.inserter.identifier,
            "inserters",
            counts.inserters,
            // the output inserter points the other way, so mirror it
            flipHorizontal = isOutput,
        )
    }
}

internal val optimizationResult =
    FC<OptimizationResultPresenterProps>("optimizationResult") { props ->
        props.optimizationResult.factories.values.sortedWith(factoryProductionComparator).forEach {
            factoryProduction ->
            div {
                css {
                    // one factory per row, so the result stays a single column next to the
                    // blueprint
                    display = Display.block
                    padding = 0.5.rem
                    // the material hue tints the block (keeping its identity) but is mixed with
                    // the themed panel so it stays muted in dark mode
                    backgroundColor =
                        Color(
                            "color-mix(in srgb, ${hashColor(factoryProduction.material.name)} " +
                                "var(--factory-tint, 70%), var(--factory-panel, #fff))",
                        )
                }
                div {
                    css {
                        display = Display.inlineBlock
                        verticalAlign = VerticalAlign.middle
                        // recipes without ingredients (mining, pumping, ...) still reserve the
                        // full column so every recipe block keeps the same width
                        width = COLUMN_WIDTH
                    }
                    factoryProduction.recipe.ingredients.keys.forEach {
                        materialGroup(
                            props,
                            factoryProduction.factory,
                            it,
                            factoryProduction.consumption(it),
                            isOutput = false,
                        )
                    }
                }

                groupBox(inline = true) {
                    exclusionCheckbox(
                        props,
                        factoryProduction.material,
                        imported = Category.IMPORT in factoryProduction.recipe.categories,
                    )
                    amountInputIcon(
                        props,
                        factoryProduction.factory.identifier,
                        factoryProduction.amount,
                    )
                    // middle-clicking the recipe icon picks the produced material as the target
                    amountIcon(
                        props,
                        factoryProduction.recipe.name,
                        factoryProduction.amount,
                        factoryProduction.material,
                    )
                }

                div {
                    css {
                        display = Display.inlineBlock
                        verticalAlign = VerticalAlign.middle
                        // the fixed width keeps the output column aligned with the input column
                        width = COLUMN_WIDTH
                    }
                    factoryProduction.recipe.result.keys.forEach {
                        materialGroup(
                            props,
                            factoryProduction.factory,
                            it,
                            factoryProduction.production(it),
                            isOutput = true,
                        )
                    }
                }
            }
        }

        // an exclusion can outlive the plan that showed it (a target change, or a byproduct that
        // covers the demand), so keep a checked row for every excluded material without a row
        val shown = props.optimizationResult.factories.values.mapTo(mutableSetOf()) { it.material }
        props.excludedMaterials
            .filterNot { it in shown }
            .sortedBy { it.name }
            .forEach { material ->
                div {
                    css {
                        display = Display.block
                        padding = 0.5.rem
                    }
                    groupBox(inline = true) {
                        exclusionCheckbox(props, material, imported = true)
                        icon(material.prototypeName)
                        span {
                            css { marginLeft = 0.25.rem }
                            +material.prototypeName
                        }
                    }
                }
            }
    }

fun hashColor(s: String, alpha: Double = 1.0): Color {
    val hash = s.hashCode()
    val r = hash and 0x7F0000 shr 16
    val g = hash and 0x007F00 shr 8
    val b = hash and 0x00007F

    return rgb(r + 128, g + 128, b + 128, alpha)
}

fun ChildrenBuilder.urlOptimizationResultPresenter(props: FactorioOptimizerProps) {
    optimizationResult {
        optimizationResult = props.optimizationResult
        logistics = props.logistics
        normalizeTarget = props.normalizeTarget
        excludedMaterials = props.excludedMaterials
        changeMaterial = { props.changeUrl { target = it } }
        changeNormalizeTo = { props.changeUrl { normalizeTarget = 1 / it } }
        editNormalizeTo = { props.changeUrl(replace = true) { normalizeTarget = 1 / it } }
        changeExcluded = { material, exclude ->
            props.changeUrl {
                excludedMaterials =
                    if (exclude) excludedMaterials + material else excludedMaterials - material
            }
        }
    }
}
