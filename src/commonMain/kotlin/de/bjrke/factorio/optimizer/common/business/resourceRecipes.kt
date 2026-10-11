package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Recipe

/**
 * A map resource and the extraction recipe that yields it. Mining times, amounts and the required
 * fluid are hardcoded from the factorio prototypes: the raw prototype files build their resource
 * tables via helper functions (`resource(resource_parameters, ...)`, `resource_parameters.*`),
 * which the simple lua parser used for recipes cannot evaluate. Which machine/surface can extract
 * the resource is derived from [category] and the resource map in the model.
 */
private data class ResourceSource(
    val name: String,
    val category: Category,
    val time: Double,
    val result: String = name,
    val amount: Double = 1.0,
    val fluid: String? = null,
    val fluidAmount: Double = 1.0,
)

private val resourceSources =
    listOf(
        ResourceSource("iron-ore", Category.MINING_BASIC_SOLID, 1.0),
        ResourceSource("copper-ore", Category.MINING_BASIC_SOLID, 1.0),
        ResourceSource("coal", Category.MINING_BASIC_SOLID, 1.0),
        ResourceSource("stone", Category.MINING_BASIC_SOLID, 1.0),
        ResourceSource(
            name = "uranium-ore",
            category = Category.MINING_BASIC_SOLID,
            time = 2.0,
            fluid = "sulfuric-acid",
            fluidAmount = 10.0,
        ),
        ResourceSource("crude-oil", Category.MINING_FLUID, 1.0, amount = 10.0),
        ResourceSource("calcite", Category.MINING_BASIC_SOLID, 1.0),
        ResourceSource("tungsten-ore", Category.MINING_HARD_SOLID, 5.0),
        ResourceSource(
            "sulfuric-acid-geyser",
            Category.MINING_FLUID,
            1.0,
            result = "sulfuric-acid",
            amount = 10.0,
        ),
        ResourceSource("scrap", Category.MINING_BASIC_SOLID, 0.5),
        ResourceSource("lithium-brine", Category.MINING_FLUID, 1.0),
        ResourceSource("fluorine-vent", Category.MINING_FLUID, 1.0, result = "fluorine"),
        // agricultural tower: a fully grown tree yields 50 fruit every 5 minutes (300s)
        ResourceSource("yumako", Category.HARVESTING, 300.0, amount = 50.0),
        ResourceSource("jellynut", Category.HARVESTING, 300.0, amount = 50.0),
    )

/** The offshore pump extracts the fluid of its "ocean" tile: water, heavy oil, lava or solution. */
private val offshorePumpFluids = listOf("water", "heavy-oil", "lava", "ammoniacal-solution")

/**
 * Asteroid chunks are collected by the asteroid collector in space. The collector prototype has a
 * 0.2s mining time; the in-game chunk mix depends on where the platform is, so all chunk types are
 * assumed to drop at the same rate. Modelled as one recipe so a single collector serves every
 * chunk.
 */
private val asteroidChunks =
    listOf(
        "metallic-asteroid-chunk",
        "carbonic-asteroid-chunk",
        "oxide-asteroid-chunk",
        "promethium-asteroid-chunk",
    )

private fun asteroidCollectionRecipes(material: (String) -> Material) =
    listOf(
        Recipe(
            name = "asteroid-collection",
            ingredients = emptyMap(),
            result = asteroidChunks.associate { material(it) to 1.0 },
            time = 0.2,
            category = Category.ASTEROID_COLLECTION,
        )
    )

private fun extractionRecipe(
    name: String,
    result: Material,
    category: Category,
    time: Double,
    amount: Double = 1.0,
    ingredients: Map<Material, Double> = emptyMap(),
) =
    Recipe(
        name = name,
        ingredients = ingredients,
        result = mapOf(result to amount),
        time = time,
        category = category,
    )

/** Hardcoded extraction recipes for map resources and offshore pump fluids. */
fun resourceRecipes(material: (String) -> Material): List<Recipe> =
    resourceSources.map {
        extractionRecipe(
            name = it.name,
            result = material(it.result),
            category = it.category,
            time = it.time,
            amount = it.amount,
            ingredients =
                it.fluid?.let { fluid -> mapOf(material(fluid) to it.fluidAmount) } ?: emptyMap(),
        )
    } +
        offshorePumpFluids.map {
            extractionRecipe(
                name = it,
                result = material(it),
                category = Category.OFFSHORE_PUMPING,
                time = 1.0,
            )
        } +
        asteroidCollectionRecipes(material)
