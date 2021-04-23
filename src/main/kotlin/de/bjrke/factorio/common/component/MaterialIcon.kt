package de.bjrke.factorio.common.component

import de.bjrke.factorio.common.model.Material
import kotlinx.css.*
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import styled.css
import styled.styledImg

interface MaterialIconProps : RProps {
    var material: Material
    var size: Int
}

class MaterialIcon(props: MaterialIconProps) : RComponent<MaterialIconProps, RState>(props) {
    override fun RBuilder.render() {
        styledImg(src = "icons/${props.material.name}.png") {
            css {
                width = props.size.px
                height = props.size.px
                objectFit = ObjectFit.cover
                objectPosition = "0 0"
            }
            attrs {
                height = "${props.size}"
            }
        }
    }
}
