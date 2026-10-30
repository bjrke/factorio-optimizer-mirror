package de.bjrke.factorio.optimizer.common.blueprint

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.assertUndergroundRunsAreStretched
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.fluidRowPlan
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.planned
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.recipe
import de.bjrke.factorio.optimizer.common.blueprint.inspection.layoutAscii
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.factorio.optimizer.common.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.layout.entity.PipeEntity
import de.bjrke.factorio.optimizer.common.layout.entity.PipeToGroundEntity
import de.bjrke.factorio.optimizer.common.layout.entity.Plumbing
import de.bjrke.factorio.optimizer.common.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.Io.INPUT
import de.bjrke.factorio.optimizer.common.model.Io.OUTPUT
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.area
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.prototype.Category
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Characterization of the fluid lane one row of three chemical plants produces today, so a later
 * fluid change can show exactly which tile, direction or glyph moved on purpose.
 *
 * The three machines touch, so their water ports sit at x = 40, 43 and 46. The lane runs two rows
 * above them: a plain junction pipe over every port, and a pipe-to-ground pair across each two-tile
 * gap, which the player can walk across. Every port is reached by a vertical shaft whose top end
 * stands one row inside the lane and whose bottom end sits on the port tile.
 */
class BlueprintFluidCharacterizationTest {

    private val boil =
        recipe("water-boil", Category.CHEMISTRY, mapOf(gear to 1.0), mapOf(water to 1.0))

    private val fluidPlan =
        BlueprintPlan(
            target = gear,
            rows = listOf(PlannedRow(List(3) { planned(boil, gear, Factory.CHEMICAL_PLANT) })),
            sources = listOf(PlannedSource(water, 1.0, fluid = true)),
            scale = 1.0,
            label = "water-boil",
        )

    private val layout = Layout(fluidPlan)

    private fun plumbingAt(tile: Coordinate) = layout.entities.single { it.area.topLeft == tile }

    private fun end(tile: Coordinate, material: Material, direction: Direction, io: Io) =
        PipeToGroundEntity(tile.area(), "pipe-to-ground", material, direction, io)

    @Test
    fun theMachinesTouchInOneRow() =
        assertTableOf(
            MachineEntity(Factory.CHEMICAL_PLANT, Coordinate(40, 0).area(3, 3), "water-boil"),
            MachineEntity(Factory.CHEMICAL_PLANT, Coordinate(43, 0).area(3, 3), "water-boil"),
            MachineEntity(Factory.CHEMICAL_PLANT, Coordinate(46, 0).area(3, 3), "water-boil"),
        ) { expected ->
            assertEquals(expected, layout.entities.single { it.area == expected.area })
        }

    @Test
    fun theLanePutsAJunctionAboveEveryPortAndARunAcrossEveryGap() =
        assertTableOf(
            // the plain junction pipes, one above every machine port
            Coordinate(40, -3) to "pipe",
            Coordinate(43, -3) to "pipe",
            Coordinate(46, -3) to "pipe",
            // the two-tile gap between two ports is a pair of directly adjacent ends
            Coordinate(41, -3) to "pipe-to-ground",
            Coordinate(42, -3) to "pipe-to-ground",
            Coordinate(44, -3) to "pipe-to-ground",
            Coordinate(45, -3) to "pipe-to-ground",
        ) { (tile, name) ->
            assertEquals(name, plumbingAt(tile).name)
        }

    @Test
    fun theGapRunsFaceTheJunctionsTheyJoin() =
        assertTableOf(
            Coordinate(41, -3) to INPUT,
            Coordinate(42, -3) to OUTPUT,
            Coordinate(44, -3) to INPUT,
            Coordinate(45, -3) to OUTPUT,
        ) { (tile, io) ->
            assertEquals(end(tile, water, EAST, io), plumbingAt(tile))
        }

