package de.bjrke.factorio.optimizer.common.business

actual fun loadRecipe(): String = loadResource("/recipes/recipe.lua").readText()

private fun loadResource(s: String) = object {}.javaClass.getResource(s)!!
