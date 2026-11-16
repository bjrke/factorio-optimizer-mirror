package de.bjrke.factorio.optimizer.common.blueprint.placing.special

import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutNames
import de.bjrke.factorio.optimizer.common.blueprint.layout.PowerPole
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PoleEntity
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.area
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.PoleStats
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A burner machine (furnace, biochamber, captive biter spawner) runs on fuel instead of the
 * electric grid, so its body needs no pole; the inserters around it still do.
 */
class PowerPlacerTest {

    private val smallPole = PoleStats(supplyRadius = 2, wireReach = 7.5)

    @Test
    fun aBurnerMachineNeedsNoPole() {
        val board = boardWith(machine(Factory.STONE_FURNACE))

        placePoles(board)

        assertEquals(emptyList(), poles(board))
    }

    @Test
    fun anElectricMachineIsCovered() {
        val board = boardWith(machine(Factory.ELECTRIC_FURNACE))

        placePoles(board)

        assertEquals(1, poles(board).size)
    }

    @Test
    fun aBurnerMachineRowStillPowersItsInserter() {
        val inserter = inserter(Coordinate(3, 5))
        val board = boardWith(machine(Factory.STONE_FURNACE), inserter)

        placePoles(board)

        val covered = poles(board).flatMapTo(mutableSetOf()) { it.area.topLeft.area().grow(2) }
        assertAll(
            { assertTrue(poles(board).isNotEmpty(), "no pole at all") },
            { assertTrue(inserter.area.topLeft in covered, "the inserter is outside the poles") },
        )
    }

    private fun placePoles(board: LayoutBoard) =
        PowerPlacer(board, 0, PowerPole.SMALL_ELECTRIC_POLE, smallPole).place()

    private fun poles(board: LayoutBoard) = board.placed.filterIsInstance<PoleEntity>()

    private fun machine(factory: Factory) =
        MachineEntity(factory, Coordinate(4, 4).area(3, 3), recipe = "iron-plate")

    private fun inserter(topLeft: Coordinate) =
        InserterEntity(
            area = topLeft.area(),
            name = "fast-inserter",
            material = ironPlate,
            direction = Direction.EAST,
            long = false,
        )

    private fun boardWith(vararg entities: LayoutEntity) =
        LayoutBoard(
                names =
                    LayoutNames(
                        belt = "transport-belt",
                        inserter = "inserter",
                        longInserter = "long-handed-inserter",
                    ),
                undergroundRange = 4,
                fluidPredicate = { false },
                temperatureOf = { 0 },
            )
            .also { board -> entities.forEach(board::place) }
}
