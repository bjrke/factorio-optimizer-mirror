package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityChestEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity
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
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.assertNoLoaderOverTunnel
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
import de.bjrke.factorio.optimizer.common.core.model.neighbors
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

    /**
     * Every real plan's layout once, against every geometry and feed invariant: overlap, dangling
     * inserters, tunnel ends, splitter feeds, merged materials, stretched runs and one belt source
     * per material. One build per target, and the table labels a failure with the target that broke
     * the rule.
     */
    @Test
    fun realBlueprintsAreValid() =
        assertTableOf(
            "advanced-circuit",
            "electronic-circuit",
            "logistic-science-pack",
            "processing-unit",
            "utility-science-pack",
            "lubricant",
            "copper-cable",
            "iron-gear-wheel",
            "inserter",
        ) { target ->
            val layout = Layout(target)
            val entities = layout.blueprint.entities
            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertNoOverlap(entities) },
                { assertNoDanglingInserter(entities) },
                { assertConnectedUndergroundBelts(entities) },
                { assertTunnelEndsAreFed(entities) },
                { assertSplittersOnlyFeedSurface(entities) },
                { assertNoMergingBelts(entities, layout.beltMaterials, layout.beltLoads) },
                { assertUndergroundRunsAreStretched(entities, layout.beltMaterials) },
                { assertOneBeltSourcePerMaterial(layout.plan) },
            )
        }

    @Test
    fun realCheatBlueprintsAreValid() =
        assertTableOf(
            "advanced-circuit",
            "electronic-circuit",
            "utility-science-pack",
            "processing-unit",
        ) { target ->
            val layout = Layout(target, beltEnd = BeltEnd.INFINITY_CHEST)
            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertNoOverlap(layout.blueprint.entities) },
                { assertNoLoaderOverTunnel(layout) },
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
            { assertTrue(width <= 42, "layout is still $width tiles wide") },
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
            { assertTrue(width <= 52, "layout is still $width tiles wide") },
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
            { assertTrue(width <= 52, "layout is still $width tiles wide") },
            { assertTrue(height <= 125, "layout is still $height rows tall") },
            { assertNoPlannedMaterialIsImported(layout.plan) },
            { assertContains(layout.plan.beltMaterials, Material("rocket-silo")) },
        )
    }

    /**
     * The reported logistic-science layout puts the target between its producer branches: the
     * inserter/circuit branch sits north, the transport-belt/gear branch south. Every feed turns at
     * its trunk and its lane instead of looping around the target row, so the layout keeps its two
     * circuit corners and needs no route tunnel at all.
     */
    @Test
    fun theReportedLogisticScienceUrlCompactsBothCorners() {
        val layout = layoutFromUrl("http://localhost:8080/#/1/logistic-science-pack/CFSXbmr/1/1")
        val entities = layout.entities
        val width =
            entities.maxOf { it.area.bottomRight.x } - entities.minOf { it.area.topLeft.x } + 1
        val height =
            entities.maxOf { it.area.bottomRight.y } - entities.minOf { it.area.topLeft.y } + 1
        val circuitCorners = corners(entities, electronicCircuit)

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(entities)) },
            { assertTrue(width <= 23, "layout is still $width tiles wide") },
            { assertTrue(height <= 47, "layout is still $height rows tall") },
            {
                assertEquals(
                    2,
                    circuitCorners.size,
                    "the circuit feed turns only at its trunk and at its lane: $circuitCorners",
                )
            },
            {
                assertEquals(
                    emptySet(),
                    routeTunnels(entities).map { it.material }.toSet(),
                    "the target-centred rows route every feed on the surface",
                )
            },
        )
    }

    /**
     * The reported production-science layout distributes the target's three producer branches: the
     * productivity-module branch goes north, the electric-furnace branch south and the iron-stick
     * branch north again.
     */
    @Test
    fun theReportedProductionScienceUrlPutsModulesNorth() {
        val layout =
            layoutFromUrl(
                "http://localhost:8080/#/0.892857142857/production-science-pack/CFSXbmr/1/1",
            )
        val machines = layout.entities.filterIsInstance<MachineEntity>()
        val targetTop =
            machines.filter { it.recipe == "production-science-pack" }.minOf { it.area.topLeft.y }
        val moduleTop =
            machines.filter { it.recipe == "productivity-module" }.minOf { it.area.topLeft.y }
        val furnaceTop =
            machines.filter { it.recipe == "electric-furnace" }.minOf { it.area.topLeft.y }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertTrue(moduleTop < targetTop, "the module branch is not north of the target") },
            {
                assertTrue(
                    moduleTop < furnaceTop,
                    "the module branch is not north of the electric-furnace branch",
                )
            },
        )
    }

    /**
     * The reported utility-science layout puts the robot-frame branch first below the target, so
     * the frames and their requirements stay in the north instead of running up the east side from
     * the bottom. The target product moves above the target to leave the producer side free.
     */
    @Test
    fun theReportedUtilityScienceUrlPutsRobotFramesNorth() {
        val layout = layoutFromUrl("http://localhost:8080/#/2.5/utility-science-pack/CFSXbmr/1/1")
        val entities = layout.entities
        val machines = entities.filterIsInstance<MachineEntity>()
        val targetTop =
            machines.filter { it.recipe == "utility-science-pack" }.minOf { it.area.topLeft.y }
        val robotTop =
            machines.filter { it.recipe == "flying-robot-frame" }.minOf { it.area.topLeft.y }
        val processingTop =
            machines.filter { it.recipe == "processing-unit" }.minOf { it.area.topLeft.y }
        val lowDensityTop =
            machines.filter { it.recipe == "low-density-structure" }.minOf { it.area.topLeft.y }
        val productRow =
            entities
                .filterIsInstance<BeltEntity>()
                .filter { it.material == Material("utility-science-pack") }
                .minOf { it.area.topLeft.y }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            {
                assertTrue(
                    robotTop < processingTop,
                    "robot frames are not north of the processing-unit branch",
                )
            },
            {
                assertTrue(
                    robotTop < lowDensityTop,
                    "robot frames are not north of the low-density branch",
                )
            },
            { assertTrue(productRow < targetTop, "the target product is not north of the target") },
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
     * The reported advanced-circuit URL must still replay without a single failed feed. The
     * target-centred rows put the circuit branch north and the cable/plastic branch south; the two
     * south inputs share one mixed lane, so every feed reaches its lane on the surface.
     */
    @Test
    fun advancedCircuitUrlRoutesEveryFeedOnTheSurface() {
        val layout = layoutFromUrl("http://localhost:8080/#/4.5/advanced-circuit/CFSXbmr/1/1")

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            {
                assertEquals(
                    emptySet(),
                    routeTunnels(layout.entities).map { it.material }.toSet(),
                    "every feed reaches its lane on the surface",
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
            { assertTrue(width <= 43, "layout is still $width tiles wide") },
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

    /**
     * The belt tiles of [material] that a belt of another direction pours into: the side-loads that
     * make a route turn. A corner faces the tile it turns into, so its feeder lies on another axis.
     */
    private fun corners(entities: List<LayoutEntity>, material: Material): List<BeltEntity> {
        val belts = entities.filterIsInstance<BeltEntity>().filter { it.material == material }
        val byTile = belts.associateBy { it.area.topLeft }
        return belts.filter { belt ->
            val tile = belt.area.topLeft
            tile.neighbors.any { neighbor ->
                val feeder = byTile[neighbor]
                feeder != null &&
                    feeder.direction.step == tile - neighbor &&
                    feeder.direction != belt.direction
            }
        }
    }

    /** The underground belt ends of the belt routes, without the fixed import connectors. */
    private fun routeTunnels(entities: List<LayoutEntity>) =
        entities.filterIsInstance<UndergroundBeltEntity>().filter { tunnel ->
            entities.none {
                it is MarkerEntity && it.area.topLeft.manhattanDist(tunnel.area.topLeft) == 1
            }
        }
}
