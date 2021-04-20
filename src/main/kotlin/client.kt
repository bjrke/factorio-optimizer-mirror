import de.bjrke.factorio.common.component.materialSelector
import de.bjrke.factorio.optimizer.OptimizerAppState
import de.bjrke.factorio.optimizer.rootReducer
import kotlinext.js.require
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.css.Color
import kotlinx.css.backgroundColor
import react.dom.render
import react.redux.provider
import redux.RAction
import redux.compose
import redux.createStore
import redux.rEnhancer
import styled.css
import styled.styledDiv

fun main() {

    val store = createStore<OptimizerAppState, RAction, dynamic>(
        ::rootReducer,
        OptimizerAppState(),
        compose(
            rEnhancer(),
            js("if(window.__REDUX_DEVTOOLS_EXTENSION__ )window.__REDUX_DEVTOOLS_EXTENSION__ ();else(function(f){return f;});")
        )
    )
    //val json = require("../../../resources/main/recipe.json")
    val json = require("recipe.json")
    console.log(json)

    window.onload = {
        render(document.getElementById("root")) {
            provider(store) {
                styledDiv {
                    css {
                        backgroundColor = Color.red
                    }
                    materialSelector {}
                    materialSelector {}
                }

            }
        }
    }
}
