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
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.FIRST_MACHINE_X
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.NORTH_MARGIN
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.assertUndergroundRunsAreStretched
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.fluidRowPlan
import de.bjrke.factorio.optimizer.common.blueprint.printing.inspection.layoutAscii
import de.bjrke.factorio.optimizer.common.core.model.Area
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.Io.INPUT
import de.bjrke.factorio.optimizer.common.core.model.Io.OUTPUT
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.area
import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.petroleum
import de.bjrke.factorio.optimizer.common.fixtures.production
import de.bjrke.factorio.optimizer.common.fixtures.solidFuel
import de.bjrke.factorio.optimizer.common.fixtures.solidFuelFromPetroleumGasRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Factory.CHEMICAL_PLANT
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Characterization of the fluid lane one row of three chemical plants produces today, so a later
 * fluid change can show exactly which tile, direction or glyph moved on purpose.
 *
 * The three machines touch, so their petroleum ports sit at x = FIRST_MACHINE_X, +3 and +6. The
 * lane runs three rows above them: a plain junction pipe over every port, and a pipe-to-ground pair
 * across each two-tile gap, which the player can walk across. Every port is reached by a vertical
 * shaft whose top end stands one row inside the lane and whose bottom end sits on the port tile.
 */
class BlueprintFluidCharacterizationTest : BaseGameCtxTest() {

