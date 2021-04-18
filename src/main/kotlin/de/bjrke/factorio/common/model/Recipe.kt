package de.bjrke.factorio.common.model

data class Recipe(val name: String, val ingredients: Map<Material, Int>, val result: Material)
