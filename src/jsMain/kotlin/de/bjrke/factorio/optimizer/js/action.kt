package de.bjrke.factorio.optimizer.js

import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.Material
import redux.RAction

data class ChangeMaterial(val newMaterial: Material) : RAction

data class SelectFactory(val selectedFactories: Set<Factory>) : RAction

data class NormalizeTo(val newNormalizeTarget: Double) : RAction
