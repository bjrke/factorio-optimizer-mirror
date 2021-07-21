package de.bjrke.factorio.optimizer

import de.bjrke.factorio.optimizer.common.business.optimize
import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.Material
import redux.RAction

private fun targetReducer(state: Material, action: RAction): Material =
    when (action) {
        is ChangeMaterial -> action.newMaterial
        else -> state
    }

private fun normalizeTargetReducer(state: Double, action: RAction): Double =
    when (action) {
        is NormalizeTo -> action.newNormalizeTarget
        else -> state
    }

private fun factoriesReducer(state: Set<Factory>, action: RAction): Set<Factory> =
    when (action) {
        is SelectFactory -> action.selectedFactories
        else -> state
    }

private fun optimizationResultReducer(state: OptimizerAppState, action: RAction) =
    when (action) {
        is ChangeMaterial -> optimize(action.newMaterial, state.parsedRecipes, state.factories)
        is SelectFactory -> optimize(state.target, state.parsedRecipes, action.selectedFactories)
        else -> state.optimizationResult
    }

fun rootReducer(state: OptimizerAppState, action: RAction) =
    OptimizerAppState(
        target = targetReducer(state.target, action),
        normalizeTarget = normalizeTargetReducer(state.normalizeTarget, action),
        parsedRecipes = state.parsedRecipes,
        factories = factoriesReducer(state.factories, action),
        optimizationResult = optimizationResultReducer(state, action)
    )
