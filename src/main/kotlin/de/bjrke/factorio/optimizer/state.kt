package de.bjrke.factorio.optimizer

import de.bjrke.factorio.common.model.ParsedRecipe
import de.bjrke.factorio.common.model.Material
import de.bjrke.factorio.common.parseRecipes

data class OptimizerAppState(val target: Material = Material("foo"), val parsedRecipe: ParsedRecipe = parseRecipes()) {

}
