package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeToGroundEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.Plumbing
import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.LANE_MARGIN
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.assertUndergroundRunsAreStretched
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.fluidRowPlan
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.planned
import de.bjrke.factorio.optimizer.common.blueprint.printing.inspection.layoutAscii
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.Io.INPUT
import de.bjrke.factorio.optimizer.common.core.model.Io.OUTPUT
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.area
import de.bjrke.factorio.optimizer.common.fixtures.petroleum
import de.bjrke.factorio.optimizer.common.fixtures.solidFuel
import de.bjrke.factorio.optimizer.common.fixtures.solidFuelFromPetroleumGasRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Characterization of the fluid lane one row of three chemical plants produces today, so a later
 * fluid change can show exactly which tile, direction or glyph moved on purpose.
 *
 * The three machines touch, so their petroleum ports sit at x = LANE_MARGIN, +3 and +6. The lane
 * runs two rows above them: a plain junction pipe over every port, and a pipe-to-ground pair across
 * each two-tile gap, which the player can walk across. Every port is reached by a vertical shaft
 * whose top end stands one row inside the lane and whose bottom end sits on the port tile.
 */
class BlueprintFluidCharacterizationTest {

    private val fluidPlan =
        BlueprintPlan(
            target = solidFuel,
            rows =
                listOf(
                    PlannedRow.copies(
                        planned(solidFuelFromPetroleumGasRecipe, Factory.CHEMICAL_PLANT),
                        3,
                    ),
                ),
            sources = listOf(PlannedSource(petroleum, 1.0, fluid = true)),
            label = "solid-fuel-from-petroleum-gas",
        )

    private val layout = Layout(fluidPlan)

    private fun plumbingAt(tile: Coordinate) = layout.entities.single { it.area.topLeft == tile }

    private fun end(tile: Coordinate, material: Material, direction: Direction, io: Io) =
        PipeToGroundEntity(tile.area(), "pipe-to-ground", material, direction, io)

