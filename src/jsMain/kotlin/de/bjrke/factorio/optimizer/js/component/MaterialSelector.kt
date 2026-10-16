package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.business.searchMaterials
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.js.FactorioOptimizerProps
import react.ChildrenBuilder
import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.span
import react.useState
import web.cssom.ClassName

external interface MaterialSelectorStateProps : Props {
    var material: Material
    var availableMaterials: List<Material>
}

external interface MaterialSelectorDispatchProps : Props {
    var changeMaterial: (Material) -> Unit
}

external interface MaterialSelectorProps : MaterialSelectorStateProps, MaterialSelectorDispatchProps

internal val materialSelector =
    FC<MaterialSelectorProps>("materialSelector") { props ->
        val (query, setQuery) = useState("")
        val (open, setOpen) = useState(false)
        val (highlighted, setHighlighted) = useState(0)

        val matches = searchMaterials(props.availableMaterials, query)

        fun select(material: Material) {
            props.changeMaterial(material)
            setQuery("")
            setOpen(false)
            setHighlighted(0)
        }

        div {
            className = ClassName("material-selector")
            span {
                className = ClassName("material-selector-icon")
                icon(props.material.name)
            }
            input {
                className = ClassName("material-selector-input")
                value = query
                placeholder = props.material.name
                onChange = {
                    setQuery(it.target.value)
                    setOpen(true)
                    setHighlighted(0)
                }
                onFocus = { setOpen(true) }
                onBlur = { setOpen(false) }
                onKeyDown = { event ->
                    when (event.key) {
                        "ArrowDown" -> {
                            event.preventDefault()
                            setOpen(true)
                            setHighlighted {
                                (it + 1).coerceAtMost(matches.lastIndex.coerceAtLeast(0))
                            }
                        }
                        "ArrowUp" -> {
                            event.preventDefault()
                            setHighlighted { (it - 1).coerceAtLeast(0) }
                        }
                        "Enter" -> {
                            event.preventDefault()
                            matches.getOrNull(highlighted)?.let { select(it) }
                        }
                        "Escape" -> {
                            setQuery("")
                            setOpen(false)
                        }
                    }
                }
            }
            if (open) {
                div {
                    className = ClassName("material-selector-dropdown")
                    if (matches.isEmpty()) {
                        div {
                            className = ClassName("material-selector-empty")
                            +"no matches"
                        }
                    }
                    for ((index, material) in matches.withIndex()) {
                        div {
                            key = Key(material.name)
                            className =
                                ClassName(
                                    if (index == highlighted) {
                                        "material-selector-option highlighted"
                                    } else {
                                        "material-selector-option"
                                    },
                                )
                            onMouseEnter = { setHighlighted(index) }
                            onMouseDown = {
                                it.preventDefault()
                                select(material)
                            }
                            icon(material.name)
                            +material.name
                        }
                    }
                }
            }
        }
    }

fun ChildrenBuilder.urlMaterialSelector(props: FactorioOptimizerProps) {
    materialSelector {
        availableMaterials = props.parsedRecipes.materials
        material = props.target
        changeMaterial = { props.changeUrl { target = it } }
    }
}
