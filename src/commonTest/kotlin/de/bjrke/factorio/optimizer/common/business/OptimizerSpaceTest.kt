package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.business.fixtures.production
import de.bjrke.factorio.optimizer.common.model.Factory
import de.bjrke.factorio.optimizer.common.model.FactoryConfig
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Surface
import de.bjrke.factorio.optimizer.common.model.forSurface
import de.bjrke.factorio.optimizer.common.service.Optimizer
import de.bjrke.factorio.optimizer.common.service.RecipeProvider
import de.bjrke.test.assertTable
import kotlin.test.Test
import kotlin.test.assertEquals

class OptimizerSpaceTest {

    private val asteroidChunks =
        listOf(
            "metallic-asteroid-chunk",
            "carbonic-asteroid-chunk",
            "oxide-asteroid-chunk",
            "promethium-asteroid-chunk",
        )

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
    fun biterEggsAreImportedInSpace() {
        // the captive biter spawner can only be built on Nauvis, so the biter eggs needed for
        // promethium science must be imported on space platforms
        val optimizer = Optimizer(RecipeProvider.forActiveMods().forSurface(Surface.SPACE))

        val eggs = Material("biter-egg")
        assertEquals(Factory.NO_FACTORY, optimizer[eggs].production(eggs).factory)
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
}
