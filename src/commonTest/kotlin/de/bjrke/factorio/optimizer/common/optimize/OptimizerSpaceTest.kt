package de.bjrke.factorio.optimizer.common.optimize

import de.bjrke.factorio.optimizer.common.fixtures.biterEgg
import de.bjrke.factorio.optimizer.common.fixtures.carbonicAsteroidChunk
import de.bjrke.factorio.optimizer.common.fixtures.metallicAsteroidChunk
import de.bjrke.factorio.optimizer.common.fixtures.oxideAsteroidChunk
import de.bjrke.factorio.optimizer.common.fixtures.production
import de.bjrke.factorio.optimizer.common.fixtures.promethiumAsteroidChunk
import de.bjrke.factorio.optimizer.common.fixtures.quantumProcessor
import de.bjrke.factorio.optimizer.common.fixtures.solarPanel
import de.bjrke.factorio.optimizer.common.fixtures.spaceScience
import de.bjrke.factorio.optimizer.common.game.production.RecipeLoader
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.FactoryConfig
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.factorio.optimizer.common.game.selection.forSurface
import de.bjrke.test.assertTable
import kotlin.test.Test
import kotlin.test.assertEquals

class OptimizerSpaceTest {

    private val asteroidChunks =
        listOf(
            metallicAsteroidChunk,
            carbonicAsteroidChunk,
            oxideAsteroidChunk,
            promethiumAsteroidChunk,
        )

    @Test
    fun spaceAgeSpaceScienceIsAssembledInSpace() {
        val optimizer =
            Optimizer(
                RecipeLoader.forActiveMods().forSurface(Surface.SPACE),
                FactoryConfig(spaceAge = true).defaults,
            )

        // space science is a plain crafting recipe, so it uses the assembling machine
        assertEquals(
            Factory.ASSEMBLING_MACHINE_3,
            optimizer.compute(spaceScience).production(spaceScience).factory,
        )
    }

    @Test
    fun spaceAgeQuantumProcessorIsAssembledInSpace() {
        // quantum-processor lives in the Aquilo subgroup, but its low-pressure condition also holds
        // on space platforms; the fluoroketone it needs is imported (barrels) there
        val optimizer =
            Optimizer(
                RecipeLoader.forActiveMods().forSurface(Surface.SPACE),
                FactoryConfig(spaceAge = true).defaults,
            )

        assertEquals(
            Factory.ELECTROMAGNETIC_PLANT,
            optimizer.compute(quantumProcessor).production(quantumProcessor).factory,
        )
    }

    @Test
    fun biterEggsAreImportedInSpace() {
        // the captive biter spawner can only be built on Nauvis, so the biter eggs needed for
        // promethium science must be imported on space platforms
        val optimizer = Optimizer(RecipeLoader.forActiveMods().forSurface(Surface.SPACE))

        assertEquals(Factory.NO_FACTORY, optimizer.compute(biterEgg).production(biterEgg).factory)
    }

    @Test
    fun spaceAsteroidChunksComeFromCollectors() {
        val optimizer =
            Optimizer(
                RecipeLoader.forActiveMods().forSurface(Surface.SPACE),
                FactoryConfig(spaceAge = true).defaults,
            )

        assertTable(asteroidChunks) { chunk ->
            assertEquals(
                Factory.ASTEROID_COLLECTOR,
                optimizer.compute(chunk).production(chunk).factory,
            )
        }
    }

    @Test
    fun spaceAsteroidChunksUseCollectionRecipe() {
        val optimizer =
            Optimizer(
                RecipeLoader.forActiveMods().forSurface(Surface.SPACE),
                FactoryConfig(spaceAge = true).defaults,
            )

        assertTable(asteroidChunks) { chunk ->
            assertEquals(
                "asteroid-collection",
                optimizer.compute(chunk).production(chunk).recipe.name,
            )
        }
    }

    @Test
    fun spaceScienceNeedsOnlyOneAsteroidCollector() {
        // iron, carbon and ice all come from different chunks of the same collector, so the
        // collector must appear once instead of once per chunk
        val optimizer =
            Optimizer(
                RecipeLoader.forActiveMods().forSurface(Surface.SPACE),
                FactoryConfig(spaceAge = true).defaults,
            )

        val result = optimizer.compute(spaceScience)

        assertEquals(1, result.factories.values.count { it.recipe.name == "asteroid-collection" })
    }

    @Test
    fun byproductCrusherRunsOnceForIronAndCopper() {
        // advanced metallic crushing yields both iron and copper ore; it must run once, sized for
        // whichever output is needed most, not once per ore
        val optimizer =
            Optimizer(
                RecipeLoader.forActiveMods().forSurface(Surface.SPACE),
                FactoryConfig(spaceAge = true).defaults,
            )

        assertEquals(
            1,
            optimizer.compute(solarPanel).factories.values.count {
                it.recipe.name == "advanced-metallic-asteroid-crushing"
            },
        )
    }
}
