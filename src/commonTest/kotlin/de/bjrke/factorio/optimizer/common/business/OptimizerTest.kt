package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.test.assertAll
import de.bjrke.test.assertTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OptimizerTest {

    private val ironOre = Material("iron-ore")
    private val water = Material("water")
    private val ice = Material("ice")

    private val oreSpoilingBacteria =
        mapOf("iron-ore" to "iron-bacteria", "copper-ore" to "copper-bacteria")

    private val harvestedFruits = listOf("yumako", "jellynut")

    private val asteroidChunks =
        listOf(
            "metallic-asteroid-chunk",
            "carbonic-asteroid-chunk",
            "oxide-asteroid-chunk",
            "promethium-asteroid-chunk",
        )

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
            categories = setOf(category),
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
        Optimizer(ParsedRecipe(emptyList(), recipes), factories)[target].production(target).factory

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

        assertAll(
            { assertEquals("casting-steel", result.recipe.name) },
            { assertTrue(result.factories.values.none { it.factory == Factory.NO_FACTORY }) },
            {
                assertTrue(
                    result.factories.values.any { it.recipe.name == "molten-iron-from-lava" }
                )
            },
        )
    }

    @Test
    fun glebaIronAndCopperOreSpoilFromBacteria() {
        // Gleba has no ore patches, but iron/copper bacteria spoil into their ore, so the ore must
        // resolve to the spoiling machine instead of a free source (box).
        val optimizer =
            Optimizer(RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.GLEBA))

        assertTable(oreSpoilingBacteria.keys) { ore ->
            val material = Material(ore)
            assertEquals(
                Factory.SPOILING,
                optimizer[material].production(material).factory,
                ore,
            )
        }
    }

    @Test
    fun glebaIronAndCopperOreUseBacteriaSpoilingRecipe() {
        val optimizer =
            Optimizer(RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.GLEBA))

        assertTable(oreSpoilingBacteria.entries) { (ore, bacteria) ->
            val material = Material(ore)
            assertEquals(
                "$bacteria-spoiling",
                optimizer[material].production(material).recipe.name,
                ore,
            )
        }
    }

    @Test
    fun nauvisOreStillComesFromMiningDrill() {
        // the spoiling recipe must not shadow mining where ore patches exist
        val optimizer =
            Optimizer(RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.NAUVIS))

        val material = Material("iron-ore")
        val production = optimizer[material].production(material)
        assertAll(
            { assertEquals(Factory.BIG_MINING_DRILL, production.factory) },
            { assertEquals("iron-ore", production.recipe.name) },
        )
    }

    @Test
    fun fulgoraIronOreComesFromRecyclingNotBacteria() {
        // Fulgora has no ore patches and no bacteria: iron ore is recovered by recycling concrete,
        // which is one of the scrap-recycling outputs
        val optimizer =
            Optimizer(RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.FULGORA))

        val material = Material("iron-ore")
        val result = optimizer[material]
        val production = result.production(material)
        assertAll(
            { assertEquals(Factory.RECYCLER, production.factory) },
            { assertEquals("concrete-recycling", production.recipe.name) },
            {
                assertTrue(
                    result.factories.values.none {
                        it.factory == Factory.SPOILING || it.factory == Factory.NO_FACTORY
                    }
                )
            },
        )
    }

    @Test
    fun fulgoraCopperOreIsImported() {
        // nothing on Fulgora recycles into copper ore, so it is imported instead of being spoiled
        // from imported bacteria
        val optimizer =
            Optimizer(RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.FULGORA))

        val material = Material("copper-ore")
        val production = optimizer[material].production(material)
        assertAll(
            { assertEquals(Factory.NO_FACTORY, production.factory) },
            { assertEquals("copper-ore", production.recipe.name) },
        )
    }

    @Test
    fun fulgoraScienceNeverSpoilsBacteria() {
        val optimizer =
            Optimizer(RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.FULGORA))

        val result = optimizer[Material(ALL_SCIENCE_PACKS)]
        assertAll(
            { assertTrue(result.factories.values.none { it.factory == Factory.SPOILING }) },
            { assertTrue(result.factories.values.none { it.recipe.name.endsWith("-spoiling") }) },
        )
    }

    @Test
    fun fulgoraScrapRecyclingConsumesWhatTheDrillProduces() {
        // Regression: the recycler's display used the curated Factory speed (1.0) instead of the
        // dump's crafting speed (0.5), so its scrap input showed twice the drill's scrap output.
        val optimizer =
            Optimizer(RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.FULGORA))

        val result = optimizer[Material(ALL_SCIENCE_PACKS)]
        val scrap = Material("scrap")
        val drill = result.factories.values.single { it.recipe.name == "scrap" }
        val recycler = result.factories.values.single { it.recipe.name == "scrap-recycling" }
        assertAll(
            { assertEquals(0.5, recycler.speed) },
            { assertEquals(drill.production(scrap), recycler.consumption(scrap), 1e-9) },
        )
    }

    @Test
    fun fulgoraAssemblingMachineRecyclesScrapOutputsInParallel() {
        // scrap recycling yields gears, batteries and processing units; all of them are recycled:
        // gear wheels and batteries into iron plates, processing units into electronic circuits.
        // Crafting a material that recycling already supplies (iron-plate, electronic-circuit,
        // copper-cable, ...) is not allowed.
        val optimizer =
            Optimizer(RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.FULGORA))

        val result = optimizer[Material("assembling-machine-1")]
        val recipes = result.factories.values.map { it.recipe.name }.toSet()
        val scrap = result.factories.values.single { it.recipe.name == "scrap" }
        assertAll(
            { assertEquals(Factory.BIG_MINING_DRILL, scrap.factory) },
            { assertTrue("iron-gear-wheel-recycling" in recipes) },
            { assertTrue("battery-recycling" in recipes) },
            { assertTrue("processing-unit-recycling" in recipes) },
            { assertTrue("electronic-circuit" !in recipes) },
            { assertTrue("iron-plate" !in recipes) },
            { assertTrue("copper-cable" !in recipes) },
            // far less scrap than the old single-recipe chain that needed 180 drills
            { assertEquals(20.909, scrap.amount, 1e-3) },
        )
    }

    @Test
    fun glebaSpoilageComesFromCheapOrganicByproductNotScience() {
        // spoilage is cheapest as a byproduct of the bacteria recipes, not by rotting raw fruit
        // (jellynut itself takes an hour) nor by spoiling expensive Gleba science packs.
        val optimizer =
            Optimizer(RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.GLEBA))

        val spoilage = Material("spoilage")
        val result = optimizer[spoilage]
        val production = result.production(spoilage)
        assertAll(
            { assertEquals("iron-bacteria", production.recipe.name) },
            { assertEquals(Factory.BIOCHAMBER, production.factory) },
            {
                assertTrue(
                    result.factories.values.none {
                        it.recipe.name == "agricultural-science-pack-spoiling"
                    }
                )
            },
        )
    }

    @Test
    fun glebaFruitIsHarvestedByAgriculturalTower() {
        // yumako/jellynut are harvested from trees by the agricultural tower, not free boxes
        val optimizer =
            Optimizer(RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.GLEBA))

        assertTable(harvestedFruits) { fruit ->
            val material = Material(fruit)
            assertEquals(
                Factory.AGRICULTURAL_TOWER,
                optimizer[material].production(material).factory,
                fruit,
            )
        }
    }

    @Test
    fun glebaFruitHarvestYieldsFifty() {
        val optimizer =
            Optimizer(RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.GLEBA))

        assertTable(harvestedFruits) { fruit ->
            val material = Material(fruit)
            val production = optimizer[material].production(material)
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
                FactoryConfig(spaceAge = false).defaults,
            )

        val space = Material("space-science-pack")
        assertEquals(Factory.ROCKET_SILO, optimizer[space].production(space).factory)
    }

    @Test
    fun spaceAgeSpaceScienceIsImportedOnNauvis() {
        val optimizer =
            Optimizer(
                RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.NAUVIS),
                FactoryConfig(spaceAge = true).defaults,
            )

        val space = Material("space-science-pack")
        assertEquals(Factory.NO_FACTORY, optimizer[space].production(space).factory)
    }

    @Test
    fun spaceAgeSpaceScienceIsAssembledInSpace() {
        val optimizer =
            Optimizer(
                RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.SPACE),
                FactoryConfig(spaceAge = true).defaults,
            )

        val space = Material("space-science-pack")
        // space science is a plain crafting recipe, so it uses the assembling machine
        assertEquals(
            Factory.ASSEMBLING_MACHINE_3,
            optimizer[space].production(space).factory,
        )
    }

    @Test
    fun spaceAgeQuantumProcessorIsAssembledInSpace() {
        // quantum-processor lives in the Aquilo subgroup, but its low-pressure condition also holds
        // on space platforms; the fluoroketone it needs is imported (barrels) there
        val optimizer =
            Optimizer(
                RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.SPACE),
                FactoryConfig(spaceAge = true).defaults,
            )

        val quantum = Material("quantum-processor")
        assertEquals(
            Factory.ELECTROMAGNETIC_PLANT,
            optimizer[quantum].production(quantum).factory,
        )
    }

    @Test
    fun spaceAgeElectronicCircuitUsesElectromagneticPlant() {
        // the data-updates add the electromagnetics category to electronic circuits, so the
        // electromagnetic plant (with its built-in productivity) is preferred over an assembler
        val optimizer =
            Optimizer(
                RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.NAUVIS),
                FactoryConfig(spaceAge = true).defaults,
            )

        val circuit = Material("electronic-circuit")
        assertEquals(
            Factory.ELECTROMAGNETIC_PLANT,
            optimizer[circuit].production(circuit).factory,
        )
    }

    @Test
    fun spaceAgeIronGearWheelStaysInAssembler() {
        // plain crafting recipes have no electromagnetics category, so the electromagnetic plant
        // must not be used for them
        val optimizer =
            Optimizer(
                RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.NAUVIS),
                FactoryConfig(spaceAge = true).defaults,
            )

        val gear = Material("iron-gear-wheel")
        assertEquals(
            Factory.ASSEMBLING_MACHINE_3,
            optimizer[gear].production(gear).factory,
        )
    }

    @Test
    fun spaceAgePlasticUsesCryogenicPlant() {
        // plastic gains the cryogenics category, so the cryogenic plant can run it
        val optimizer =
            Optimizer(
                RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.NAUVIS),
                FactoryConfig(spaceAge = true).defaults,
            )

        val plastic = Material("plastic-bar")
        assertEquals(
            Factory.CRYOGENIC_PLANT,
            optimizer[plastic].production(plastic).factory,
        )
    }

    @Test
    fun spaceAgeLubricantStaysInChemicalPlant() {
        // lubricant is pure chemistry, so the cryogenic plant must not be used for it
        val optimizer =
            Optimizer(
                RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.NAUVIS),
                FactoryConfig(spaceAge = true).defaults,
            )

        val lubricant = Material("lubricant")
        assertEquals(
            Factory.CHEMICAL_PLANT,
            optimizer[lubricant].production(lubricant).factory,
        )
    }

    @Test
    fun glebaSulfurUsesBiosulfur() {
        // crude oil does not exist on Gleba, so sulfur must come from the biosulfur recipe rather
        // than the petroleum-based chemical one
        val optimizer =
            Optimizer(
                RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.GLEBA),
                FactoryConfig(spaceAge = true).defaults,
            )

        val sulfur = Material("sulfur")
        val production = optimizer[sulfur].production(sulfur)
        assertAll(
            { assertEquals("biosulfur", production.recipe.name) },
            { assertEquals(Factory.BIOCHAMBER, production.factory) },
        )
    }

    @Test
    fun biterEggsAreImportedInSpace() {
        // the captive biter spawner can only be built on Nauvis, so the biter eggs needed for
        // promethium science must be imported on space platforms
        val optimizer =
            Optimizer(RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.SPACE))

        val eggs = Material("biter-egg")
        assertEquals(Factory.NO_FACTORY, optimizer[eggs].production(eggs).factory)
    }

    @Test
    fun biterEggsAreBredOnNauvis() {
        val optimizer =
            Optimizer(RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.NAUVIS))

        val eggs = Material("biter-egg")
        assertEquals(
            Factory.CAPTIVE_BITER_SPAWNER,
            optimizer[eggs].production(eggs).factory,
        )
    }

    @Test
    fun spaceAsteroidChunksComeFromCollectors() {
        val optimizer =
            Optimizer(
                RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.SPACE),
                FactoryConfig(spaceAge = true).defaults,
            )

        assertTable(asteroidChunks) { chunk ->
            val material = Material(chunk)
            assertEquals(
                Factory.ASTEROID_COLLECTOR,
                optimizer[material].production(material).factory,
                chunk,
            )
        }
    }

    @Test
    fun spaceAsteroidChunksUseCollectionRecipe() {
        val optimizer =
            Optimizer(
                RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.SPACE),
                FactoryConfig(spaceAge = true).defaults,
            )

        assertTable(asteroidChunks) { chunk ->
            val material = Material(chunk)
            assertEquals(
                "asteroid-collection",
                optimizer[material].production(material).recipe.name,
                chunk,
            )
        }
    }

    @Test
    fun spaceScienceNeedsOnlyOneAsteroidCollector() {
        // iron, carbon and ice all come from different chunks of the same collector, so the
        // collector must appear once instead of once per chunk
        val optimizer =
            Optimizer(
                RecipeProvider.forActiveMods(defaultMods).forSurface(Surface.SPACE),
                FactoryConfig(spaceAge = true).defaults,
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
                FactoryConfig(spaceAge = true).defaults,
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
                FactoryConfig(spaceAge = true).defaults,
            )

        val chunk = Material("metallic-asteroid-chunk")
        assertEquals(Factory.NO_FACTORY, optimizer[chunk].production(chunk).factory)
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
        assertAll(
            { assertEquals(1, byproducts.size) },
            // one machine covers both outputs: max, not sum
            { assertEquals(0.8, byproducts.single().amount, 1e-9) },
        )
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
        assertEquals(2.5, smelting.amount, 1e-9)
    }

    private fun multiCategoryRecipe(
        name: String,
        categories: Set<Category>,
        result: Material,
    ) =
        Recipe(
            name = name,
            ingredients = emptyMap(),
            result = mapOf(result to 1.0),
            time = 1.0,
            categories = categories,
        )

    @Test
    fun biochamberIsPreferredOverAssembler() {
        val product = Material("synthetic-organic-product")
        assertEquals(
            Factory.BIOCHAMBER,
            factoryOf(
                product,
                listOf(
                    multiCategoryRecipe(
                        "organic-recipe",
                        setOf(
                            Category.ORGANIC,
                            Category.CRAFTING,
                        ),
                        product,
                    )
                ),
                setOf(
                    Factory.BIOCHAMBER,
                    Factory.ASSEMBLING_MACHINE_3,
                    Factory.NO_FACTORY,
                    Factory.SCIENCE_LAB,
                ),
            ),
        )
    }

    @Test
    fun cryogenicPlantIsPreferredOverChemicalPlant() {
        val product = Material("synthetic-cold-product")
        assertEquals(
            Factory.CRYOGENIC_PLANT,
            factoryOf(
                product,
                listOf(
                    multiCategoryRecipe(
                        "cold-recipe",
                        setOf(
                            Category.CHEMISTRY,
                            Category.CRYOGENICS,
                        ),
                        product,
                    )
                ),
                setOf(
                    Factory.CRYOGENIC_PLANT,
                    Factory.CHEMICAL_PLANT,
                    Factory.NO_FACTORY,
                    Factory.SCIENCE_LAB,
                ),
            ),
        )
    }

    @Test
    fun electromagneticPlantIsNotUsedForCrafting() {
        // the electromagnetic plant only runs electromagnetics recipes, not plain crafting ones
        val product = Material("synthetic-circuit")
        assertEquals(
            Factory.ASSEMBLING_MACHINE_3,
            factoryOf(
                product,
                listOf(recipe("circuit", Category.CRAFTING, mapOf(product to 1.0))),
                setOf(
                    Factory.ELECTROMAGNETIC_PLANT,
                    Factory.ASSEMBLING_MACHINE_3,
                    Factory.NO_FACTORY,
                    Factory.SCIENCE_LAB,
                ),
            ),
        )
    }

    @Test
    fun cryogenicPlantIsNotUsedForChemistry() {
        // the cryogenic plant only runs cryogenics recipes, not plain chemistry ones (e.g. plastic)
        val product = Material("synthetic-plastic")
        assertEquals(
            Factory.CHEMICAL_PLANT,
            factoryOf(
                product,
                listOf(recipe("plastic", Category.CHEMISTRY, mapOf(product to 1.0))),
                setOf(
                    Factory.CRYOGENIC_PLANT,
                    Factory.CHEMICAL_PLANT,
                    Factory.NO_FACTORY,
                    Factory.SCIENCE_LAB,
                ),
            ),
        )
    }

    @Test
    fun builtInProductivityBoostsOutputWithoutExtraInput() {
        val ore = Material("synthetic-em-ore")
        val plate = Material("synthetic-em-plate")
        val emRecipe =
            Recipe(
                name = "em-recipe",
                ingredients = mapOf(ore to 1.0),
                result = mapOf(plate to 1.0),
                time = 1.0,
                categories = setOf(Category.ELECTROMAGNETICS),
            )
        val optimizer =
            Optimizer(
                ParsedRecipe(emptyList(), listOf(emRecipe)),
                setOf(
                    Factory.ELECTROMAGNETIC_PLANT,
                    Factory.NO_FACTORY,
                    Factory.SCIENCE_LAB,
                ),
            )

        val production = optimizer[plate].production(plate)
        assertAll(
            { assertEquals(Factory.ELECTROMAGNETIC_PLANT, production.factory) },
            // one machine produces 2 * 1.5 = 3/s, so 1/s needs 1/3 of a machine
            { assertEquals(1.0 / 3.0, production.amount, 1e-9) },
            { assertEquals(1.0, production.production(plate), 1e-9) },
            // the bonus does not change the input consumption
            { assertEquals(2.0 / 3.0, production.consumption(ore), 1e-9) },
        )
    }
}