    @Test
    fun everyMachineIsFedThroughItsOwnVerticalShaft() =
        assertTableOf(
            Coordinate(40, -2) to Coordinate(40, -1),
            Coordinate(43, -2) to Coordinate(43, -1),
            Coordinate(46, -2) to Coordinate(46, -1),
        ) { (top, port) ->
            assertAll(
                { assertEquals(end(top, water, SOUTH, INPUT), plumbingAt(top)) },
                { assertEquals(end(port, water, SOUTH, OUTPUT), plumbingAt(port)) },
            )
        }

    @Test
    fun theMarkerSitsInlineWestOfTheLaneHead() {
        val markers = layout.entities.filterIsInstance<MarkerEntity>()
        val fluidMarker = markers.single { it.signal.fluid }

        assertAll(
            // the solid target marker is the only other one
            { assertEquals(2, markers.size) },
            {
                assertEquals(
                    MarkerEntity(
                        Coordinate(38, -3).area(),
                        "constant-combinator",
                        fluidMarker.signal,
                    ),
                    fluidMarker,
                )
            },
            { assertEquals(water, fluidMarker.signal.material) },
        )
    }

    @Test
    fun theLabelIsFlankedByTheTwoEndsOfItsUndergroundRun() =
        assertTableOf(
            Coordinate(37, -3) to INPUT,
            Coordinate(39, -3) to OUTPUT,
        ) { (tile, io) ->
            assertEquals(end(tile, water, EAST, io), plumbingAt(tile))
        }

    @Test
    fun theRowCarriesTheWaterInJunctionsRunsMarkerAndShafts() {
        val plumbing = layout.entities.filterIsInstance<Plumbing>()
        val pipes = plumbing.filterIsInstance<PipeEntity>().map { it.area.topLeft }

        assertAll(
            // three junctions, four gap ends, the marker's two ends and six shaft ends
            { assertEquals(15, plumbing.size) },
            { assertEquals(12, plumbing.count { it is PipeToGroundEntity }) },
            {
                assertEquals(
                    listOf(Coordinate(40, -3), Coordinate(43, -3), Coordinate(46, -3)),
                    pipes,
                )
            },
            { assertEquals(setOf(water), plumbing.map { it.material }.toSet()) },
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
        // The board starts at x = 37, so the ruler prints "4" at x = 40 and "5" at x = 50. Codes:
        // 1 = the chemical plant, a = gear on the output belt, o = a pipe-to-ground end,
        // - = a plain pipe, C = a constant combinator, ^ = a product inserter, + = a pole.
        val expected =
            """
            layout 14x8
                 4         5
            -3 oCo-oo-oo-
            -2    o  o  o
            -1    o  o  o
            0    111111111 +
            1    111111111
            2    111111111
            3     ^ +^  ^
            4    aaaaaaa(C)

            legend
              1 = chemical-plant: water-boil
              a = iron-gear-wheel
              b = water
              C = constant-combinator: iron-gear-wheel
              C = constant-combinator: water
            """
                .trimIndent()
        assertEquals(normalized(expected), normalized(layoutAscii(layout.dump)))
    }

    /** A drawing without the trailing blanks of its grid rows, so only the glyphs are pinned. */
    private fun normalized(drawing: String) =
        drawing.lines().joinToString("\n") { it.trimEnd() }.trimEnd('\n')

    @Test
    fun theFluidRowKeepsEveryUndergroundRunStretched() {
        assertUndergroundRunsAreStretched(layout.entities)
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
            Factory.FOUNDRY to (Coordinate(40, 0) to Coordinate(43, -1)),
            Factory.CRYOGENIC_PLANT to (Coordinate(40, 0) to Coordinate(42, -1)),
            Factory.OIL_REFINERY to (Coordinate(40, 0) to Coordinate(43, -1)),
        ) { (factory, expected) ->
            val (machine, port) = expected
            val layout = Layout(fluidRowPlan(factory))
            assertAll(
                { assertEquals(listOf(machine), layout.machineEntities.map { it.area.topLeft }) },
                {
                    assertEquals(
                        mapOf(port to water),
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
