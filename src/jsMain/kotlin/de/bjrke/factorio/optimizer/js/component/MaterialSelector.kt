package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.js.ChangeMaterial
import de.bjrke.factorio.optimizer.js.FactorioOptimizerProps
import de.bjrke.factorio.optimizer.js.OptimizerAppState
import kotlinx.html.js.onChangeFunction
import org.w3c.dom.HTMLSelectElement
import react.Props
import react.RBuilder
import react.dom.option
import react.dom.select
import react.fc
import react.invoke
import react.redux.rConnect
import redux.WrapperAction

external interface MaterialSelectorStateProps : Props {
    var material: Material
    var availableMaterials: List<Material>
}

external interface MaterialSelectorDispatchProps : Props {
    var changeMaterial: (Material) -> Unit
}

external interface MaterialSelectorProps :
    MaterialSelectorStateProps, MaterialSelectorDispatchProps

private fun RBuilder.materialSelector(props: MaterialSelectorProps) {
    select {
        attrs.onChangeFunction =
            { event ->
                props.changeMaterial(Material.of((event.target as HTMLSelectElement).value))
            }
        attrs.value = props.material.name
        for (m in props.availableMaterials) {
            option {
                attrs.value = m.name
                +m.name
            }
        }
    }
}

private val reduxMaterialSelector =
    rConnect<
        OptimizerAppState,
        ChangeMaterial,
        WrapperAction,
        Props,
        MaterialSelectorStateProps,
        MaterialSelectorDispatchProps,
        MaterialSelectorProps>(
        { state, _ ->
            availableMaterials = state.parsedRecipes.materials
            material = state.target
        },
        { dispatch, _ -> changeMaterial = { dispatch(ChangeMaterial(it)) } }
    )(RBuilder::materialSelector)

fun RBuilder.reduxMaterialSelector() = reduxMaterialSelector {}

fun RBuilder.urlMaterialSelector(props: FactorioOptimizerProps) {
    child(fc(RBuilder::materialSelector)) {
        attrs {
            availableMaterials = props.parsedRecipes.materials
            material = props.target
            changeMaterial = { props.changeUrl { target = it } }
        }
    }
}
