package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityChestEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.MAX_ROW_BELTS
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.beltRowBudget
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.oneLaneCapacity
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.rowInputs
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.rowOutputs
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.assertConnectedUndergroundBelts
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.assertNoDanglingInserter
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.assertNoMergingBelts
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.assertNoOverlap
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.assertOneBeltSourcePerMaterial
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.assertSplittersOnlyFeedSurface
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.assertTunnelEndsAreFed
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.assertUndergroundRunsAreStretched
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.layoutFromUrl
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.manhattanDist
import de.bjrke.factorio.optimizer.common.fixtures.copperCable
import de.bjrke.factorio.optimizer.common.fixtures.copperPlate
import de.bjrke.factorio.optimizer.common.fixtures.electricEngineUnit
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuit
import de.bjrke.factorio.optimizer.common.fixtures.recipes
import de.bjrke.factorio.optimizer.common.fixtures.sulfuricAcid
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertContains
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
            val layout = Layout(it)
            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertNoOverlap(layout.blueprint.entities) },
            )
        }

    @Test
    fun realCheatBlueprintsHaveNoOverlappingEntities() =
        assertTableOf(
            "advanced-circuit",
            "electronic-circuit",
            "utility-science-pack",
            "processing-unit",
        ) {
            val layout = Layout(it, beltEnd = BeltEnd.INFINITY_CHEST)
            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertNoOverlap(layout.blueprint.entities) },
            )
        }

    @Test
    fun theCheatFluidImportIsAnInfinityPipeAtTheFluidsDefaultTemperature() {
        // plastic is built from petroleum gas now, so the plan imports two fluids; pin the acid one
        val layout = Layout("processing-unit", beltEnd = BeltEnd.INFINITY_CHEST)
        val entities = layout.blueprint.entities
        val pipe = entities.single {
            it.name == "infinity-pipe" && it.infinityPipe?.fluid == sulfuricAcid
        }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(sulfuricAcid, pipe.infinityPipe?.fluid) },
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
            val layout = Layout(it)
            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertNoDanglingInserter(layout.blueprint.entities) },
            )
        }

    @Test
    fun realBlueprintsPutEveryMaterialOnAtMostOneBelt() =
        assertTableOf(
            "advanced-circuit",
            "electronic-circuit",
            "logistic-science-pack",
            "processing-unit",
            "lubricant",
            "utility-science-pack",
        ) {
            val layout = Layout(it)
            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertOneBeltSourcePerMaterial(layout.plan) },
            )
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
            val layout = Layout(it)
            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertConnectedUndergroundBelts(layout.blueprint.entities) },
            )
        }

    @Test
    fun splitterBranchesStartOnTheSurface() {
        val layout = layoutFromUrl("http://localhost:8080/#/4.5/advanced-circuit/CFSXbmr/1/1")
        val blueprintEntities = layout.blueprint.entities

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
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

    /**
     * The reported fusion-reactor layout was invalid before compaction: an underground entrance was
     * treated as feeding the belt above its tunnel span. Every corridor was then rejected, so the
     * plan kept its uncompacted 78x184 shape.
     */
    @Test
    fun theReportedFusionReactorUrlCompacts() {
        val layout =
            layoutFromUrl(
                "http://localhost:8080/#/0.041666666667/fusion-reactor-equipment/CFSXbmr/1/1",
            )
        val width =
            layout.entities.maxOf { it.area.bottomRight.x } -
                layout.entities.minOf { it.area.topLeft.x } + 1
        val height =
            layout.entities.maxOf { it.area.bottomRight.y } -
                layout.entities.minOf { it.area.topLeft.y } + 1

        assertAll(
            { assertTrue(width <= 50, "layout is still $width tiles wide") },
            { assertTrue(height <= 95, "layout is still $height rows tall") },
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
            { assertNoPlannedMaterialIsImported(layout.plan) },
        )
    }

    /**
     * The reported utility-science layout used to import battery, electric-engine-unit, LDS and
     * processing-unit while building them: the band search had put the same production into two
     * rows, so one row internalized the material and the other imported it. Its acid lane also
     * collided with the gear belt's mixed merge and was dropped, leaving the batteries dry.
     */
    @Test
    fun theReportedUtilityScienceUrlCompacts() {
        val layout =
            layoutFromUrl(
                "http://localhost:8080/#/0.178571428571/utility-science-pack/FSbr/1/1",
            )
        val width =
            layout.entities.maxOf { it.area.bottomRight.x } -
                layout.entities.minOf { it.area.topLeft.x } + 1
        val height =
            layout.entities.maxOf { it.area.bottomRight.y } -
                layout.entities.minOf { it.area.topLeft.y } + 1

        assertAll(
            { assertTrue(width <= 45, "layout is still $width tiles wide") },
            { assertTrue(height <= 75, "layout is still $height rows tall") },
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
            { assertNoPlannedMaterialIsImported(layout.plan) },
        )
    }

    /**
     * The rocket-silo row has five solid inputs plus its product, more belt rows than a row can
     * hold at one lane each. The row pairs two over-capacity inputs instead of dropping the product
     * belt (which used to leave the target without any output connector), and every belt route
     * keeps its own tiles, so the layout compacts.
     */
    @Test
    fun theReportedRocketSiloUrlCompacts() {
        val layout = layoutFromUrl("http://localhost:8080/#/0.041666666667/rocket-silo/CFSXbmr/1/1")
        val entities = layout.entities
        val width =
            entities.maxOf { it.area.bottomRight.x } - entities.minOf { it.area.topLeft.x } + 1
        val height =
            entities.maxOf { it.area.bottomRight.y } - entities.minOf { it.area.topLeft.y } + 1

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(entities)) },
            { assertTrue(width <= 50, "layout is still $width tiles wide") },
            { assertTrue(height <= 125, "layout is still $height rows tall") },
            { assertNoPlannedMaterialIsImported(layout.plan) },
            { assertContains(layout.plan.beltMaterials, Material("rocket-silo")) },
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
     * machines. The plastic row added by the widened planning forces the electronic-circuit feed to
     * tunnel as well; every other material still reaches its lane on the surface.
     */
    @Test
    fun advancedCircuitUrlOnlyTunnelsForItsBandedFeeds() {
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
                    setOf(copperCable, electronicCircuit),
                    routeTunnels.map { it.material }.toSet(),
                    "only the band's feeds may need an underground belt",
                )
            },
        )
    }

    /**
     * The imported copper plate must reach its lane directly: no turn and no tunnel. Routing
     * imports before the produced deliveries makes this the default, and the trimmer can then move
     * all markers to one column close to the factory edge. (Uranium-238 is produced by the planned
     * centrifuging row now, so it is no longer an import.)
     */
    @Test
    fun reportedUraniumRoundsUrlKeepsItsCopperImportStraight() {
        val layout =
            layoutFromUrl("http://localhost:8080/#/0.3/uranium-rounds-magazine/CFSXbmr/1/1")
        val belts =
            layout.entities.filterIsInstance<BeltEntity>().filter { it.material == copperPlate }
        val tunnels =
            layout.entities.filterIsInstance<UndergroundBeltEntity>().filter {
                it.material == copperPlate
            }
        val inputChests =
            layout.entities.filterIsInstance<InfinityChestEntity>().filter {
                it.settings.item != null
            }
        val machineLeft =
            layout.entities.filterIsInstance<MachineEntity>().minOf {
                it.area.topLeft.x
            }
        val copperChest = inputChests.single { it.settings.item == copperPlate }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), tunnels, "the copper plate import tunnels") },
            { assertTrue(belts.isNotEmpty(), "no copper plate belt was placed") },
            {
                assertEquals(
                    1,
                    belts.map { it.area.topLeft.y }.distinct().size,
                    "the copper plate import is not one straight row",
                )
            },
            {
                assertEquals(
                    emptyList(),
                    belts.filterNot { it.direction == EAST },
                    "the copper plate import turns",
                )
            },
            {
                assertEquals(
                    1,
                    inputChests.map { it.area.topLeft.x }.distinct().size,
                    "the import markers do not share one column",
                )
            },
            {
                assertTrue(
                    machineLeft - copperChest.area.topLeft.x <= 6,
                    "the import prefix was not trimmed",
                )
            },
        )
    }

    /**
     * The electric engine now joins the engine/pipe band: its five solid materials need one mixed
     * input belt, so the product still keeps its own lane instead of falling off the band.
     */
    @Test
    fun theElectricEngineBandKeepsItsProductOnABelt() {
        val layout = layoutFromUrl("http://localhost:8080/#/0.25/electric-engine-unit/CFSXbmr/1/1")
        val bands = layout.plan.rows.filter { it.links.isNotEmpty() }
        val engineBand = bands.single { band ->
            band.machines.any { it.recipe.name == "engine-unit" }
        }

        assertAll(
            {
                assertEquals(
                    setOf("electric-engine-unit", "engine-unit", "pipe"),
                    engineBand.machines.map { it.recipe.name }.toSet(),
                )
            },
            {
                bands.forEach { band ->
                    val internal = band.links.map { it.material }.toSet()
                    val inputs = rowInputs(band.machines, layout.logistics).minus(internal)
                    val outputs = rowOutputs(band.machines, layout.logistics).minus(internal)
                    val budget = beltRowBudget(inputs, outputs, layout.logistics.oneLaneCapacity())
                    assertTrue(
                        budget.fits && budget.rows <= MAX_ROW_BELTS,
                        "band needs ${budget.rows} solid rows: $inputs + $outputs",
                    )
                }
            },
            { assertContains(layout.plan.beltMaterials, electricEngineUnit) },
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
        )
    }

    @Test
    fun aFiveInputRealRecipeUsesAMixedBelt() {
        val layout = Layout(target = "oil-refinery")
        val mixed = layout.entities.filterIsInstance<BeltEntity>().filter { it.load.isMixed }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
            { assertNotNull(mixed.firstOrNull(), "no mixed belt") },
            { assertEquals(emptyList(), mixed.filterNot { it.load.materials.size == 2 }) },
        )
    }

    /**
     * The long straight piercing-rounds run must not survive compaction: a corridor that only fails
     * after stretching is ruled out by its seam and the pass continues with the next one. The plan
     * now includes a uranium-reprocessing centrifuge row, so the bounds are that plan's size.
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
            // the 5-belt firearm/piercing/uranium band is now split into a nuclear/uranium band
            // and a firearm/piercing band, so the widest row is the nine-machine uranium band;
            // the old 23- and 36-tile guards only held while that was one belted row
            { assertTrue(width <= 42, "layout is still $width tiles wide") },
            {
                assertTrue(
                    height <= 40,
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
            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                {
                    assertUndergroundRunsAreStretched(
                        layout.blueprint.entities,
                        layout.beltMaterials,
                    )
                },
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
            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertNoMergingBelts(layout.blueprint.entities, layout.beltMaterials) },
            )
        }

    /** A plan must never import a material one of its own rows builds. */
    private fun assertNoPlannedMaterialIsImported(plan: BlueprintPlan) {
        val built =
            plan.rows
                .flatMap { row ->
                    row.machines.flatMap { it.recipe.result.keys }
                }
                .toSet()

        assertEquals(
            emptyList(),
            plan.sources.map { it.material }.filter { it in built },
            "the plan imports a material it builds",
        )
    }
}
