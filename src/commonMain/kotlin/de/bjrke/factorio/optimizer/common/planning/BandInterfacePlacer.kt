package de.bjrke.factorio.optimizer.common.planning

import de.bjrke.factorio.optimizer.common.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.layout.placeInserter
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.plan.PlannedLink
import de.bjrke.factorio.optimizer.common.plan.blueprintMachineFluidPorts
import de.bjrke.factorio.optimizer.common.service.LogisticsCalculator
import kotlin.math.ceil

/**
 * Places the inserters that move a direct-feed band's internal material straight from one machine
 * into the next. They sit in the gap column between the two machines, on every machine row that
 * neither neighbor needs for a fluid port, as many as fit.
 */
internal class BandInterfacePlacer(
    private val board: LayoutBoard,
    private val plan: BlueprintPlan,
    private val logistics: LogisticsCalculator,
) {

    fun place(row: Row) {
        for (link in row.interfaces) placeInterface(row, link)
    }

    private fun placeInterface(row: Row, link: PlannedLink) {
        val left = row.machines[link.gap]
        val right = row.machines[link.gap + 1]
        val blocked = portRows(row, left, east = true) + portRows(row, right, east = false)
        val free = (row.factoryTop..row.factoryBottom).filterNot(blocked::contains)
        val needed = maxOf(1, ceil(logistics.directInserterCount(link.flow)).toInt())
        val x = row.left + link.gap * row.stride + row.width
        for (y in free.take(needed)) {
            board.placeInserter(
                Coordinate(x, y),
                if (link.producerIsLeft) WEST else EAST,
                link.material,
                false,
            )
        }
    }

    /** The machine rows [machine] keeps free for a fluid port on the side facing the gap. */
    private fun portRows(row: Row, machine: PlacedMachine, east: Boolean): Set<Int> {
        val allocation = plan.recipes[machine.allocation]
        val recipe = allocation.recipe
        val usesFluid =
            recipe.ingredients.keys.any { logistics.isFluid(it) } ||
                recipe.result.keys.any { logistics.isFluid(it) }
        if (!usesFluid) return emptySet()
        val edge = if (east) row.width else -1
        return blueprintMachineFluidPorts(allocation.factory.identifier)
            .filter { it.tile.x == edge }
            .mapTo(mutableSetOf()) { row.factoryTop + it.tile.y }
    }
}
