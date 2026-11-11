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
import de.bjrke.factorio.optimizer.common.fixtures.batteryRecipe
import de.bjrke.factorio.optimizer.common.fixtures.coal
import de.bjrke.factorio.optimizer.common.fixtures.copperPlate
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import kotlin.test.Test
import kotlin.test.assertEquals

class RowFluidLaneTest {

    /**
     * A mixed input belt's merge and turn tiles stand one and two rows outside its lane. The fluid
     * lane must clear them too: in the reported utility-science plan the acid lane's marker block
     * collided with the gear belt's merge, so the lane was dropped and the batteries got no acid.
     */
    @Test
    fun aMixedBeltMergePushesTheLaneBeyondItsTurnTile() {
        val production = planned(batteryRecipe, Factory.CHEMICAL_PLANT)
        val row =
            Row(
                plannedRow = PlannedRow.copies(production, 1),
                machines = listOf(PlacedMachine(production, 40)),
                box = Area(Coordinate(40, 20), Dimension(3, 3)),
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
                                    merge =
                                        BeltMerge(
                                            column = 39,
                                            above = true,
                                            westMaterial = coal,
                                            eastMaterial = copperPlate,
                                            direction = EAST,
                                        ),
                                ),
                            ),
                    ),
            )

        // merge at 16, turn at 15; the lane goes two rows beyond the outline, not the belt
        assertEquals(13, RowFluidLane(logistics).laneRow(row, LineSide.NORTH))
    }
}
