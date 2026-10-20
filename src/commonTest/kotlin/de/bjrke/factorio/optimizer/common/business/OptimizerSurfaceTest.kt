package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import de.bjrke.test.assertTable
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OptimizerSurfaceTest {

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

    @Test
    fun glebaIronAndCopperOreSpoilFromBacteria() {
        // Gleba has no ore patches, but iron/copper bacteria spoil into their ore, so the ore must
        // resolve to the spoiling machine instead of a free source (box).
        val optimizer = Optimizer(RecipeProvider.forActiveMods().forSurface(Surface.GLEBA))

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
        val optimizer = Optimizer(RecipeProvider.forActiveMods().forSurface(Surface.GLEBA))

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
        val optimizer = Optimizer(RecipeProvider.forActiveMods().forSurface(Surface.NAUVIS))

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
        val optimizer = Optimizer(RecipeProvider.forActiveMods().forSurface(Surface.FULGORA))

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
                    },
                )
            },
        )
    }

    @Test
    fun fulgoraCopperOreIsImported() {
        // nothing on Fulgora recycles into copper ore, so it is imported instead of being spoiled
        // from imported bacteria
        val optimizer = Optimizer(RecipeProvider.forActiveMods().forSurface(Surface.FULGORA))

        val material = Material("copper-ore")
        val production = optimizer[material].production(material)
        assertAll(
            { assertEquals(Factory.NO_FACTORY, production.factory) },
            { assertEquals("copper-ore", production.recipe.name) },
        )
    }

    @Test
    fun fulgoraScienceNeverSpoilsBacteria() {
        val optimizer = Optimizer(RecipeProvider.forActiveMods().forSurface(Surface.FULGORA))

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
        val optimizer = Optimizer(RecipeProvider.forActiveMods().forSurface(Surface.FULGORA))

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
        val optimizer = Optimizer(RecipeProvider.forActiveMods().forSurface(Surface.FULGORA))

        val result = optimizer[Material("assembling-machine-1")]
        val recipes = result.factories.values.map { it.recipe.name }.toSet()
        val scrap = result.factories.values.single { it.recipe.name == "scrap" }
        assertAll(
            { assertEquals(Factory.BIG_MINING_DRILL, scrap.factory) },
            { assertContains(recipes, "iron-gear-wheel-recycling") },
            { assertContains(recipes, "battery-recycling") },
            { assertContains(recipes, "processing-unit-recycling") },
            { assertContainsNot(recipes, "electronic-circuit") },
            { assertContainsNot(recipes, "iron-plate") },
            { assertContainsNot(recipes, "copper-cable") },
            // far less scrap than the old single-recipe chain that needed 180 drills
            { assertEquals(20.909, scrap.amount, 1e-3) },
        )
    }

    @Test
    fun glebaSpoilageComesFromCheapOrganicByproductNotScience() {
        // spoilage is cheapest as a byproduct of the bacteria recipes, not by rotting raw fruit
        // (jellynut itself takes an hour) nor by spoiling expensive Gleba science packs.
        val optimizer = Optimizer(RecipeProvider.forActiveMods().forSurface(Surface.GLEBA))

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
                    },
                )
            },
        )
    }

    @Test
    fun glebaFruitIsHarvestedByAgriculturalTower() {
        // yumako/jellynut are harvested from trees by the agricultural tower, not free boxes
        val optimizer = Optimizer(RecipeProvider.forActiveMods().forSurface(Surface.GLEBA))

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
        val optimizer = Optimizer(RecipeProvider.forActiveMods().forSurface(Surface.GLEBA))

        assertTable(harvestedFruits) { fruit ->
            val material = Material(fruit)
            val production = optimizer[material].production(material)
            assertEquals(50.0, production.recipe.result.getValue(material), fruit)
        }
    }

    @Test
    fun nauvisWoodIsHarvestedByAgriculturalTower() {
        val optimizer = Optimizer(RecipeProvider.forActiveMods().forSurface(Surface.NAUVIS))
        val wood = Material("wood")
        val actual = optimizer[wood].production(wood).factory
        assertEquals(Factory.AGRICULTURAL_TOWER, actual)
    }

    @Test
    fun baseGameSpaceScienceComesFromRocketSilo() {
        val optimizer =
            Optimizer(
                RecipeProvider.forActiveMods(false).forSurface(Surface.NAUVIS),
                FactoryConfig(spaceAge = false).defaults,
            )

        val space = Material("space-science-pack")
        assertEquals(Factory.ROCKET_SILO, optimizer[space].production(space).factory)
    }

    @Test
    fun spaceAgeSpaceScienceIsImportedOnNauvis() {
        val optimizer =
            Optimizer(
                RecipeProvider.forActiveMods().forSurface(Surface.NAUVIS),
                FactoryConfig(spaceAge = true).defaults,
            )

        val space = Material("space-science-pack")
        assertEquals(Factory.NO_FACTORY, optimizer[space].production(space).factory)
    }

    @Test
    fun spaceAgeSpaceScienceIsAssembledInSpace() {
        val optimizer =
            Optimizer(
                RecipeProvider.forActiveMods().forSurface(Surface.SPACE),
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
                RecipeProvider.forActiveMods().forSurface(Surface.SPACE),
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
                RecipeProvider.forActiveMods().forSurface(Surface.NAUVIS),
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
                RecipeProvider.forActiveMods().forSurface(Surface.NAUVIS),
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
                RecipeProvider.forActiveMods().forSurface(Surface.NAUVIS),
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
                RecipeProvider.forActiveMods().forSurface(Surface.NAUVIS),
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
                RecipeProvider.forActiveMods().forSurface(Surface.GLEBA),
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
        val optimizer = Optimizer(RecipeProvider.forActiveMods().forSurface(Surface.SPACE))

        val eggs = Material("biter-egg")
        assertEquals(Factory.NO_FACTORY, optimizer[eggs].production(eggs).factory)
    }

    @Test
    fun biterEggsAreBredOnNauvis() {
        val optimizer = Optimizer(RecipeProvider.forActiveMods().forSurface(Surface.NAUVIS))

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
                RecipeProvider.forActiveMods().forSurface(Surface.SPACE),
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
                RecipeProvider.forActiveMods().forSurface(Surface.SPACE),
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
                RecipeProvider.forActiveMods().forSurface(Surface.SPACE),
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
                RecipeProvider.forActiveMods().forSurface(Surface.SPACE),
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
                RecipeProvider.forActiveMods().forSurface(Surface.NAUVIS),
                FactoryConfig(spaceAge = true).defaults,
            )

        val chunk = Material("metallic-asteroid-chunk")
        assertEquals(Factory.NO_FACTORY, optimizer[chunk].production(chunk).factory)
    }
}
