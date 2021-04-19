package de.bjrke.factorio.optimizer

import de.bjrke.factorio.common.model.Material
import redux.RAction

fun targetReducer(state: Material, action: RAction): Material = when (action) {
    is ChangeMaterial -> action.newMaterial
    else -> state
}

fun rootReducer(state: OptimizerAppState, action: RAction) = OptimizerAppState(
    target = targetReducer(state.target, action)
)
