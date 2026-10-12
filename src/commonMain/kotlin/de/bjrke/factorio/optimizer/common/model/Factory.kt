package de.bjrke.factorio.optimizer.common.model

private val assembler1Categories = setOf(Category.CRAFTING, Category.ADVANCED_CRAFTING)
private val assembler2Categories = assembler1Categories.plus(Category.CRAFTING_WITH_FLUID)
private val smelterCategories = setOf(Category.SMELTING)

/**
 * A machine that can run recipes. Some machines only exist with a certain mod active
 * ([spaceAgeOnly], [baseOnly]), which [FactoryConfig] uses to gate what the user can pick.
 *
 * [productivity] is the machine's built-in productivity bonus (its output is multiplied by `1 +
 * productivity` without consuming more ingredients). [preferred] marks the Space Age speciality
 * machines that should be used whenever they can run a recipe, even without a bonus (the cryogenic
 * plant). These values are curated fallbacks; the optimizer prefers the stats derived from the
 * prototype dump (see [MachineStats]).
 */
enum class Factory(
    val code: Char?,
    val identifier: String,
    val categories: Set<Category>,
    val speed: Double = 1.0,
    val productivity: Double = 0.0,
    val preferred: Boolean = false,
    val spaceAgeOnly: Boolean = false,
    val baseOnly: Boolean = false,
) {
    ASSEMBLING_MACHINE_1('1', "assembling-machine-1", assembler1Categories, 0.5),
    ASSEMBLING_MACHINE_2('2', "assembling-machine-2", assembler2Categories, 0.75),
    ASSEMBLING_MACHINE_3('3', "assembling-machine-3", assembler2Categories, 1.25),
    OIL_REFINERY(null, "oil-refinery", setOf(Category.OIL_PROCESSING)),
    CHEMICAL_PLANT(null, "chemical-plant", setOf(Category.CHEMISTRY)),
    CENTRIFUGE(null, "centrifuge", setOf(Category.CENTRIFUGING)),
    // the rocket silo only builds space science in the base game, so it is always on there
    ROCKET_SILO(null, "rocket-silo", setOf(Category.ROCKET_BUILDING), baseOnly = true),
    STONE_FURNACE('f', "stone-furnace", smelterCategories),
    STEEL_FURNACE('s', "steel-furnace", smelterCategories, 2.0),
    ELECTRIC_FURNACE('e', "electric-furnace", smelterCategories, 2.0),
    BIOCHAMBER(
        'b',
        "biochamber",
        setOf(Category.ORGANIC),
        2.0,
        productivity = 0.5,
        preferred = true,
        spaceAgeOnly = true,
    ),
    FOUNDRY(
        'F',
        "foundry",
        setOf(Category.METALLURGY),
        4.0,
        productivity = 0.5,
        preferred = true,
        spaceAgeOnly = true,
    ),
    CRYOGENIC_PLANT(
        'C',
        "cryogenic-plant",
        setOf(Category.CRYOGENICS),
        2.0,
        preferred = true,
        spaceAgeOnly = true,
    ),
    ELECTROMAGNETIC_PLANT(
        'm',
        "electromagnetic-plant",
        setOf(Category.ELECTROMAGNETICS),
        2.0,
        productivity = 0.5,
        preferred = true,
        spaceAgeOnly = true,
    ),
    CRUSHER(null, "crusher", setOf(Category.CRUSHING), spaceAgeOnly = true),
    ASTEROID_COLLECTOR(
        null,
        "asteroid-collector",
        setOf(Category.ASTEROID_COLLECTION),
        spaceAgeOnly = true,
    ),
    RECYCLER('r', "recycler", setOf(Category.RECYCLING), spaceAgeOnly = true),
    CAPTIVE_BITER_SPAWNER(
        null,
        "captive-biter-spawner",
        setOf(Category.CAPTIVE_SPAWNER_PROCESS),
        spaceAgeOnly = true,
    ),
    BURNER_MINING_DRILL('B', "burner-mining-drill", setOf(Category.MINING_BASIC_SOLID), 0.25),
    ELECTRIC_MINING_DRILL('E', "electric-mining-drill", setOf(Category.MINING_BASIC_SOLID), 0.5),
    BIG_MINING_DRILL(
        'G',
        "big-mining-drill",
        setOf(Category.MINING_BASIC_SOLID, Category.MINING_HARD_SOLID),
        2.5,
        spaceAgeOnly = true,
    ),
    PUMPJACK(null, "pumpjack", setOf(Category.MINING_FLUID), 1.0),
    OFFSHORE_PUMP(null, "offshore-pump", setOf(Category.OFFSHORE_PUMPING), 1200.0),
    // speed is the number of trees a tower tends; one tree yields 50 fruit per 5 minute growth
    AGRICULTURAL_TOWER(
        null,
        "agricultural-tower",
        setOf(Category.HARVESTING),
        48.0,
        spaceAgeOnly = true,
    ),
    SCIENCE_LAB(null, "lab", setOf(Category.SCIENCE_LAB)),
    // spoilage only exists in Space Age
    ROTTING(null, "spoilage", setOf(Category.ROTTING), spaceAgeOnly = true),
    NO_FACTORY(null, "infinity-chest", setOf()),
}

