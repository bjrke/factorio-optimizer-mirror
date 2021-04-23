package de.bjrke.factorio.optimizer

import de.bjrke.factorio.common.model.Material
import redux.RAction

fun targetReducer(state: Material, action: RAction): Material = when (action) {
    is ChangeMaterial -> action.newMaterial
    else -> state
}

fun rootReducer(state: OptimizerAppState, action: RAction) = OptimizerAppState(
    target = targetReducer(state.target, action),
    normalizeTarget = normalizeTargetReducer(state.normalizeTarget, action),
    parsedRecipes = state.parsedRecipes,
    optimizationResult = optimizationResultReducer(state, action)
)

fun normalizeTargetReducer(state: Double, action: RAction): Double = when (action) {
    is NormalizeTo -> action.newNormalizeTarget
    else -> state
}

private fun optimizationResultReducer(state: OptimizerAppState, action: RAction) = when (action) {
    is ChangeMaterial -> optimize(action.newMaterial, state.parsedRecipes)
    else -> state.optimizationResult
}
