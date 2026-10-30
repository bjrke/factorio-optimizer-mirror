package de.bjrke.factorio.optimizer.common.service

import de.bjrke.factorio.optimizer.common.fixtures.copperOre
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.lubricant
import de.bjrke.factorio.optimizer.common.fixtures.production
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.production.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.production.RecipeLoader
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.prototype.FactoryConfig
import de.bjrke.factorio.optimizer.common.prototype.Surface
import de.bjrke.factorio.optimizer.common.prototype.selection.forSurface
import de.bjrke.test.TOLERANCE
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

    @Test
    fun glebaIronAndCopperOreSpoilFromBacteria() {
        // Gleba has no ore patches, but iron/copper bacteria spoil into their ore, so the ore must
        // resolve to the spoiling machine instead of a free source (box).
        val optimizer = Optimizer(RecipeLoader.forActiveMods().forSurface(Surface.GLEBA))

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
        val optimizer = Optimizer(RecipeLoader.forActiveMods().forSurface(Surface.GLEBA))

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
        val optimizer = Optimizer(RecipeLoader.forActiveMods().forSurface(Surface.NAUVIS))

        val production = optimizer[ironOre].production(ironOre)
        assertAll(
            { assertEquals(Factory.BIG_MINING_DRILL, production.factory) },
            { assertEquals("iron-ore", production.recipe.name) },
        )
    }

    @Test
    fun fulgoraIronOreComesFromRecyclingNotBacteria() {
        // Fulgora has no ore patches and no bacteria: iron ore is recovered by recycling concrete,
        // which is one of the scrap-recycling outputs
        val optimizer = Optimizer(RecipeLoader.forActiveMods().forSurface(Surface.FULGORA))

        val result = optimizer[ironOre]
        val production = result.production(ironOre)
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
        val optimizer = Optimizer(RecipeLoader.forActiveMods().forSurface(Surface.FULGORA))
        val production = optimizer[copperOre].production(copperOre)
        assertAll(
            { assertEquals(Factory.NO_FACTORY, production.factory) },
            { assertEquals("copper-ore", production.recipe.name) },
        )
    }

    @Test
    fun fulgoraScienceNeverSpoilsBacteria() {
        val optimizer = Optimizer(RecipeLoader.forActiveMods().forSurface(Surface.FULGORA))

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
        val optimizer = Optimizer(RecipeLoader.forActiveMods().forSurface(Surface.FULGORA))

        val result = optimizer[Material(ALL_SCIENCE_PACKS)]
        val scrap = Material("scrap")
        val drill = result.factories.values.single { it.recipe.name == "scrap" }
        val recycler = result.factories.values.single { it.recipe.name == "scrap-recycling" }
        assertAll(
            { assertEquals(0.5, recycler.speed) },
            { assertEquals(drill.production(scrap), recycler.consumption(scrap), TOLERANCE) },
        )
    }

    @Test
    fun fulgoraAssemblingMachineRecyclesScrapOutputsInParallel() {
        // scrap recycling yields gears, batteries and processing units; all of them are recycled:
        // gear wheels and batteries into iron plates, processing units into electronic circuits.
        // Crafting a material that recycling already supplies (iron-plate, electronic-circuit,
        // copper-cable, ...) is not allowed.
        val optimizer = Optimizer(RecipeLoader.forActiveMods().forSurface(Surface.FULGORA))

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
        val optimizer = Optimizer(RecipeLoader.forActiveMods().forSurface(Surface.GLEBA))

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
        val optimizer = Optimizer(RecipeLoader.forActiveMods().forSurface(Surface.GLEBA))

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
        val optimizer = Optimizer(RecipeLoader.forActiveMods().forSurface(Surface.GLEBA))

        assertTable(harvestedFruits) { fruit ->
            val material = Material(fruit)
            val production = optimizer[material].production(material)
            assertEquals(50.0, production.recipe.result.getValue(material), fruit)
        }
    }

    @Test
    fun nauvisWoodIsHarvestedByAgriculturalTower() {
        val optimizer = Optimizer(RecipeLoader.forActiveMods().forSurface(Surface.NAUVIS))
        val wood = Material("wood")
        val actual = optimizer[wood].production(wood).factory
        assertEquals(Factory.AGRICULTURAL_TOWER, actual)
    }

    @Test
    fun baseGameSpaceScienceComesFromRocketSilo() {
        val optimizer =
            Optimizer(
                RecipeLoader.forActiveMods(false).forSurface(Surface.NAUVIS),
                FactoryConfig(spaceAge = false).defaults,
            )

        val space = Material("space-science-pack")
        assertEquals(Factory.ROCKET_SILO, optimizer[space].production(space).factory)
    }

    @Test
    fun spaceAgeSpaceScienceIsImportedOnNauvis() {
        val optimizer =
            Optimizer(
                RecipeLoader.forActiveMods().forSurface(Surface.NAUVIS),
                FactoryConfig(spaceAge = true).defaults,
            )

        val space = Material("space-science-pack")
        assertEquals(Factory.NO_FACTORY, optimizer[space].production(space).factory)
    }

    @Test
    fun spaceAgeElectronicCircuitUsesElectromagneticPlant() {
        // the data-updates add the electromagnetics category to electronic circuits, so the
        // electromagnetic plant (with its built-in productivity) is preferred over an assembler
        val optimizer =
            Optimizer(
                RecipeLoader.forActiveMods().forSurface(Surface.NAUVIS),
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
                RecipeLoader.forActiveMods().forSurface(Surface.NAUVIS),
                FactoryConfig(spaceAge = true).defaults,
            )

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
                RecipeLoader.forActiveMods().forSurface(Surface.NAUVIS),
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
                RecipeLoader.forActiveMods().forSurface(Surface.NAUVIS),
                FactoryConfig(spaceAge = true).defaults,
            )

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
                RecipeLoader.forActiveMods().forSurface(Surface.GLEBA),
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
    fun biterEggsAreBredOnNauvis() {
        val optimizer = Optimizer(RecipeLoader.forActiveMods().forSurface(Surface.NAUVIS))

        val eggs = Material("biter-egg")
        assertEquals(
            Factory.CAPTIVE_BITER_SPAWNER,
            optimizer[eggs].production(eggs).factory,
        )
    }

    @Test
    fun asteroidChunkIsImportedOnNauvis() {
        val optimizer =
            Optimizer(
                RecipeLoader.forActiveMods().forSurface(Surface.NAUVIS),
                FactoryConfig(spaceAge = true).defaults,
            )

        val chunk = Material("metallic-asteroid-chunk")
        assertEquals(Factory.NO_FACTORY, optimizer[chunk].production(chunk).factory)
    }
}
