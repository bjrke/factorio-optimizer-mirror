package de.bjrke.factorio.optimizer.common.optimize

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.assemblingMachine1
import de.bjrke.factorio.optimizer.common.fixtures.biterEgg
import de.bjrke.factorio.optimizer.common.fixtures.carbon
import de.bjrke.factorio.optimizer.common.fixtures.coal
import de.bjrke.factorio.optimizer.common.fixtures.copperBacteria
import de.bjrke.factorio.optimizer.common.fixtures.copperOre
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuit
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironBacteria
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.jellynut
import de.bjrke.factorio.optimizer.common.fixtures.lubricant
import de.bjrke.factorio.optimizer.common.fixtures.metallicAsteroidChunk
import de.bjrke.factorio.optimizer.common.fixtures.nutrients
import de.bjrke.factorio.optimizer.common.fixtures.plasticBar
import de.bjrke.factorio.optimizer.common.fixtures.production
import de.bjrke.factorio.optimizer.common.fixtures.rawFish
import de.bjrke.factorio.optimizer.common.fixtures.scrap
import de.bjrke.factorio.optimizer.common.fixtures.solidFuel
import de.bjrke.factorio.optimizer.common.fixtures.spaceScience
import de.bjrke.factorio.optimizer.common.fixtures.spoilage
import de.bjrke.factorio.optimizer.common.fixtures.sulfur
import de.bjrke.factorio.optimizer.common.fixtures.uranium235
import de.bjrke.factorio.optimizer.common.fixtures.wood
import de.bjrke.factorio.optimizer.common.fixtures.yumako
import de.bjrke.factorio.optimizer.common.game.production.ALL_SCIENCE_PACKS
import de.bjrke.factorio.optimizer.common.game.production.RecipeLoader
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.FactoryConfig
import de.bjrke.factorio.optimizer.common.game.prototype.MAX_INTERMEDIATE_LIMIT
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.factorio.optimizer.common.game.prototype.forSurface
import de.bjrke.test.TOLERANCE
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import de.bjrke.test.assertTable
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OptimizerSurfaceTest {

    private val oreSpoilingBacteria = mapOf(ironOre to ironBacteria, copperOre to copperBacteria)

    private val harvestedFruits = listOf(yumako, jellynut)

    @Test
    fun glebaIronAndCopperOreSpoilFromBacteria() {
        // Gleba has no ore patches, but iron/copper bacteria spoil into their ore, so the ore must
        // resolve to the spoiling machine instead of a free source (box).
        val optimizer = Optimizer(RecipeLoader.forActiveMods().forSurface(Surface.GLEBA))

        assertTable(oreSpoilingBacteria.keys) { ore ->
            assertEquals(Factory.SPOILING, optimizer[ore].production(ore).factory)
        }
    }

    @Test
    fun glebaIronAndCopperOreUseBacteriaSpoilingRecipe() {
        val optimizer = Optimizer(RecipeLoader.forActiveMods().forSurface(Surface.GLEBA))

        assertTable(oreSpoilingBacteria.entries) { (ore, bacteria) ->
            assertEquals("${bacteria.name}-spoiling", optimizer[ore].production(ore).recipe.name)
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
                assertEquals(
                    emptyList(),
                    result.factories.values.filter {
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
            {
                assertEquals(
                    emptyList(),
                    result.factories.values.filter { it.factory == Factory.SPOILING },
                )
            },
            {
                assertEquals(
                    emptyList(),
                    result.factories.values.filter { it.recipe.name.endsWith("-spoiling") },
                )
            },
        )
    }

    @Test
    fun fulgoraScrapRecyclingConsumesWhatTheDrillProduces() {
        // Regression: the recycler's display used the curated Factory speed (1.0) instead of the
        // dump's crafting speed (0.5), so its scrap input showed twice the drill's scrap output.
        val optimizer =
            Optimizer(
                RecipeLoader.forActiveMods().forSurface(Surface.FULGORA),
                maxIntermediates = MAX_INTERMEDIATE_LIMIT,
            )

        val result = optimizer[Material(ALL_SCIENCE_PACKS)]
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

        val result = optimizer[assemblingMachine1]
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

        val result = optimizer[spoilage]
        val production = result.production(spoilage)
        assertAll(
            { assertEquals("iron-bacteria", production.recipe.name) },
            { assertEquals(Factory.BIOCHAMBER, production.factory) },
            {
                assertEquals(
                    emptyList(),
                    result.factories.values.filter {
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
            assertEquals(
                Factory.AGRICULTURAL_TOWER,
                optimizer[fruit].production(fruit).factory,
            )
        }
    }

    @Test
    fun glebaFruitHarvestYieldsFifty() {
        val optimizer = Optimizer(RecipeLoader.forActiveMods().forSurface(Surface.GLEBA))

        assertTable(harvestedFruits) { fruit ->
            val production = optimizer[fruit].production(fruit)
            assertEquals(50.0, production.recipe.result.getValue(fruit))
        }
    }

    @Test
    fun nauvisWoodIsHarvestedByAgriculturalTower() {
        val optimizer = Optimizer(RecipeLoader.forActiveMods().forSurface(Surface.NAUVIS))
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

        assertEquals(Factory.ROCKET_SILO, optimizer[spaceScience].production(spaceScience).factory)
    }

    @Test
    fun spaceAgeSpaceScienceIsImportedOnNauvis() {
        val optimizer =
            Optimizer(
                RecipeLoader.forActiveMods().forSurface(Surface.NAUVIS),
                FactoryConfig(spaceAge = true).defaults,
            )

        assertEquals(Factory.NO_FACTORY, optimizer[spaceScience].production(spaceScience).factory)
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

        assertEquals(
            Factory.ELECTROMAGNETIC_PLANT,
            optimizer[electronicCircuit].production(electronicCircuit).factory,
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

        assertEquals(
            Factory.CRYOGENIC_PLANT,
            optimizer[plasticBar].production(plasticBar).factory,
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

        val production = optimizer[sulfur].production(sulfur)
        assertAll(
            { assertEquals("biosulfur", production.recipe.name) },
            { assertEquals(Factory.BIOCHAMBER, production.factory) },
        )
    }

    @Test
    fun fulgoraSolidFuelComesFromScrapRecyclingWhenItCan() {
        // scrap recycling yields solid fuel as a byproduct; the LP prefers it over the heavy-oil
        // fallback, which stays available for plans that need more than the byproduct.
        val optimizer = Optimizer(RecipeLoader.forActiveMods().forSurface(Surface.FULGORA))

        val result = optimizer[solidFuel]

        assertEquals("scrap-recycling", result.production(solidFuel).recipe.name)
    }

    @Test
    fun solidFuelUsesTheSurfaceRecipe() =
        assertTable(
            listOf(
                Surface.NAUVIS to "solid-fuel-from-light-oil",
                Surface.FULGORA to "solid-fuel-from-heavy-oil",
                Surface.AQUILO to "solid-fuel-from-ammonia",
            ),
        ) { (surface, expected) ->
            // Fulgora's local resource is heavy oil; the other oil recipes only exist to keep the
            // LP from splitting solid fuel between producers.
            val parsed = RecipeLoader.forActiveMods().forSurface(surface)
            val solidFuelRecipes = parsed.recipes.filter { it.name.startsWith("solid-fuel-from-") }
            val result = Optimizer(parsed.copy(recipes = solidFuelRecipes))[solidFuel]

            assertEquals(expected, result.recipe.name, "surface $surface")
        }

    @Test
    fun biterEggsAreBredOnNauvis() {
        val optimizer = Optimizer(RecipeLoader.forActiveMods().forSurface(Surface.NAUVIS))

        assertEquals(
            Factory.CAPTIVE_BITER_SPAWNER,
            optimizer[biterEgg].production(biterEgg).factory,
        )
    }

    @Test
    fun asteroidChunkIsImportedOnNauvis() {
        val optimizer =
            Optimizer(
                RecipeLoader.forActiveMods().forSurface(Surface.NAUVIS),
                FactoryConfig(spaceAge = true).defaults,
            )

        assertEquals(
            Factory.NO_FACTORY,
            optimizer[metallicAsteroidChunk].production(metallicAsteroidChunk).factory,
        )
    }

    @Test
    fun bannedRecipesAreNeverPlanned() {
        assertTable(
            listOf(
                Triple(Surface.NAUVIS, coal, "coal-synthesis"),
                Triple(Surface.FULGORA, carbon, "carbon"),
                Triple(Surface.NAUVIS, rawFish, "fish-breeding"),
                Triple(Surface.NAUVIS, nutrients, "nutrients-from-fish"),
                Triple(Surface.NAUVIS, uranium235, "kovarex-enrichment-process"),
            ),
        ) { (surface, target, banned) ->
            val result = Optimizer(RecipeLoader.forActiveMods().forSurface(surface))[target]
            // the import pseudo recipe is named after its material, so filter it out by category
            val planned =
                result.factories.keys.filter { Category.IMPORT !in it.categories }.map { it.name }
            assertContainsNot(planned, banned)
        }
    }

    @Test
    fun plasticPlansStayCycleFreeOnEverySurface() =
        assertTable(Surface.entries) { surface ->
            // plastic is made from coal on the coal surfaces, so this exercises the coal/carbon
            // policy (Gleba uses bioplastic and Fulgora recycles it instead)
            val result = Optimizer(RecipeLoader.forActiveMods().forSurface(surface))[plasticBar]
            val planned =
                result.factories.keys.filter { Category.IMPORT !in it.categories }.map { it.name }
            assertAll(
                {
                    assertContains(
                        result.production(plasticBar).recipe.result,
                        plasticBar,
                        "$surface",
                    )
                },
                {
                    assertTrue(
                        "carbon" !in planned || "coal-synthesis" !in planned,
                        "$surface plans the coal/carbon cycle: $planned",
                    )
                },
            )
        }
}
