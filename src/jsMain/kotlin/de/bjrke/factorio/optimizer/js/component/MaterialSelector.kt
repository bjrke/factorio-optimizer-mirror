package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.js.FactorioOptimizerProps
import react.ChildrenBuilder
import react.FC
import react.Props
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.select

external interface MaterialSelectorStateProps : Props {
    var material: Material
    var availableMaterials: List<Material>
}

external interface MaterialSelectorDispatchProps : Props {
    var changeMaterial: (Material) -> Unit
}

external interface MaterialSelectorProps : MaterialSelectorStateProps, MaterialSelectorDispatchProps

private val materialSelector =
    FC<MaterialSelectorProps>("materialSelector") { props ->
        select {
            value = props.material.name
            onChange = {
                props.changeMaterial(Material.of(it.target.value))
            }
            for (m in props.availableMaterials) {
                option {
                    value = m.name
                    +m.name
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
