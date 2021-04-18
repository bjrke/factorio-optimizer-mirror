import de.bjrke.factorio.common.component.materialSelector
import de.bjrke.factorio.optimizer.OptimizerAppState
import de.bjrke.factorio.optimizer.rootReducer
import kotlinx.browser.document
import kotlinx.browser.window
import react.dom.div
import react.dom.render
import react.redux.provider
import redux.RAction
import redux.compose
import redux.createStore
import redux.rEnhancer

fun main() {

    val store = createStore<OptimizerAppState, RAction, dynamic>(
        ::rootReducer,
        OptimizerAppState(),
        compose(
            rEnhancer(),
            js("if(window.__REDUX_DEVTOOLS_EXTENSION__ )window.__REDUX_DEVTOOLS_EXTENSION__ ();else(function(f){return f;});")
        )
    )

    window.onload = {
        render(document.getElementById("root")) {
            provider(store) {
                div {
                    materialSelector {}
                }

            }
        }
    }
}
