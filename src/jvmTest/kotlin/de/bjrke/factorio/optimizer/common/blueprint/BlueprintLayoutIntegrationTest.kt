package de.bjrke.factorio.optimizer.common.blueprint

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.assertConnectedUndergroundBelts
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.assertNoDanglingInserter
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.assertNoMergingBelts
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.assertNoOverlap
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.assertOneBeltSourcePerMaterial
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.assertSplittersOnlyFeedSurface
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.assertTunnelEndsAreFed
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.assertUndergroundRunsAreStretched
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.layoutFromUrl
import de.bjrke.factorio.optimizer.common.fixtures.recipes
import de.bjrke.factorio.optimizer.common.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.layout.entity.InfinityChestEntity
import de.bjrke.factorio.optimizer.common.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.manhattanDist
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Layout invariants on the real prototype dump: overlap, belts, tunnels and compaction. */
class BlueprintLayoutIntegrationTest {

    @Test
    fun realBlueprintsHaveNoOverlappingEntities() =
        assertTableOf(
            "advanced-circuit",
            "electronic-circuit",
            "logistic-science-pack",
            "processing-unit",
            "utility-science-pack",
            "lubricant",
        ) {
            assertNoOverlap(Layout(it).blueprint.entities)
        }

    @Test
    fun realCheatBlueprintsHaveNoOverlappingEntities() =
        assertTableOf(
            "advanced-circuit",
            "electronic-circuit",
            "utility-science-pack",
            "processing-unit",
        ) {
            assertNoOverlap(Layout(it, beltEnd = BeltEnd.INFINITY_CHEST).blueprint.entities)
        }

    @Test
    fun theCheatFluidImportIsAnInfinityPipeAtTheFluidsDefaultTemperature() {
        val entities =
            Layout("processing-unit", beltEnd = BeltEnd.INFINITY_CHEST).blueprint.entities
        val pipe = entities.singleOrNull { it.name == "infinity-pipe" }

        assertNotNull(pipe) { "no infinity pipe for the imported sulfuric acid" }
        assertAll(
            { assertEquals(Material("sulfuric-acid"), pipe.infinityPipe?.fluid) },
            { assertEquals(25, pipe.infinityPipe?.temperature) },
        )
    }

    @Test
    fun realBlueprintsHaveNoDanglingInserter() =
        assertTableOf(
            "advanced-circuit",
            "electronic-circuit",
            "logistic-science-pack",
            "processing-unit",
            "utility-science-pack",
        ) {
            assertNoDanglingInserter(Layout(it).blueprint.entities)
        }

    @Test
    fun realBlueprintsPutEveryMaterialOnAtMostOneBelt() =
        assertTableOf(
            "advanced-circuit",
            "electronic-circuit",
            "logistic-science-pack",
            "processing-unit",
            "utility-science-pack",
            "lubricant",
        ) {
            assertOneBeltSourcePerMaterial(Layout(it).plan)
        }

    @Test
    fun realBlueprintsHaveConnectedUndergroundBelts() =
        assertTableOf(
            "advanced-circuit",
            "electronic-circuit",
            "logistic-science-pack",
            "processing-unit",
            "utility-science-pack",
        ) {
            assertConnectedUndergroundBelts(Layout(it).blueprint.entities)
        }

    @Test
    fun splitterBranchesStartOnTheSurface() {
        val blueprintEntities =
            layoutFromUrl("http://localhost:8080/#/4.5/advanced-circuit/CFSXbmr/1/1")
                .blueprint
                .entities

        assertAll(
            { assertSplittersOnlyFeedSurface(blueprintEntities) },
            { assertConnectedUndergroundBelts(blueprintEntities) },
        )
    }

    /**
     * Smoke test for the weights: this layout is small enough to stay entirely on the surface, so
     * any underground belt means a cost or lane-direction regression, not a genuine obstacle.
     */
    @Test
    fun compactionHalvesTheAdvancedCircuitLayout() {
        val layout = Layout(target = "advanced-circuit")
        val width =
            layout.entities.maxOf { it.area.bottomRight.x } -
                layout.entities.minOf { it.area.topLeft.x } + 1
        val height =
            layout.entities.maxOf { it.area.bottomRight.y } -
                layout.entities.minOf { it.area.topLeft.y } + 1

        assertAll(
            { assertTrue(width < 40, "layout is still $width tiles wide") },
            { assertTrue(height < 80, "layout is still $height tiles tall") },
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
        )
    }

