package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.model.Surface
import de.bjrke.factorio.optimizer.js.FactorioOptimizerProps
import react.ChildrenBuilder
import react.FC
import react.Props
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import web.html.InputType
import web.html.radio

external interface PlanetSelectorStateProps : Props {
    var selectedSurface: Surface
    var availableSurfaces: List<Surface>
    var enabled: Boolean
}

external interface PlanetSelectorDispatchProps : Props {
    var selectSurface: (Surface) -> Unit
}

external interface PlanetSelectorProps : PlanetSelectorStateProps, PlanetSelectorDispatchProps

private val planetSelector =
    FC<PlanetSelectorProps>("planetSelector") { props ->
        for (surface in props.availableSurfaces) {
            label {
                input {
                    type = InputType.Companion.radio
                    name = "surface"
                    checked = props.selectedSurface == surface
                    disabled = !props.enabled
                    onChange = { _ -> props.selectSurface(surface) }
                }
                icon(surface.identifier)
            }
        }
    }

fun ChildrenBuilder.urlPlanetSelector(props: FactorioOptimizerProps) {
    planetSelector {
        availableSurfaces = Surface.entries
        selectedSurface = props.surface
        enabled = props.spaceAgeEnabled
        selectSurface = { props.changeUrl { surface = it } }
    }
}
