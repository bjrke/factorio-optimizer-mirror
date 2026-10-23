package de.bjrke.factorio.optimizer.common.blueprint.output

import de.bjrke.factorio.optimizer.common.blueprint.BlueprintWire
import de.bjrke.factorio.optimizer.common.layout.entity.ChestEntity
import de.bjrke.factorio.optimizer.common.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.model.Coordinate

/**
 * The circuit wires a chest marker needs: the output marker's inserters read the chest through
 * their circuit condition, so each has to be wired to it. A blueprint does not rebuild a circuit
 * wire on its own (just like a pole wire), so the connection must be part of it.
 */
internal fun chestWires(placed: List<LayoutEntity>): List<BlueprintWire> {
    val wires = mutableListOf<BlueprintWire>()
    placed.forEachIndexed { index, entity ->
        // only the output marker reads the chest; the input marker has no circuit condition
        if (entity !is ChestEntity || entity.circuitConditionEnabled != false) {
            return@forEachIndexed
        }
        val chest = entity.area.topLeft
        for (dx in listOf(-1, 1)) {
            val inserter =
                placed.withIndex().firstOrNull { (_, candidate) ->
                    candidate is InserterEntity &&
                        candidate.circuitCondition != null &&
                        candidate.area.topLeft == Coordinate(chest.x + dx, chest.y)
                } ?: continue
            val chestNumber = index + 1
            val inserterNumber = inserter.index + 1
            wires +=
                if (dx < 0) {
                    BlueprintWire(inserterNumber, 1, chestNumber, 1)
                } else {
                    BlueprintWire(chestNumber, 1, inserterNumber, 1)
                }
        }
    }
    return wires
}
