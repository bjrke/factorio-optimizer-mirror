package de.bjrke.factorio.optimizer.common.model

import de.bjrke.factorio.optimizer.common.model.Factories.removeFactory

private val assembler1Categories = setOf(Category.CRAFTING, Category.ADVANCED_CRAFTING)
private val assembler2Categories = assembler1Categories.plus(Category.CRAFTING_WITH_FLUID)
private val smelterCategories = setOf(Category.SMELTING)

enum class Factory(
    val code: Char?,
    val identifier: String,
    val categories: Set<Category>,
    val speed: Double = 1.0,
) {
    ASSEMBLING_MACHINE_1('1', "assembling-machine-1", assembler1Categories, 0.5),
    ASSEMBLING_MACHINE_2('2', "assembling-machine-2", assembler2Categories, 0.75),
    ASSEMBLING_MACHINE_3('3', "assembling-machine-3", assembler2Categories, 1.25),
    OIL_REFINERY('o', "oil-refinery", setOf(Category.OIL_PROCESSING)),
    CHEMICAL_PLANT('c', "chemical-plant", setOf(Category.CHEMISTRY)),
    CENTRIFUGE('u', "centrifuge", setOf(Category.CENTRIFUGING)),
    ROCKET_SILO('R', "rocket-silo", setOf(Category.ROCKET_BUILDING)),
    STONE_FURNACE('f', "stone-furnace", smelterCategories),
    STEEL_FURNACE('s', "steel-furnace", smelterCategories, 2.0),
    ELECTRIC_FURNACE('e', "electric-furnace", smelterCategories, 2.0),
    BIOCHAMBER('b', "biochamber", setOf(Category.ORGANIC)),
    FOUNDRY('F', "foundry", setOf(Category.METALLURGY, Category.SMELTING), 2.0),
    CRYOGENIC_PLANT('C', "cryogenic-plant", setOf(Category.CRYOGENICS)),
    ELECTROMAGNETIC_PLANT('m', "electromagnetic-plant", setOf(Category.ELECTROMAGNETICS)),
    CRUSHER('h', "crusher", setOf(Category.CRUSHING)),
    RECYCLER('r', "recycler", setOf(Category.RECYCLING)),
    CAPTIVE_BITER_SPAWNER('p', "captive-biter-spawner", setOf(Category.CAPTIVE_SPAWNER_PROCESS)),
    BURNER_MINING_DRILL('B', "burner-mining-drill", setOf(Category.MINING_BASIC_SOLID), 0.25),
    ELECTRIC_MINING_DRILL('E', "electric-mining-drill", setOf(Category.MINING_BASIC_SOLID), 0.5),
    BIG_MINING_DRILL(
        'G',
        "big-mining-drill",
        setOf(Category.MINING_BASIC_SOLID, Category.MINING_HARD_SOLID),
        2.5,
    ),
    PUMPJACK(null, "pumpjack", setOf(Category.MINING_FLUID), 1.0),
    OFFSHORE_PUMP(null, "offshore-pump", setOf(Category.OFFSHORE_PUMPING), 1200.0),
    // speed is the number of trees a tower tends; one tree yields 50 fruit per 5 minute growth
    AGRICULTURAL_TOWER('A', "agricultural-tower", setOf(Category.HARVESTING), 48.0),
    SCIENCE_LAB(null, "lab", setOf(Category.SCIENCE_LAB)),
    NO_FACTORY(null, "infinity-chest", setOf()),
}

object Factories {

    private val allFactories = Factory.entries.toSet()
    private val smelters = allFactories.filter { it.categories == smelterCategories }.toSet()
    private val assemblers =
        allFactories.filter { it.categories.contains(Category.CRAFTING) }.toSet()
    private val miners =
        setOf(
            Factory.BURNER_MINING_DRILL,
            Factory.ELECTRIC_MINING_DRILL,
            Factory.BIG_MINING_DRILL,
        )
    private val alwaysEnabledFactories =
        setOf(Factory.NO_FACTORY, Factory.SCIENCE_LAB, Factory.PUMPJACK, Factory.OFFSHORE_PUMP)
    private val tech = allFactories - smelters - assemblers - miners - alwaysEnabledFactories
    val defaultFactories =
        setOf(
            Factory.ASSEMBLING_MACHINE_3,
            Factory.ELECTRIC_FURNACE,
            Factory.ELECTRIC_MINING_DRILL,
        ) + alwaysEnabledFactories + tech
    val selectableFactories = Factory.entries - alwaysEnabledFactories

    /** Mutually exclusive choices; rendered as radio buttons, exactly one stays selected. */
    val factoryGroups: List<List<Factory>> =
        listOf(assemblers, smelters, miners).map { it.sorted() }

    /** Independently toggled machines; rendered as checkboxes. */
    val singleFactories: List<Factory> =
        selectableFactories.filter { factory -> factoryGroups.none { factory in it } }.sorted()

    private val factoriesByCode = Factory.entries.filter { it.code != null }.associateBy { it.code }

    fun Set<Factory>.removeFactory(factory: Factory) =
        when (factory) {
            in smelters -> this - smelters - factory
            in assemblers -> this - assemblers - factory
            in miners -> this - miners - factory
            in alwaysEnabledFactories -> this
            else -> this - factory
        } + alwaysEnabledFactories

    fun factorySetFromUrl(s: String) =
        s.mapNotNull { factoriesByCode[it] }.fold(setOf(), Set<Factory>::addFactory)
}

fun Set<Factory>.addFactory(factory: Factory) = removeFactory(factory) + factory

fun Set<Factory>.toUrlParam() = sorted().mapNotNull { it.code }.joinToString("")