/**
 * The machines that can be selected for a given mod set. Without Space Age the DLC-only machines
 * are hidden and the rocket silo stays; with it the silo is replaced by the Space Age recipes.
 */
class FactoryConfig(val spaceAge: Boolean = true) {

    val available =
        Factory.entries
            .filter {
                when {
                    it.baseOnly -> !spaceAge
                    it.spaceAgeOnly -> spaceAge
                    else -> true
                }
            }
            .toSet()

    private val smelters = available.filter { it.categories == smelterCategories }.toSet()
    private val assemblers = available.filter { it.categories.contains(Category.CRAFTING) }.toSet()
    private val miners = available.filter { it in minerFactories }.toSet()

    val alwaysEnabled = alwaysEnabledFactories intersect available

    private val tech = available - smelters - assemblers - miners - alwaysEnabled

    /** Mutually exclusive choices; rendered as radio buttons, exactly one stays selected. */
    val groups: List<List<Factory>> =
        listOf(assemblers, smelters, miners).filter { it.isNotEmpty() }.map { it.sorted() }

    private val groupDefaults =
        setOf(
            Factory.ASSEMBLING_MACHINE_3,
            Factory.ELECTRIC_FURNACE,
            if (spaceAge) Factory.BIG_MINING_DRILL else Factory.ELECTRIC_MINING_DRILL,
        ) intersect available

    val defaults = groupDefaults.toSet() + alwaysEnabled + tech

    /** Independently toggled machines; rendered as checkboxes. */
    val singles: List<Factory> =
        (available - alwaysEnabled).filter { factory -> groups.none { factory in it } }.sorted()

    fun add(current: Set<Factory>, factory: Factory): Set<Factory> =
        remove(current, factory) + factory

    fun remove(current: Set<Factory>, factory: Factory): Set<Factory> =
        when (factory) {
            in smelters -> current - smelters
            in assemblers -> current - assemblers
            in miners -> current - miners
            else -> current - factory
        } + alwaysEnabled

    /** Decodes a URL parameter, dropping machines that are not available for these mods. */
    fun fromUrl(s: String): Set<Factory> {
        val decoded = s.mapNotNull { factoriesByCode[it] }.filter { it in available }
        var result = decoded.fold(alwaysEnabled, ::add)
        for (group in groups) {
            if (group.none { it in result }) {
                result = add(result, group.first { it in defaults })
            }
        }
        return result
    }

    companion object {
        private val minerFactories =
            setOf(
                Factory.BURNER_MINING_DRILL,
                Factory.ELECTRIC_MINING_DRILL,
                Factory.BIG_MINING_DRILL,
            )
        private val alwaysEnabledFactories =
            setOf(
                Factory.NO_FACTORY,
                Factory.PUMPJACK,
                Factory.OFFSHORE_PUMP,
                Factory.ROTTING,
                Factory.OIL_REFINERY,
                Factory.CHEMICAL_PLANT,
                Factory.CRUSHER,
                Factory.ASTEROID_COLLECTOR,
                Factory.CENTRIFUGE,
                Factory.CAPTIVE_BITER_SPAWNER,
                Factory.AGRICULTURAL_TOWER,
                Factory.ROCKET_SILO,
                Factory.SCIENCE_LAB,
            )
        private val factoriesByCode =
            Factory.entries.filter { it.code != null }.associateBy { it.code }
    }
}

/**
 * Defaults for all machines (all mods active); used by the
 * [de.bjrke.factorio.optimizer.common.business.Optimizer]'s default.
 */
val defaultFactories: Set<Factory>
    get() = FactoryConfig().defaults

fun Set<Factory>.toUrlParam() = sorted().mapNotNull { it.code }.joinToString("")
