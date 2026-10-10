package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.ParsedRecipe
import de.bjrke.factorio.optimizer.common.model.Recipe
import de.bjrke.factorio.optimizer.common.model.Surface
import de.bjrke.factorio.optimizer.common.model.forSurface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OptimizerTest {

    private val ironOre = Material("iron-ore")
    private val water = Material("water")
    private val ice = Material("ice")

    private fun recipe(
        name: String,
        category: Category,
        result: Map<Material, Double>,
        ingredients: Map<Material, Double> = emptyMap(),
        time: Double = 1.0,
    ) =
        Recipe(
            name = name,
            ingredients = ingredients,
            result = result,
            time = time,
            category = category,
        )

    private val miningIron = recipe("iron-ore", Category.MINING_BASIC_SOLID, mapOf(ironOre to 1.0))
    private val pumpWater = recipe("water", Category.OFFSHORE_PUMPING, mapOf(water to 1.0))
    private val meltIce =
        recipe(
            "ice-melting",
            Category.CHEMISTRY,
            mapOf(water to 20.0),
            ingredients = mapOf(ice to 1.0),
        )

    private fun factoryOf(target: Material, recipes: List<Recipe>, factories: Set<Factory>) =
        Optimizer(ParsedRecipe(emptyList(), recipes), factories)[target]
            .factories
            .getValue(target)
            .factory

    @Test
    fun miningDrillIsUsedForResource() =
        assertEquals(
            Factory.ELECTRIC_MINING_DRILL,
            factoryOf(
                ironOre,
                listOf(miningIron),
                setOf(
                    Factory.ELECTRIC_MINING_DRILL,
                    Factory.NO_FACTORY,
                    Factory.SCIENCE_LAB,
                ),
            ),
        )

    @Test
    fun freeSourceWhenNoDrillSelected() =
        assertEquals(
            Factory.NO_FACTORY,
            factoryOf(
                ironOre,
                listOf(miningIron),
                setOf(Factory.NO_FACTORY, Factory.SCIENCE_LAB),
            ),
        )

    @Test
    fun extractionIsPreferredOverProcessing() =
        assertEquals(
            Factory.OFFSHORE_PUMP,
            factoryOf(
                water,
                // sorted by name like RecipeProvider does: ice-melting before water
                listOf(meltIce, pumpWater),
                setOf(
                    Factory.OFFSHORE_PUMP,
                    Factory.CHEMICAL_PLANT,
                    Factory.NO_FACTORY,
                    Factory.SCIENCE_LAB,
                ),
            ),
        )

    @Test
    fun processingIsUsedWhenNoExtractionMachine() =
        assertEquals(
            Factory.CHEMICAL_PLANT,
            factoryOf(
                water,
                listOf(meltIce, pumpWater),
                setOf(Factory.CHEMICAL_PLANT, Factory.NO_FACTORY, Factory.SCIENCE_LAB),
            ),
        )

    private val moltenIron = Material("molten-iron")
    private val steelPlate = Material("steel-plate")
    private val lava = Material("lava")
    private val calcite = Material("calcite")

    private val pumpLava = recipe("lava", Category.OFFSHORE_PUMPING, mapOf(lava to 1.0))
    private val mineCalcite = recipe("calcite", Category.MINING_BASIC_SOLID, mapOf(calcite to 1.0))
    private val fromLava =
        recipe(
            "molten-iron-from-lava",
            Category.METALLURGY,
            mapOf(moltenIron to 1.0),
            ingredients = mapOf(lava to 500.0, calcite to 1.0),
        )
    private val fromOre =
        recipe(
            "iron-ore-melting",
            Category.METALLURGY,
            mapOf(moltenIron to 1.0),
            ingredients = mapOf(ironOre to 50.0, calcite to 1.0),
        )
    private val castingSteel =
        recipe(
            "casting-steel",
            Category.METALLURGY,
            mapOf(steelPlate to 1.0),
            ingredients = mapOf(moltenIron to 100.0),
        )

    private val vulcanusFactories =
        setOf(
            Factory.FOUNDRY,
            Factory.OFFSHORE_PUMP,
            Factory.ELECTRIC_MINING_DRILL,
            Factory.NO_FACTORY,
            Factory.SCIENCE_LAB,
        )

    @Test
    fun avoidsIngredientsFromFreeSource() {
        // iron-ore has no producer here (not on Vulcanus) so it would come from a chest;
        // lava & calcite are extractable, so the lava recipe should win
        val result =
            Optimizer(
                ParsedRecipe(emptyList(), listOf(fromOre, fromLava, pumpLava, mineCalcite)),
                vulcanusFactories,
            )[moltenIron]

        assertEquals("molten-iron-from-lava", result.recipe.name)
    }

    @Test
    fun vulcanusSteelUsesLavaNotChestOre() {
        val result =
            Optimizer(
                ParsedRecipe(
                    emptyList(),
                    listOf(castingSteel, fromOre, fromLava, pumpLava, mineCalcite),
                ),
                vulcanusFactories,
            )[steelPlate]

        assertEquals("casting-steel", result.recipe.name)
        assertTrue(result.factories.values.none { it.factory == Factory.NO_FACTORY })
        assertTrue(result.factories.values.any { it.recipe.name == "molten-iron-from-lava" })
    }

    @Test
    fun glebaIronAndCopperOreComeFromBoxesNotBiochamber() {
        // Gleba has no ore patches and no recipe turning bacteria into ore, so iron/copper ore
        // must resolve to a free source (box), never a biochamber recipe.
        val optimizer =
            Optimizer(RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.GLEBA))

        for (ore in listOf("iron-ore", "copper-ore")) {
            val material = Material(ore)
            val production = optimizer[material].factories.getValue(material)
            assertEquals(Factory.NO_FACTORY, production.factory, ore)
        }
    }

    @Test
    fun glebaFruitIsHarvestedByAgriculturalTower() {
        // yumako/jellynut are harvested from trees by the agricultural tower, not free boxes
        val optimizer =
            Optimizer(RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.GLEBA))

        for (fruit in listOf("yumako", "jellynut")) {
            val material = Material(fruit)
            val production = optimizer[material].factories.getValue(material)
            assertEquals(Factory.AGRICULTURAL_TOWER, production.factory, fruit)
            assertEquals(50.0, production.recipe.result.getValue(material), fruit)
        }
    }

    @Test
    fun oreBasedRecipeWinsWhenOreIsProducible() {
        // on Nauvis-like surface: iron-ore is mined, lava is not available (free source)
        val result =
            Optimizer(
                ParsedRecipe(emptyList(), listOf(fromOre, fromLava, mineCalcite, miningIron)),
                vulcanusFactories,
            )[moltenIron]

        assertEquals("iron-ore-melting", result.recipe.name)
    }
}
