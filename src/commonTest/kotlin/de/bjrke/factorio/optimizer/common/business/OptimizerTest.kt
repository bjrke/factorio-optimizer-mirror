package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.FactoryConfig
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
    fun glebaIronAndCopperOreRotFromBacteria() {
        // Gleba has no ore patches, but iron/copper bacteria rot into their ore, so the ore must
        // resolve to the rotting machine instead of a free source (box).
        val optimizer =
            Optimizer(RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.GLEBA))

        val expected = mapOf("iron-ore" to "iron-bacteria", "copper-ore" to "copper-bacteria")
        for ((ore, bacteria) in expected) {
            val material = Material(ore)
            val production = optimizer[material].factories.getValue(material)
            assertEquals(Factory.ROTTING, production.factory, ore)
            assertEquals("$bacteria-rotting", production.recipe.name, ore)
        }
    }

    @Test
    fun nauvisOreStillComesFromMiningDrill() {
        // the rotting recipe must not shadow mining where ore patches exist
        val optimizer =
            Optimizer(RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.NAUVIS))

        val material = Material("iron-ore")
        val production = optimizer[material].factories.getValue(material)
        assertEquals(Factory.ELECTRIC_MINING_DRILL, production.factory)
        assertEquals("iron-ore", production.recipe.name)
    }

    @Test
    fun glebaSpoilageComesFromRawFruitNotScience() {
        // spoilage is cheapest by letting raw harvested fruit rot, not by spoiling expensive
        // Gleba science packs.
        val optimizer =
            Optimizer(RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.GLEBA))

        val spoilage = Material("spoilage")
        val production = optimizer[spoilage].factories.getValue(spoilage)
        assertEquals(Factory.ROTTING, production.factory)
        assertEquals("jellynut-rotting", production.recipe.name)
        assertTrue(
            optimizer[spoilage].factories.values.none {
                it.recipe.name == "agricultural-science-pack-rotting"
            }
        )
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

    @Test
    fun baseGameSpaceScienceComesFromRocketSilo() {
        val optimizer =
            Optimizer(
                RecipeProvider.forActiveMods(setOf(BASE_MOD)).forSurface(Surface.NAUVIS),
                FactoryConfig(spaceAge = false, recycler = false).defaults,
            )

        val space = Material("space-science-pack")
        assertEquals(Factory.ROCKET_SILO, optimizer[space].factories.getValue(space).factory)
    }

    @Test
    fun spaceAgeSpaceScienceIsImportedOnNauvis() {
        val optimizer =
            Optimizer(
                RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.NAUVIS),
                FactoryConfig(spaceAge = true, recycler = true).defaults,
            )

        val space = Material("space-science-pack")
        assertEquals(Factory.NO_FACTORY, optimizer[space].factories.getValue(space).factory)
    }

    @Test
    fun spaceAgeSpaceScienceIsAssembledInSpace() {
        val optimizer =
            Optimizer(
                RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.SPACE),
                FactoryConfig(spaceAge = true, recycler = true).defaults,
            )

        val space = Material("space-science-pack")
        assertEquals(
            Factory.ASSEMBLING_MACHINE_3,
            optimizer[space].factories.getValue(space).factory,
        )
    }

    @Test
    fun spaceAgeQuantumProcessorIsAssembledInSpace() {
        // quantum-processor lives in the Aquilo subgroup, but its low-pressure condition also holds
        // on space platforms; the fluoroketone it needs is imported (barrels) there
        val optimizer =
            Optimizer(
                RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.SPACE),
                FactoryConfig(spaceAge = true, recycler = true).defaults,
            )

        val quantum = Material("quantum-processor")
        assertEquals(
            Factory.ELECTROMAGNETIC_PLANT,
            optimizer[quantum].factories.getValue(quantum).factory,
        )
    }

    @Test
    fun biterEggsAreImportedInSpace() {
        // the captive biter spawner can only be built on Nauvis, so the biter eggs needed for
        // promethium science must be imported on space platforms
        val optimizer =
            Optimizer(RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.SPACE))

        val eggs = Material("biter-egg")
        assertEquals(Factory.NO_FACTORY, optimizer[eggs].factories.getValue(eggs).factory)
    }

    @Test
    fun biterEggsAreBredOnNauvis() {
        val optimizer =
            Optimizer(RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.NAUVIS))

        val eggs = Material("biter-egg")
        assertEquals(
            Factory.CAPTIVE_BITER_SPAWNER,
            optimizer[eggs].factories.getValue(eggs).factory,
        )
    }

    @Test
    fun spaceAsteroidChunksComeFromCollectors() {
        val optimizer =
            Optimizer(
                RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.SPACE),
                FactoryConfig(spaceAge = true, recycler = true).defaults,
            )

        listOf(
                "metallic-asteroid-chunk",
                "carbonic-asteroid-chunk",
                "oxide-asteroid-chunk",
                "promethium-asteroid-chunk",
            )
            .forEach { chunk ->
                val material = Material(chunk)
                val production = optimizer[material].factories.getValue(material)
                assertEquals(Factory.ASTEROID_COLLECTOR, production.factory, chunk)
                assertEquals("asteroid-collection", production.recipe.name, chunk)
            }
    }

    @Test
    fun spaceScienceNeedsOnlyOneAsteroidCollector() {
        // iron, carbon and ice all come from different chunks of the same collector, so the
        // collector must appear once instead of once per chunk
        val optimizer =
            Optimizer(
                RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.SPACE),
                FactoryConfig(spaceAge = true, recycler = true).defaults,
            )

        val result = optimizer[Material("space-science-pack")]

        assertEquals(1, result.factories.values.count { it.recipe.name == "asteroid-collection" })
    }

    @Test
    fun byproductCrusherRunsOnceForIronAndCopper() {
        // advanced metallic crushing yields both iron and copper ore; it must run once, sized for
        // whichever output is needed most, not once per ore
        val optimizer =
            Optimizer(
                RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.SPACE),
                FactoryConfig(spaceAge = true, recycler = true).defaults,
            )

        val result = optimizer[Material("solar-panel")]

        assertEquals(
            1,
            result.factories.values.count {
                it.recipe.name == "advanced-metallic-asteroid-crushing"
            },
        )
    }

    @Test
    fun asteroidChunkIsImportedOnNauvis() {
        val optimizer =
            Optimizer(
                RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.NAUVIS),
                FactoryConfig(spaceAge = true, recycler = true).defaults,
            )

        val chunk = Material("metallic-asteroid-chunk")
        assertEquals(Factory.NO_FACTORY, optimizer[chunk].factories.getValue(chunk).factory)
    }

    @Test
    fun byproductRecipeRunsOnceForAllItsOutputs() {
        val a = Material("synthetic-a")
        val b = Material("synthetic-b")
        val product = Material("synthetic-product")
        val byproduct = recipe("byproduct", Category.CRAFTING, mapOf(a to 2.0, b to 1.0))
        val consumer =
            recipe(
                "consumer",
                Category.CRAFTING,
                mapOf(product to 1.0),
                ingredients = mapOf(a to 2.0, b to 1.0),
            )
        val optimizer =
            Optimizer(
                ParsedRecipe(emptyList(), listOf(byproduct, consumer)),
                setOf(Factory.ASSEMBLING_MACHINE_3, Factory.NO_FACTORY, Factory.SCIENCE_LAB),
            )

        val result = optimizer[product]

        val byproducts = result.factories.values.filter { it.recipe.name == "byproduct" }
        assertEquals(1, byproducts.size)
        // one machine covers both outputs: max, not sum
        assertEquals(0.8, byproducts.single().amount)
    }

    @Test
    fun sharedIntermediateMachinesAreSummed() {
        val ore = Material("synthetic-ore")
        val plate = Material("synthetic-plate")
        val x = Material("synthetic-x")
        val y = Material("synthetic-y")
        val product = Material("synthetic-shared-product")
        val mine = recipe("mine", Category.MINING_BASIC_SOLID, mapOf(ore to 1.0))
        val smelt =
            recipe("smelt", Category.SMELTING, mapOf(plate to 1.0), ingredients = mapOf(ore to 2.0))
        val makeX =
            recipe("make-x", Category.CRAFTING, mapOf(x to 1.0), ingredients = mapOf(plate to 2.0))
        val makeY =
            recipe("make-y", Category.CRAFTING, mapOf(y to 1.0), ingredients = mapOf(plate to 3.0))
        val assemble =
            recipe(
                "assemble",
                Category.CRAFTING,
                mapOf(product to 1.0),
                ingredients = mapOf(x to 1.0, y to 1.0),
            )
        val optimizer =
            Optimizer(
                ParsedRecipe(emptyList(), listOf(mine, smelt, makeX, makeY, assemble)),
                setOf(
                    Factory.ASSEMBLING_MACHINE_3,
                    Factory.ELECTRIC_FURNACE,
                    Factory.ELECTRIC_MINING_DRILL,
                    Factory.NO_FACTORY,
                    Factory.SCIENCE_LAB,
                ),
            )

        val result = optimizer[product]

        val smelting = result.factories.values.single { it.recipe.name == "smelt" }
        // demand(plate) = 2 * make-x + 3 * make-y, not the max of the two consumers
        assertEquals(2.5, smelting.amount)
    }
}