    @Test
    fun reportedUrlKeepsInputLanesAtTheMachineEdge() =
        assertTableOf(BeltEnd.CONSTANT_COMBINATOR, BeltEnd.INFINITY_CHEST) { beltEnd ->
            val layout =
                layoutFromUrl("http://localhost:8080/#/4.5/advanced-circuit/CFSXbmr/1/1")
                    .copy(beltEnd = beltEnd)
            val entities = layout.entities
            val recipes = layout.plan.recipes.associate { it.recipe.name to it.recipe }
            val belts = entities.filterIsInstance<BeltEntity>()

            val missing =
                entities.filterIsInstance<MachineEntity>().mapNotNull { machine ->
                    val recipe = recipes[machine.recipe] ?: return@mapNotNull null
                    val solids =
                        recipe.ingredients.keys.filterNot(layout.logistics::isFluid).toSet()
                    if (solids.isEmpty()) return@mapNotNull null
                    val laneRows =
                        setOf(
                            machine.area.topLeft.y - 2,
                            machine.area.topLeft.y - 3,
                            machine.area.bottomRight.y + 2,
                            machine.area.bottomRight.y + 3,
                        )
                    val reachesEdge = belts.any {
                        it.area.topLeft.x == machine.area.topLeft.x &&
                            it.area.topLeft.y in laneRows &&
                            it.material in solids
                    }
                    if (reachesEdge) null else "${machine.recipe}@${machine.area.topLeft}"
                }

            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                {
                    assertEquals(
                        emptyList(),
                        missing,
                        "input lanes do not reach the machine edge",
                    )
                },
            )
        }

    /**
     * The reported advanced-circuit URL must still replay without a single failed feed. It is built
     * as a direct-feed band now, which puts the two cable rows in the feed corridor west of the
     * machines; only the band's own copper-cable feed needs to tunnel there, every other material
     * still reaches its lane on the surface.
     */
    @Test
    fun advancedCircuitUrlOnlyTunnelsForItsCableFeed() {
        val layout = layoutFromUrl("http://localhost:8080/#/4.5/advanced-circuit/CFSXbmr/1/1")

        // the import connectors are fixed marker pairs; only route tunnels are checked here
        val routeTunnels =
            layout.entities.filterIsInstance<UndergroundBeltEntity>().filter { tunnel ->
                layout.entities.none {
                    it is MarkerEntity && it.area.topLeft.manhattanDist(tunnel.area.topLeft) == 1
                }
            }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            {
                assertEquals(
                    listOf(Material("copper-cable")),
                    routeTunnels.map { it.material }.distinct(),
                    "only the band's cable feed may need an underground belt",
                )
            },
        )
    }

    /**
     * The imported uranium-238 must reach its lane directly: no turn and no tunnel. Routing imports
     * before the produced deliveries makes this the default, and the trimmer can then move all
     * markers to one column close to the factory edge.
     */
    @Test
    fun reportedUraniumRoundsUrlKeepsItsImportedInputStraight() {
        val layout =
            layoutFromUrl("http://localhost:8080/#/0.3/uranium-rounds-magazine/CFSXbmr/1/1")
        val uranium = Material("uranium-238")
        val belts = layout.entities.filterIsInstance<BeltEntity>().filter { it.material == uranium }
        val tunnels =
            layout.entities.filterIsInstance<UndergroundBeltEntity>().filter {
                it.material == uranium
            }
        val inputChests =
            layout.entities.filterIsInstance<InfinityChestEntity>().filter {
                it.settings.item != null
            }
        val machineLeft =
            layout.entities.filterIsInstance<MachineEntity>().minOf {
                it.area.topLeft.x
            }
        val uraniumChest = inputChests.single { it.settings.item == uranium }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), tunnels, "the uranium-238 import tunnels") },
            { assertTrue(belts.isNotEmpty(), "no uranium-238 belt was placed") },
            {
                assertEquals(
                    1,
                    belts.map { it.area.topLeft.y }.distinct().size,
                    "the uranium-238 import is not one straight row",
                )
            },
            { assertTrue(belts.all { it.direction == EAST }, "the uranium-238 import turns") },
            {
                assertEquals(
                    1,
                    inputChests.map { it.area.topLeft.x }.distinct().size,
                    "the import markers do not share one column",
                )
            },
            {
                assertTrue(
                    machineLeft - uraniumChest.area.topLeft.x <= 6,
                    "the import prefix was not trimmed",
                )
            },
        )
    }

    /**
     * The long straight piercing-rounds run must not survive compaction: a corridor that only fails
     * after stretching is ruled out by its seam and the pass continues with the next one.
     */
    @Test
    fun reportedUraniumRoundsUrlIsFullyCompactedAndFed() {
        val layout =
            layoutFromUrl("http://localhost:8080/#/0.3/uranium-rounds-magazine/CFSXbmr/1/1")
        val blueprintEntities = layout.blueprint.entities
        val width =
            layout.entities.maxOf { it.area.bottomRight.x } -
                layout.entities.minOf { it.area.topLeft.x } + 1
        val height =
            layout.entities.maxOf { it.area.bottomRight.y } -
                layout.entities.minOf { it.area.topLeft.y } + 1

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
            { assertTrue(width <= 20, "layout is still $width tiles wide") },
            {
                assertTrue(
                    height <= 32,
                    "the long piercing-rounds belt was not collapsed: layout is $height rows tall",
                )
            },
            { assertConnectedUndergroundBelts(blueprintEntities) },
            { assertTunnelEndsAreFed(blueprintEntities) },
        )
    }

    @Test
    fun realBlueprintsStretchUndergroundRuns() =
        assertTableOf("advanced-circuit", "electronic-circuit", "processing-unit") { target ->
            val layout = Layout(target)
            assertUndergroundRunsAreStretched(
                layout.blueprint.entities,
                layout.beltMaterials,
            )
        }

    /** Every input belt must reach its lane; a failure means the machine is starved. */
    @Test
    fun realBlueprintsFeedEveryInput() =
        assertTableOf(
            "advanced-circuit",
            "electronic-circuit",
            "logistic-science-pack",
            "processing-unit",
            "copper-cable",
            "iron-gear-wheel",
            "utility-science-pack",
        ) {
            assertEquals(emptyList(), Layout(it).failures)
        }

    @Test
    fun realBlueprintsDoNotMergeMaterials() =
        assertTableOf("inserter", "advanced-circuit", "electronic-circuit") { target ->
            val layout = Layout(target)
            assertNoMergingBelts(
                layout.blueprint.entities,
                layout.beltMaterials,
            )
        }
}
