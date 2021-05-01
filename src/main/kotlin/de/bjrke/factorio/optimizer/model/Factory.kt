package de.bjrke.factorio.optimizer.model

private val assembler1Categories = setOf(Category.CRAFTING, Category.ADVANCED_CRAFTING)
private val assembler2Categories = assembler1Categories.plus(Category.CRAFTING_WITH_FLUID)
private val smelterCategories = setOf(Category.SMELTING)

enum class Factory(val identifier: String, val categories: Set<Category>, val speed: Double = 1.0) {

    ASSEMBLING_MACHINE_1("assembling-machine-1", assembler1Categories, 0.5),
    ASSEMBLING_MACHINE_2("assembling-machine-2", assembler2Categories, 0.75),
    ASSEMBLING_MACHINE_3("assembling-machine-3", assembler2Categories, 1.25),

    OIL_REFINERY("oil-refinery", setOf(Category.OIL_PROCESSING)),
    CHEMICAL_PLANT("chemical-plant", setOf(Category.CHEMISTRY)),
    CENTRIFUGE("centrifuge", setOf(Category.CENTRIFUGING)),
    ROCKET_SILO("rocket-silo", setOf(Category.ROCKET_BUILDING)),

    STONE_FURNACE("stone-furnace", smelterCategories),
    STEEL_FURNACE("steel-furnace", smelterCategories, 2.0),
    ELECTRIC_FURNACE("electric-furnace", smelterCategories, 2.0),

    NO_FACTORY("compilatron-chest", setOf())

    ;

}

val allFactories = Factory.values().toSet()
val smelters = allFactories.filter { it.categories == smelterCategories }
val assemblers = allFactories.filter { it.categories.contains(Category.CRAFTING) }
val tech = allFactories - smelters - assemblers - Factory.NO_FACTORY
val defaultFactories = setOf(Factory.ASSEMBLING_MACHINE_3, Factory.ELECTRIC_FURNACE, Factory.NO_FACTORY) + tech
