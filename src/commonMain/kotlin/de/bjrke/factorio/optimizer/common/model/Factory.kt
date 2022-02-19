package de.bjrke.factorio.optimizer.common.model

import de.bjrke.factorio.optimizer.common.model.Factories.removeFactory

private val assembler1Categories = setOf(Category.CRAFTING, Category.ADVANCED_CRAFTING)
private val assembler2Categories = assembler1Categories.plus(Category.CRAFTING_WITH_FLUID)
private val smelterCategories = setOf(Category.SMELTING)

enum class Factory(
    val code: Char?,
    val identifier: String,
    val categories: Set<Category>,
    val speed: Double = 1.0
) {
    ASSEMBLING_MACHINE_1('1', "assembling-machine-1", assembler1Categories, 0.5),
    ASSEMBLING_MACHINE_2('2', "assembling-machine-2", assembler2Categories, 0.75),
    ASSEMBLING_MACHINE_3('3', "assembling-machine-3", assembler2Categories, 1.25),
    OIL_REFINERY('o', "oil-refinery", setOf(Category.OIL_PROCESSING)),
    CHEMICAL_PLANT('c', "chemical-plant", setOf(Category.CHEMISTRY)),
    CENTRIFUGE('u', "centrifuge", setOf(Category.CENTRIFUGING)),
    ROCKET_SILO('r', "rocket-silo", setOf(Category.ROCKET_BUILDING)),
    STONE_FURNACE('f', "stone-furnace", smelterCategories),
    STEEL_FURNACE('s', "steel-furnace", smelterCategories, 2.0),
    ELECTRIC_FURNACE('e', "electric-furnace", smelterCategories, 2.0),
    SCIENCE_LAB(null, "lab", setOf(Category.SCIENCE_LAB)),
    NO_FACTORY(null, "compilatron-chest", setOf())
}

object Factories {

    private val allFactories = Factory.values().toSet()
    private val smelters = allFactories.filter { it.categories == smelterCategories }.toSet()
    private val assemblers =
        allFactories.filter { it.categories.contains(Category.CRAFTING) }.toSet()
    private val alwaysEnabledFactories = setOf(Factory.NO_FACTORY, Factory.SCIENCE_LAB)
    private val tech = allFactories - smelters - assemblers - alwaysEnabledFactories
    val defaultFactories =
        setOf(Factory.ASSEMBLING_MACHINE_3, Factory.ELECTRIC_FURNACE) +
            alwaysEnabledFactories +
            tech
    val selectableFactories = Factory.values().toList() - alwaysEnabledFactories
    private val factoriesByCode =
        Factory.values().filter { it.code != null }.associateBy { it.code }

    fun Set<Factory>.removeFactory(factory: Factory) =
        when (factory) {
            in smelters -> this - smelters - factory
            in assemblers -> this - assemblers - factory
            in alwaysEnabledFactories -> this
            else -> this - factory
        } + alwaysEnabledFactories

    fun factorySetFromUrl(s: String) =
        s.mapNotNull { factoriesByCode[it] }.fold(setOf(), Set<Factory>::addFactory)
}

fun Set<Factory>.addFactory(factory: Factory) = removeFactory(factory) + factory

fun Set<Factory>.toUrlParam() = sorted().mapNotNull { it.code }.joinToString("")
