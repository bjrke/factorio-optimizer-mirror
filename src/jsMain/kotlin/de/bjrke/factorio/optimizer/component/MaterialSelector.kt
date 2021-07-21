package de.bjrke.factorio.optimizer.component

import de.bjrke.factorio.optimizer.ChangeMaterial
import de.bjrke.factorio.optimizer.FactorioOptimizerProps
import de.bjrke.factorio.optimizer.OptimizerAppState
import de.bjrke.factorio.optimizer.common.model.Material
import kotlinx.html.js.onChangeFunction
import org.w3c.dom.HTMLSelectElement
import react.RBuilder
import react.RProps
import react.dom.option
import react.dom.select
import react.invoke
import react.rFunction
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

private val materialSelector =
    rFunction<MaterialSelectorProps>("materialSelector") { props ->
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
        RProps,
        MaterialSelectorStateProps,
        MaterialSelectorDispatchProps,
        MaterialSelectorProps>(
        { state, _ ->
            availableMaterials = state.parsedRecipes.materials
            material = state.target
        },
        { dispatch, _ -> changeMaterial = { dispatch(ChangeMaterial(it)) } }
    )(materialSelector)

fun RBuilder.reduxMaterialSelector() = reduxMaterialSelector {}

fun RBuilder.urlMaterialSelector(props: FactorioOptimizerProps) {
    materialSelector {
        attrs {
            availableMaterials = props.parsedRecipes.materials
            material = props.target
            changeMaterial = { props.changeUrl { target = it } }
        }
    }
}
