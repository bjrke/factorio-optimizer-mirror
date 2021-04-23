package de.bjrke.factorio.common.component

import de.bjrke.factorio.common.model.Material
import de.bjrke.factorio.optimizer.ChangeMaterial
import de.bjrke.factorio.optimizer.OptimizerAppState
import kotlinx.html.js.onChangeFunction
import org.w3c.dom.HTMLSelectElement
import react.*

import react.dom.option
import react.dom.select
import react.redux.rConnect
import redux.WrapperAction

interface MaterialSelectorStateProps : RProps {
    var material: Material
    var availableMaterials: List<Material>
}

interface MaterialSelectorDispatchProps : RProps {
    var changeMaterial: (Material) -> Unit
}

interface MaterialSelectorProps : MaterialSelectorStateProps, MaterialSelectorDispatchProps

class MaterialSelector(props: MaterialSelectorProps) :
    RComponent<MaterialSelectorProps, RState>(props) {

    override fun RBuilder.render() {
        select {
            attrs {
                onChangeFunction = { event ->
                    props.changeMaterial(Material((event.target as HTMLSelectElement).value))
                }
                value = props.material.name
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

val materialSelector: RClass<RProps> =
    rConnect<OptimizerAppState, ChangeMaterial, WrapperAction, RProps, MaterialSelectorStateProps, MaterialSelectorDispatchProps, MaterialSelectorProps>(
        { state, _ ->
            availableMaterials = state.parsedRecipes.materials
            material = state.target
        },
        { dispatch, _ ->
            changeMaterial = { dispatch(ChangeMaterial(it)) }
        }
    )(MaterialSelector::class.rClass)