    @Test
    fun theMachinesTouchInOneRow() =
        assertTableOf(
            MachineEntity(
                Factory.CHEMICAL_PLANT,
                Coordinate(LANE_MARGIN, 0).area(3, 3),
                "solid-fuel-from-petroleum-gas",
            ),
            MachineEntity(
                Factory.CHEMICAL_PLANT,
                Coordinate(LANE_MARGIN + 3, 0).area(3, 3),
                "solid-fuel-from-petroleum-gas",
            ),
            MachineEntity(
                Factory.CHEMICAL_PLANT,
                Coordinate(LANE_MARGIN + 6, 0).area(3, 3),
                "solid-fuel-from-petroleum-gas",
            ),
        ) { expected ->
            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertEquals(expected, layout.entities.single { it.area == expected.area }) },
            )
        }

    @Test
    fun theLanePutsAJunctionAboveEveryPortAndARunAcrossEveryGap() =
        assertTableOf(
            // the plain junction pipes, one above every machine port
            Coordinate(LANE_MARGIN, -3) to "pipe",
            Coordinate(LANE_MARGIN + 3, -3) to "pipe",
            Coordinate(LANE_MARGIN + 6, -3) to "pipe",
            // the two-tile gap between two ports is a pair of directly adjacent ends
            Coordinate(LANE_MARGIN + 1, -3) to "pipe-to-ground",
            Coordinate(LANE_MARGIN + 2, -3) to "pipe-to-ground",
            Coordinate(LANE_MARGIN + 4, -3) to "pipe-to-ground",
            Coordinate(LANE_MARGIN + 5, -3) to "pipe-to-ground",
        ) { (tile, name) ->
            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertEquals(name, plumbingAt(tile).name) },
            )
        }

    @Test
    fun theGapRunsFaceTheJunctionsTheyJoin() =
        assertTableOf(
            Coordinate(LANE_MARGIN + 1, -3) to INPUT,
            Coordinate(LANE_MARGIN + 2, -3) to OUTPUT,
            Coordinate(LANE_MARGIN + 4, -3) to INPUT,
            Coordinate(LANE_MARGIN + 5, -3) to OUTPUT,
        ) { (tile, io) ->
            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertEquals(end(tile, petroleum, EAST, io), plumbingAt(tile)) },
            )
        }

    @Test
    fun everyMachineIsFedThroughItsOwnVerticalShaft() =
        assertTableOf(
            Coordinate(LANE_MARGIN, -2) to Coordinate(LANE_MARGIN, -1),
            Coordinate(LANE_MARGIN + 3, -2) to Coordinate(LANE_MARGIN + 3, -1),
            Coordinate(LANE_MARGIN + 6, -2) to Coordinate(LANE_MARGIN + 6, -1),
        ) { (top, port) ->
            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertEquals(end(top, petroleum, SOUTH, INPUT), plumbingAt(top)) },
                { assertEquals(end(port, petroleum, SOUTH, OUTPUT), plumbingAt(port)) },
            )
        }

    @Test
    fun theMarkerSitsInlineWestOfTheLaneHead() {
        val markers = layout.entities.filterIsInstance<MarkerEntity>()
        val fluidMarker = markers.single { it.signal.fluid }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            // the solid target marker is the only other one
            { assertEquals(2, markers.size) },
            {
                assertEquals(
                    MarkerEntity(
                        Coordinate(LANE_MARGIN - 2, -3).area(),
                        "constant-combinator",
                        fluidMarker.signal,
                    ),
                    fluidMarker,
                )
            },
            { assertEquals(petroleum, fluidMarker.signal.material) },
        )
    }

    @Test
    fun theLabelIsFlankedByTheTwoEndsOfItsUndergroundRun() =
        assertTableOf(
            Coordinate(LANE_MARGIN - 3, -3) to INPUT,
            Coordinate(LANE_MARGIN - 1, -3) to OUTPUT,
        ) { (tile, io) ->
            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertEquals(end(tile, petroleum, EAST, io), plumbingAt(tile)) },
            )
        }

    @Test
    fun theRowCarriesTheWaterInJunctionsRunsMarkerAndShafts() {
        val plumbing = layout.entities.filterIsInstance<Plumbing>()
        val pipes = plumbing.filterIsInstance<PipeEntity>().map { it.area.topLeft }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            // three junctions, four gap ends, the marker's two ends and six shaft ends
            { assertEquals(15, plumbing.size) },
            { assertEquals(12, plumbing.count { it is PipeToGroundEntity }) },
            {
                assertEquals(
                    listOf(
                        Coordinate(LANE_MARGIN, -3),
                        Coordinate(LANE_MARGIN + 3, -3),
                        Coordinate(LANE_MARGIN + 6, -3),
                    ),
                    pipes,
                )
            },
            { assertEquals(setOf(petroleum), plumbing.map { it.material }.toSet()) },
        )
    }

    @Test
    fun theFluidRowNeedsNoFailedRouteAndValidates() {
        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
        )
    }

    @Test
    fun theFluidRowDrawingIsStable() {
        // The board starts at x = LANE_MARGIN - 3, so the ruler prints "1" at x = 10 and "2" at
        // x = 20 when LANE_MARGIN is 10. Codes: 1 = the chemical plant, a = petroleum on the input
        // lane, b = solid fuel on the output belt, o = a pipe-to-ground end, - = a plain pipe,
        // C = a constant combinator, ^ = a product inserter, + = a pole.
        val expected =
            """
            layout 16x8
                 1         2
            -3 oCo-oo-oo-
            -2    o  o  o
            -1    o  o  o
            0    111111111 +
            1    111111111
            2    111111111
            3     ^ +^  ^
            4    bbbbbbbbbb(C)

            legend
              1 = chemical-plant: solid-fuel-from-petroleum-gas
              a = petroleum-gas
              b = solid-fuel
              C = constant-combinator: petroleum-gas
              C = constant-combinator: solid-fuel
            """
                .trimIndent()
        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(normalized(expected), normalized(layoutAscii(layout.dump))) },
        )
    }

    /** A drawing without the trailing blanks of its grid rows, so only the glyphs are pinned. */
    private fun normalized(drawing: String) =
        drawing.lines().joinToString("\n") { it.trimEnd() }.trimEnd('\n')

    @Test
    fun theFluidRowKeepsEveryUndergroundRunStretched() {
        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertUndergroundRunsAreStretched(layout.entities) },
        )
    }

    /**
     * The three machines the port-table reconciliation moved. The foundry, the cryogenic plant and
     * the oil refinery have pipes on both their top and their bottom edge; the row feeds every
     * machine from the lane above and drains it onto the belt below, so the reconciled table reads
     * the top pipes as the inputs and the bottom ones as outputs.
     */
    @Test
    fun aMachineWithPipesOnBothEdgesIsFedFromItsTopEdge() =
        assertTableOf(
            // machine -> (its top-left corner, the pipe that feeds it)
            Factory.FOUNDRY to (Coordinate(LANE_MARGIN, 0) to Coordinate(LANE_MARGIN + 3, -1)),
            Factory.CRYOGENIC_PLANT to
                (Coordinate(LANE_MARGIN, 0) to Coordinate(LANE_MARGIN + 2, -1)),
            Factory.OIL_REFINERY to (Coordinate(LANE_MARGIN, 0) to Coordinate(LANE_MARGIN + 3, -1)),
        ) { (factory, expected) ->
            val (machine, port) = expected
            val layout = Layout(fluidRowPlan(factory))
            assertAll(
                { assertEquals(listOf(machine), layout.machineEntities.map { it.area.topLeft }) },
                {
                    assertEquals(
                        mapOf(port to petroleum),
                        layout.inputPipes,
                        "$factory is not fed from above",
                    )
                },
                {
                    assertEquals(
                        emptyMap(),
                        layout.outputPipes,
                        "$factory still reserves a port under the machine",
                    )
                },
                // one port per fluid, so the machine's other top pipe stays untouched
                { assertEquals(1, layout.portPipes.size, "$factory is fed twice") },
                {
                    assertEquals(
                        emptyList(),
                        layout.failures,
                        "$factory left its fluid unrouted",
                    )
                },
            )
        }
}