    private val fluidPlan =
        BlueprintPlan(
            target = solidFuel,
            rows =
                listOf(
                    PlannedRow.copies(
                        production(solidFuelFromPetroleumGasRecipe, CHEMICAL_PLANT),
                        3,
                    ),
                ),
            sources = listOf(PlannedSource(petroleum, fluid = true)),
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
                CHEMICAL_PLANT,
                Area(FIRST_MACHINE_X, NORTH_MARGIN, 3, 3),
                "solid-fuel-from-petroleum-gas",
            ),
            MachineEntity(
                CHEMICAL_PLANT,
                Area(FIRST_MACHINE_X + 3, NORTH_MARGIN, 3, 3),
                "solid-fuel-from-petroleum-gas",
            ),
            MachineEntity(
                CHEMICAL_PLANT,
                Area(FIRST_MACHINE_X + 6, NORTH_MARGIN, 3, 3),
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
            Coordinate(FIRST_MACHINE_X, NORTH_MARGIN - 3) to "pipe",
            Coordinate(FIRST_MACHINE_X + 3, NORTH_MARGIN - 3) to "pipe",
            Coordinate(FIRST_MACHINE_X + 6, NORTH_MARGIN - 3) to "pipe",
            // the two-tile gap between two ports is a pair of directly adjacent ends
            Coordinate(FIRST_MACHINE_X + 1, NORTH_MARGIN - 3) to "pipe-to-ground",
            Coordinate(FIRST_MACHINE_X + 2, NORTH_MARGIN - 3) to "pipe-to-ground",
            Coordinate(FIRST_MACHINE_X + 4, NORTH_MARGIN - 3) to "pipe-to-ground",
            Coordinate(FIRST_MACHINE_X + 5, NORTH_MARGIN - 3) to "pipe-to-ground",
        ) { (tile, name) ->
            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertEquals(name, plumbingAt(tile).name) },
            )
        }

    @Test
    fun theGapRunsFaceTheJunctionsTheyJoin() =
        assertTableOf(
            Coordinate(FIRST_MACHINE_X + 1, NORTH_MARGIN - 3) to INPUT,
            Coordinate(FIRST_MACHINE_X + 2, NORTH_MARGIN - 3) to OUTPUT,
            Coordinate(FIRST_MACHINE_X + 4, NORTH_MARGIN - 3) to INPUT,
            Coordinate(FIRST_MACHINE_X + 5, NORTH_MARGIN - 3) to OUTPUT,
        ) { (tile, io) ->
            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertEquals(end(tile, petroleum, EAST, io), plumbingAt(tile)) },
            )
        }

    @Test
    fun everyMachineIsFedThroughItsOwnVerticalShaft() =
        assertTableOf(
            Coordinate(FIRST_MACHINE_X, NORTH_MARGIN - 2) to
                Coordinate(FIRST_MACHINE_X, NORTH_MARGIN - 1),
            Coordinate(FIRST_MACHINE_X + 3, NORTH_MARGIN - 2) to
                Coordinate(FIRST_MACHINE_X + 3, NORTH_MARGIN - 1),
            Coordinate(FIRST_MACHINE_X + 6, NORTH_MARGIN - 2) to
                Coordinate(FIRST_MACHINE_X + 6, NORTH_MARGIN - 1),
        ) { (top, port) ->
            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertEquals(end(top, petroleum, SOUTH, INPUT), plumbingAt(top)) },
                { assertEquals(end(port, petroleum, SOUTH, OUTPUT), plumbingAt(port)) },
            )
        }

    @Test
    fun theMarkerSitsInTheSharedImportColumnWestOfTheLaneHead() {
        val markers = layout.entities.filterIsInstance<MarkerEntity>()
        val fluidMarker = markers.single { it.signal.fluid }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            // the solid target marker is the only other one
            { assertEquals(2, markers.size) },
            {
                assertEquals(
                    MarkerEntity(
                        Coordinate(FIRST_MACHINE_X - 4, NORTH_MARGIN - 3).area(),
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
    fun theSourceBlockEndsWestOfItsConnectionRun() {
        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            {
                assertEquals(
                    end(Coordinate(FIRST_MACHINE_X - 5, NORTH_MARGIN - 3), petroleum, EAST, INPUT),
                    plumbingAt(Coordinate(FIRST_MACHINE_X - 5, NORTH_MARGIN - 3)),
                )
            },
            {
                assertEquals(
                    end(Coordinate(FIRST_MACHINE_X - 3, NORTH_MARGIN - 3), petroleum, EAST, OUTPUT),
                    plumbingAt(Coordinate(FIRST_MACHINE_X - 3, NORTH_MARGIN - 3)),
                )
            },
            // the block ends three tiles west of the lane head; the link is a pair of touching ends
            {
                assertEquals(
                    end(Coordinate(FIRST_MACHINE_X - 2, NORTH_MARGIN - 3), petroleum, EAST, INPUT),
                    plumbingAt(Coordinate(FIRST_MACHINE_X - 2, NORTH_MARGIN - 3)),
                )
            },
            {
                assertEquals(
                    end(Coordinate(FIRST_MACHINE_X - 1, NORTH_MARGIN - 3), petroleum, EAST, OUTPUT),
                    plumbingAt(Coordinate(FIRST_MACHINE_X - 1, NORTH_MARGIN - 3)),
                )
            },
        )
    }

    @Test
    fun theRowCarriesTheWaterInJunctionsRunsMarkerAndShafts() {
        val plumbing = layout.entities.filterIsInstance<Plumbing>()
        val pipes =
            plumbing.filterIsInstance<PipeEntity>().map { it.area.topLeft }.sortedBy { it.x }

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            // three junctions, four gap ends, the source block's two ends, its two link ends and
            // six shafts
            { assertEquals(17, plumbing.size) },
            { assertEquals(14, plumbing.count { it is PipeToGroundEntity }) },
            {
                assertEquals(
                    listOf(
                        Coordinate(FIRST_MACHINE_X, NORTH_MARGIN - 3),
                        Coordinate(FIRST_MACHINE_X + 3, NORTH_MARGIN - 3),
                        Coordinate(FIRST_MACHINE_X + 6, NORTH_MARGIN - 3),
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
        // The source block starts at x = FIRST_MACHINE_X - 5 = 27, so the ruler prints "3" at
        // x = 30 and "4" at x = 40. Codes: 1 = the chemical plant, a = petroleum on the input
        // lane, b = solid fuel on the output belt, o = a pipe-to-ground end, - = a plain pipe,
        // C = a constant combinator, ^ = a product inserter, + = a pole.
        val expected =
            """
            layout 18x8
                  3         4
            14 oCooo-oo-oo-
            15      o  o  o
            16      o  o  o
            17      111111111 +
            18      111111111
            19      111111111
            20       ^ +^  ^
            21      bbbbbbbbbb(C)

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
            {
                assertUndergroundRunsAreStretched(
                    layout.entities,
                    layout.logistics.undergroundRange,
                )
            },
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
            Factory.FOUNDRY to
                (Coordinate(FIRST_MACHINE_X, NORTH_MARGIN) to
                    Coordinate(FIRST_MACHINE_X + 3, NORTH_MARGIN - 1)),
            Factory.CRYOGENIC_PLANT to
                (Coordinate(FIRST_MACHINE_X, NORTH_MARGIN) to
                    Coordinate(FIRST_MACHINE_X + 2, NORTH_MARGIN - 1)),
            Factory.OIL_REFINERY to
                (Coordinate(FIRST_MACHINE_X, NORTH_MARGIN) to
                    Coordinate(FIRST_MACHINE_X + 3, NORTH_MARGIN - 1)),
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
