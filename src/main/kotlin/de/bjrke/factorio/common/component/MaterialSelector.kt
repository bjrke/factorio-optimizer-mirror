package de.bjrke.factorio.common.component

import de.bjrke.factorio.common.model.Material
import kotlinx.html.js.onChangeFunction
import org.w3c.dom.HTMLSelectElement
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.dom.option
import react.dom.select

external interface MaterialSelectorProps : RProps {
    var material: Material
    var availableMaterials: List<Material>
}

data class MaterialSelectorState(val material: Material) : RState

@JsExport
class MaterialSelector(props: MaterialSelectorProps) : RComponent<MaterialSelectorProps, MaterialSelectorState>(props) {

    init {
        state = MaterialSelectorState(props.material);
    }

    override fun RBuilder.render() {
        select {
            attrs {
                onChangeFunction = { event ->
                    setState(
                        MaterialSelectorState(Material((event.target as HTMLSelectElement).value))
                    )
                }
                value = state.material.name
            }
            for (m in props.availableMaterials) {
                option {
                    attrs.value = m.name
                    +m.name
                }
            }

        }
    }

}
