package de.bjrke.factorio.common.model

data class Recipe(val name: String, val ingredients: Map<Material, Double>, val result: Map<Material,Double>, val time: Double)
