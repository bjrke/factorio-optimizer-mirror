package de.bjrke.factorio.optimizer

import de.bjrke.factorio.common.model.Material

fun targetReducer(state: Material?, action: Any): Material? = when (action) {
    is ChangeMaterial -> action.newMaterial
    else -> state
}

fun rootReducer(state: OptimizerAppState, action: Any) = OptimizerAppState(
    target = targetReducer(state.target, action)
)
