package de.bjrke.factorio.optimizer

import de.bjrke.factorio.optimizer.component.factorySelector
import de.bjrke.factorio.optimizer.component.materialSelector
import de.bjrke.factorio.optimizer.component.normalizeTargetPresenter
import de.bjrke.factorio.optimizer.component.optimizationResultPresenter
import kotlinx.browser.document
import kotlinx.browser.window
import react.dom.div
import react.dom.render
import react.redux.provider
import redux.compose
import redux.createStore
import redux.rEnhancer

fun main() {
    window.onload = {
        val store = createStore(
            ::rootReducer,
            OptimizerAppState(),
            compose(
                rEnhancer(),
                js("if(window.__REDUX_DEVTOOLS_EXTENSION__ )window.__REDUX_DEVTOOLS_EXTENSION__ ();else(function(f){return f;});")
            )
        )
        render(document.getElementById("root")) {
            provider(store) {
                div {
                    materialSelector {}
                    factorySelector {}
                    normalizeTargetPresenter {}
                    optimizationResultPresenter {}
                }

            }
        }
    }
}

