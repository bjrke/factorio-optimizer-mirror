package de.bjrke.factorio.optimizer

import de.bjrke.factorio.common.model.Material
import redux.RAction

data class ChangeMaterial(val newMaterial: Material) : RAction
