import de.bjrke.factorio.common.component.MaterialSelector
import de.bjrke.factorio.common.model.Material
import react.dom.render
import kotlinx.browser.document
import kotlinx.browser.window

fun main() {
    window.onload = {
        render(document.getElementById("root")) {
            child(Welcome::class) {
                attrs {
                    name = "Kotlin/JS"
                }
            }
            child(MaterialSelector::class) {
                attrs {
                    material = Material("foo")
                    availableMaterials = listOf(Material("foo"), Material("bar"))
                }
            }
        }
    }
}
