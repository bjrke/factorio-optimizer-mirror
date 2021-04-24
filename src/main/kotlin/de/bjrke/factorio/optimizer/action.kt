package de.bjrke.factorio.optimizer

import de.bjrke.factorio.optimizer.model.Material
import redux.RAction

data class ChangeMaterial(val newMaterial: Material) : RAction

data class NormalizeTo(val newNormalizeTarget: Double) : RAction
