package de.bjrke.factorio.optimizer.common.blueprint

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.assertConnectedUndergroundBelts
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.assertNoDanglingInserter
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.assertNoMergingBelts
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.assertNoOverlap
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.assertOneBeltSourcePerMaterial
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.assertSplittersOnlyFeedSurface
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.assertUndergroundRunsAreStretched
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.entitiesFor
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.feedFailuresFor
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.planAndLogistics
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.planFor
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.planFromUrl
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.transportMaterialsFor
import de.bjrke.factorio.optimizer.common.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.layout.validation.validateLayout
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
            assertNoOverlap(entitiesFor(it))
        }

    @Test
    fun realCheatBlueprintsHaveNoOverlappingEntities() =
        assertTableOf(
            "advanced-circuit",
            "electronic-circuit",
            "utility-science-pack",
            "processing-unit",
        ) {
            assertNoOverlap(entitiesFor(it, beltEnd = BeltEnd.INFINITY_CHEST))
        }

    @Test
    fun theCheatFluidImportIsAnInfinityPipeAtTheFluidsDefaultTemperature() {
        val entities = entitiesFor("processing-unit", BeltEnd.INFINITY_CHEST)
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
            assertNoDanglingInserter(entitiesFor(it))
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
            assertOneBeltSourcePerMaterial(planFor(it))
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
            assertConnectedUndergroundBelts(entitiesFor(it))
        }

    @Test
    fun splitterBranchesStartOnTheSurface() {
        val (plan, logistics, beltEnd) =
            planFromUrl("http://localhost:8080/#/4.5/advanced-circuit/CFSXbmr/1/1")
        val entities = fromPlaced(plan, logistics, beltEnd).entities

        assertAll(
            { assertSplittersOnlyFeedSurface(entities) },
            { assertConnectedUndergroundBelts(entities) },
        )
    }

    /**
     * Smoke test for the weights: this layout is small enough to stay entirely on the surface, so
     * any underground belt means a cost or lane-direction regression, not a genuine obstacle.
     */
    @Test
    fun compactionHalvesTheAdvancedCircuitLayout() {
        val (plan, logistics) = planAndLogistics("advanced-circuit")
        val dump = blueprintLayoutDump(plan, logistics)
        val width =
            dump.entities.maxOf { it.area.bottomRight.x } -
                dump.entities.minOf { it.area.topLeft.x } + 1
        val height =
            dump.entities.maxOf { it.area.bottomRight.y } -
                dump.entities.minOf { it.area.topLeft.y } + 1

        assertAll(
            { assertTrue(width < 40, "layout is still $width tiles wide") },
            { assertTrue(height < 80, "layout is still $height tiles tall") },
            { assertEquals(emptyList(), dump.failures) },
            { assertEquals(emptyList(), validateLayout(dump.entities)) },
        )
    }

    @Test
    fun reportedUrlKeepsInputLanesAtTheMachineEdge() =
        assertTableOf(BeltEnd.CONSTANT_COMBINATOR, BeltEnd.INFINITY_CHEST) { beltEnd ->
            val (plan, logistics, _) =
                planFromUrl("http://localhost:8080/#/4.5/advanced-circuit/CFSXbmr/1/1")
            val dump = blueprintLayoutDump(plan, logistics, beltEnd)
            val entities = dump.entities
            val recipes = plan.recipes.associate { it.recipe.name to it.recipe }
            val belts = entities.filterIsInstance<BeltEntity>()

            val missing =
                entities.filterIsInstance<MachineEntity>().mapNotNull { machine ->
                    val recipe = recipes[machine.recipe] ?: return@mapNotNull null
                    val solids = recipe.ingredients.keys.filterNot(logistics::isFluid).toSet()
                    if (solids.isEmpty()) return@mapNotNull null
                    val laneRows =
                        setOf(
                            machine.area.topLeft.y - 2,
                            machine.area.topLeft.y - 3,
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
                { assertEquals(emptyList(), dump.failures) },
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
        val (plan, logistics, beltEnd) =
            planFromUrl("http://localhost:8080/#/4.5/advanced-circuit/CFSXbmr/1/1")
        val dump = blueprintLayoutDump(plan, logistics, beltEnd)
        // the import connectors are fixed marker pairs; only route tunnels are checked here
        val routeTunnels =
            dump.entities.filterIsInstance<UndergroundBeltEntity>().filter { tunnel ->
                dump.entities.none {
                    it is MarkerEntity && it.area.topLeft.manhattanDist(tunnel.area.topLeft) == 1
                }
            }

        assertAll(
            { assertEquals(emptyList(), dump.failures) },
            {
                assertEquals(
                    listOf(Material("copper-cable")),
                    routeTunnels.map { it.material }.distinct(),
                    "only the band's cable feed may need an underground belt",
                )
            },
        )
    }

    @Test
    fun realBlueprintsStretchUndergroundRuns() =
        assertTableOf("advanced-circuit", "electronic-circuit", "processing-unit") { target ->
            assertUndergroundRunsAreStretched(
                entitiesFor(target),
                transportMaterialsFor(target),
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
            "utility-science-pack",
            "copper-cable",
            "iron-gear-wheel",
        ) {
            assertEquals(emptyList(), feedFailuresFor(it))
        }

    @Test
    fun realBlueprintsDoNotMergeMaterials() =
        assertTableOf("inserter", "advanced-circuit", "electronic-circuit") { target ->
            assertNoMergingBelts(entitiesFor(target), transportMaterialsFor(target))
        }
}
