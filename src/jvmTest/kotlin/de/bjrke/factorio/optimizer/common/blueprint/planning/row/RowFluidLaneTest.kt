package de.bjrke.factorio.optimizer.common.blueprint.planning.row

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LaneLoad
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.logistics
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.planned
import de.bjrke.factorio.optimizer.common.core.model.Area
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Dimension
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.LineSide
import de.bjrke.factorio.optimizer.common.core.model.LineSide.NORTH
import de.bjrke.factorio.optimizer.common.fixtures.batteryRecipe
import de.bjrke.factorio.optimizer.common.fixtures.coal
import de.bjrke.factorio.optimizer.common.fixtures.copperPlate
import de.bjrke.factorio.optimizer.common.fixtures.sulfuricAcid
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RowFluidLaneTest {

    /**
     * The lane row is read from the belts alone, so it may sit on a mixed belt's turn row. The
     * east-fed merge starts west of the machine column ([EAST_MERGE_OFFSET]), so the lane head and
     * the merge's turn tile do not meet. In the reported utility-science plan the acid lane's
     * marker block collided with the gear belt's merge and the lane was dropped, leaving the
     * batteries dry.
     */
    @Test
    fun aMixedBeltTurnRowKeepsTheLaneHeadClear() {
        val production = planned(batteryRecipe, Factory.CHEMICAL_PLANT)
        val merge =
            BeltMerge(
                column = LANE_MARGIN - EAST_MERGE_OFFSET,
                above = true,
                westMaterial = coal,
                eastMaterial = copperPlate,
                direction = EAST,
            )
        val row =
            Row(
                plannedRow = PlannedRow.copies(production, 1),
                machines = listOf(PlacedMachine(production, LANE_MARGIN)),
                box = Area(Coordinate(LANE_MARGIN, 20), Dimension(3, 3)),
                lanes =
                    RowLanes(
                        factoryTop = 20,
                        belts =
                            listOf(
                                RowBelt(
                                    y = 17,
                                    load = LaneLoad.mixed(coal, copperPlate),
                                    direction = EAST,
                                    io = Io.INPUT,
                                    side = LineSide.NORTH,
                                    merge = merge,
                                ),
                            ),
                    ),
            )
        val lane = RowFluidLane(logistics)
        val laneRow = lane.laneRow(row, NORTH)
        val acid = lane.fluids(row, NORTH).single { it.material == sulfuricAcid }
        val head = FluidLane.of(laneRow, acid.ports, NORTH).head

        assertAll(
            // two rows beyond the belt, i.e. the turn row, not two rows beyond the turn
            { assertEquals(15, laneRow) },
            { assertEquals(merge.turnRow(17), laneRow) },
            { assertTrue(head.x > merge.turnColumn, "the lane head $head sits on the turn tile") },
        )
    }
}
