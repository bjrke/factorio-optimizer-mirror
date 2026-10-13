package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.model.Surface
import de.bjrke.factorio.optimizer.js.FactorioOptimizerProps
import react.ChildrenBuilder
import react.FC
import react.Props
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

internal val planetSelector =
    FC<PlanetSelectorProps>("planetSelector") { props ->
        for (surface in props.availableSurfaces) {
            selectableIcon(
                name = surface.identifier,
                selected = props.selectedSurface == surface,
                inputType = InputType.Companion.radio,
                groupName = "surface",
                disabled = !props.enabled,
                onChange = { props.selectSurface(surface) },
            )
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
