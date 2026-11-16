package de.bjrke.factorio.optimizer.common.game.production

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.prototype.BeltStats
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.PoleStats
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.factorio.optimizer.common.game.prototype.SurfaceCondition
import de.bjrke.factorio.optimizer.common.game.prototype.SurfaceProperty
import de.bjrke.test.TOLERANCE
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The values the shipped dump must supply. They pin the sanitizer: dropping one of the categories
 * the parser reads would turn these into missing-entry failures instead of a silently worse plan.
 */
class PrototypeDumpTest {

    @Test
    fun beltStatsComeFromTheDump() {
        val base = RecipeLoader.forActiveMods(spaceAge = false).logistics.belts
        val spaceAge = RecipeLoader.forActiveMods(spaceAge = true).logistics.belts

        assertAll(
            { assertEquals(BeltStats(0.03125, 5), base.getValue("transport-belt")) },
            { assertEquals(BeltStats(0.0625, 7), base.getValue("fast-transport-belt")) },
            { assertEquals(BeltStats(0.09375, 9), base.getValue("express-transport-belt")) },
            // the turbo belt only exists with Space Age
            { assertEquals(BeltStats(0.125, 11), spaceAge.getValue("turbo-transport-belt")) },
        )
    }

    @Test
    fun poleStatsComeFromTheDump() {
        val poles = RecipeLoader.forActiveMods(spaceAge = false).logistics.poles

        assertAll(
            {
                // supply_area_distance 2.5 covers a 5x5 area, i.e. two tiles in each direction
                assertEquals(PoleStats(2, 7.5), poles.getValue("small-electric-pole"))
            },
            { assertEquals(PoleStats(3, 9.0), poles.getValue("medium-electric-pole")) },
        )
    }

    @Test
    fun surfacePropertiesComeFromTheDump() {
        val properties = RecipeLoader.forActiveMods(spaceAge = true).surfaceProperties

        assertAll(
            {
                assertEquals(
                    800,
                    properties.getValue(Surface.FULGORA).getValue(SurfaceProperty.PRESSURE),
                )
            },
            {
                assertEquals(
                    8,
                    properties.getValue(Surface.FULGORA).getValue(SurfaceProperty.GRAVITY),
                )
            },
            {
                assertEquals(
                    15,
                    properties.getValue(Surface.AQUILO).getValue(SurfaceProperty.GRAVITY),
                )
            },
            {
                assertEquals(
                    0,
                    properties.getValue(Surface.SPACE).getValue(SurfaceProperty.PRESSURE),
                )
            },
        )
    }

    /** Fulgora's pressure is 800, so a `pressure 1000..1000` recipe cannot run there. */
    @Test
    fun nauvisPressureRecipesAreNotOfferedOnFulgora() {
        val names =
            RecipeLoader.forSurface(spaceAge = true, Surface.FULGORA)
                .recipes
                .map { it.name }
                .toSet()

        assertAll(
            { assertFalse("tree-seed" in names, "tree-seed needs pressure 1000") },
            { assertFalse("fish-breeding" in names, "fish-breeding needs pressure 1000") },
        )
    }

    /**
     * The base game's launch is the silo's whole cycle: 100 rocket parts of ten ingredients each
     * plus the satellite, yielding one launch's 1000 packs after 100 * 3 s of crafting. The launch
     * interval after the last part is ignored.
     */
    @Test
    fun theBaseLaunchIsOneSiloCycle() {
        val base = RecipeLoader.forActiveMods(spaceAge = false).recipes.associateBy { it.name }
        val launch = base.getValue("space-science-pack")

        assertAll(
            { assertEquals(1000.0, launch.ingredients.getValue(Material("processing-unit"))) },
            { assertEquals(1000.0, launch.ingredients.getValue(Material("rocket-fuel"))) },
            { assertEquals(1.0, launch.ingredients.getValue(Material("satellite"))) },
            { assertContainsNot(launch.ingredients.keys, Material("rocket-part")) },
            { assertEquals(300.0, launch.time, TOLERANCE) },
        )
    }

    /**
     * Space Age's launch cycle is the 50 parts one launch consumes: the dump's one-part recipe is
     * replaced, its 50 * 3 s of part crafting is the time, and the silo's pressure condition gates
     * it (a space platform cannot build the silo).
     */
    @Test
    fun theSpaceAgePartRecipeIsOneLaunchOfParts() {
        val parts =
            RecipeLoader.forActiveMods(spaceAge = true).recipes.single { it.name == "rocket-part" }

        assertAll(
            { assertEquals(50.0, parts.result.getValue(Material("rocket-part"))) },
            { assertEquals(50.0, parts.ingredients.getValue(Material("processing-unit"))) },
            { assertEquals(150.0, parts.time, TOLERANCE) },
            {
                assertEquals(
                    listOf(SurfaceCondition(SurfaceProperty.PRESSURE, 1..Int.MAX_VALUE)),
                    parts.surfaceConditions,
                )
            },
        )
    }

    @Test
    fun sciencePacksComeFromTheLab() {
        val base = RecipeLoader.forActiveMods(spaceAge = false).recipes.associateBy { it.name }
        val spaceAge = RecipeLoader.forActiveMods(spaceAge = true).recipes.associateBy { it.name }

        assertAll(
            { assertEquals(7, base.getValue(ALL_SCIENCE_PACKS).ingredients.size) },
            { assertEquals(12, spaceAge.getValue(ALL_SCIENCE_PACKS).ingredients.size) },
            {
                // the base game has no space-science-pack recipe, so the launch is synthesised
                assertEquals(
                    setOf(Category.ROCKET_BUILDING),
                    base.getValue("space-science-pack").categories,
                )
            },
            {
                // Space Age ships its own recipe, so it must not be replaced by the fallback
                assertTrue(
                    Category.ROCKET_BUILDING !in spaceAge.getValue("space-science-pack").categories,
                )
            },
        )
    }
}
