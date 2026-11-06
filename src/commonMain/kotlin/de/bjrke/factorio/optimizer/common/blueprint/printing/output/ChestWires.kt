package de.bjrke.factorio.optimizer.common.blueprint.printing.output

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.ChestEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.blueprint.printing.model.BlueprintWire
import de.bjrke.factorio.optimizer.common.core.model.Coordinate

/**
 * The circuit wires a chest marker needs: the output marker's inserters read the chest through
 * their circuit condition, so each has to be wired to it. A blueprint does not rebuild a circuit
 * wire on its own (just like a pole wire), so the connection must be part of it.
 */
internal fun chestWires(placed: List<LayoutEntity>) = buildList {
    placed.forEachIndexed { index, entity ->
        // only the output marker reads the chest; the input marker has no circuit condition
        if (entity !is ChestEntity || entity.circuitConditionEnabled != false) return@forEachIndexed

        val chest = entity.area.topLeft
        for (dx in intArrayOf(-1, 1)) {
            placed
                .withIndex()
                .firstOrNull { (_, candidate) ->
                    candidate is InserterEntity &&
                        candidate.circuitCondition != null &&
                        candidate.area.topLeft == Coordinate(chest.x + dx, chest.y)
                }
                ?.let { (inserterIdx, _) ->
                    add(BlueprintWire(inserterIdx + 1, 1, index + 1, 1))
                }
        }
    }
}
