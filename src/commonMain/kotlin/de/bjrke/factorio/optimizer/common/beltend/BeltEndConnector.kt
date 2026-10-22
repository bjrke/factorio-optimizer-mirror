package de.bjrke.factorio.optimizer.common.beltend

import de.bjrke.factorio.optimizer.common.layout.ChestEntity
import de.bjrke.factorio.optimizer.common.layout.InserterEntity
import de.bjrke.factorio.optimizer.common.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.layout.LayoutEntity
import de.bjrke.factorio.optimizer.common.layout.Signal
import de.bjrke.factorio.optimizer.common.layout.endName
import de.bjrke.factorio.optimizer.common.layout.markTunnel
import de.bjrke.factorio.optimizer.common.layout.placeEnd
import de.bjrke.factorio.optimizer.common.layout.placeInfinityChest
import de.bjrke.factorio.optimizer.common.layout.placeLoader
import de.bjrke.factorio.optimizer.common.layout.placeMarker
import de.bjrke.factorio.optimizer.common.model.BeltEnd
import de.bjrke.factorio.optimizer.common.model.BlueprintWire
import de.bjrke.factorio.optimizer.common.model.CircuitCondition
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.model.InfinitySettings
import de.bjrke.factorio.optimizer.common.model.Io.INPUT
import de.bjrke.factorio.optimizer.common.model.Io.OUTPUT
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.RequestFilter
import de.bjrke.factorio.optimizer.common.model.area

/**
 * Places the solid belt's end marker chosen by [beltEnd]: a constant-combinator label, an infinity
 * chest or a chest the inserters feed. A fluid belt never uses this; it always keeps a label.
 */
internal class BeltEndConnector(private val board: LayoutBoard, private val beltEnd: BeltEnd) {

    /**
     * How many tiles the solid belt connector occupies left of the feed: three for a label or a
     * cheat chest, five for a chest the inserters feed.
     */
    val width
        get() =
            when (beltEnd) {
                BeltEnd.CONSTANT_COMBINATOR,
                BeltEnd.INFINITY_CHEST -> 3
                else -> 5
            }

    /**
     * The column a solid connector starts on. A cheat chest sits two tiles in so its loader fits
     * before the feed; every other connector starts at the west edge.
     */
    val left
        get() = if (beltEnd == BeltEnd.INFINITY_CHEST) 2 else 0

    /**
     * Places the selected solid belt connector for [material] on [row], starting at column [left]:
     * a constant-combinator label, an infinity chest or a chest the inserters feed. [input] marks a
     * connector that supplies an imported belt, whose first tile is the tunnel input; the target
     * output passes `false` so a cheat chest loads from the belt instead.
     */
    fun placeSolidConnector(material: Material, left: Int, row: Int, input: Boolean) {
        when (beltEnd) {
            BeltEnd.CONSTANT_COMBINATOR -> {
                // the label sits above an underground run, so the belt can pass underneath
                board.placeEnd(material, Coordinate(left, row), EAST, INPUT)
                board.placeMarker(Coordinate(left + 1, row), Signal(material))
                board.placeEnd(material, Coordinate(left + 2, row), EAST, OUTPUT)
                board.markTunnel(
                    Coordinate(left, row),
                    Coordinate(left + 2, row),
                    board.endName(material),
                )
            }
            BeltEnd.INFINITY_CHEST -> {
                if (input) {
                    board.placeInfinityChest(
                        Coordinate(left, row),
                        InfinitySettings(item = material),
                    )
                    board.placeLoader(Coordinate(left + 1, row), EAST, OUTPUT, material)
                } else {
                    board.placeLoader(Coordinate(left, row), EAST, INPUT, material)
                    board.placeInfinityChest(
                        Coordinate(left + 2, row),
                        InfinitySettings(removeUnfiltered = true),
                    )
                }
            }
            else -> placeChestConnector(material, left, row, input)
        }
    }

    /**
     * Places a chest marker: a five-tile connector whose belt tunnels under the chest, with an
     * inserter on each side. An input marker feeds the belt out of the chest, an output marker
     * fills the chest from the belt, and the plain wooden and steel chests do both at once.
     */
    private fun placeChestConnector(material: Material, left: Int, row: Int, input: Boolean) {
        board.placeEnd(material, Coordinate(left, row), EAST, INPUT)
        board.placeEnd(material, Coordinate(left + 4, row), EAST, OUTPUT)
        board.markTunnel(
            Coordinate(left, row),
            Coordinate(left + 4, row),
            board.endName(material),
        )
        val buffer = beltEnd == BeltEnd.BUFFER_CHEST
        val count = if (buffer) if (input) 100 else 200 else 1
        when {
            beltEnd == BeltEnd.WOODEN_CHEST || beltEnd == BeltEnd.STEEL_CHEST -> {
                // both inserters face west, so the chest buffers a pass-through belt
                placeConnectorInserter(material, Coordinate(left + 1, row), WEST)
                placeConnectorInserter(material, Coordinate(left + 3, row), WEST)
                placeChest(Coordinate(left + 2, row), bar = 4)
            }
            input -> {
                placeConnectorInserter(material, Coordinate(left + 1, row), EAST)
                placeConnectorInserter(material, Coordinate(left + 3, row), WEST, mirror = true)
                placeChest(
                    Coordinate(left + 2, row),
                    requestFilter = RequestFilter(material.name, count, buffer),
                )
            }
            else -> {
                val condition = CircuitCondition(material.name, "<", 200)
                placeConnectorInserter(
                    material,
                    Coordinate(left + 1, row),
                    WEST,
                    circuitCondition = condition,
                )
                placeConnectorInserter(
                    material,
                    Coordinate(left + 3, row),
                    EAST,
                    circuitCondition = condition,
                )
                placeChest(
                    Coordinate(left + 2, row),
                    requestFilter = RequestFilter(material.name, count, buffer),
                    circuitConditionEnabled = false,
                )
            }
        }
    }

    /** Places a chest marker's inserter (always one tile) of the selected tier. */
    private fun placeConnectorInserter(
        material: Material,
        coordinate: Coordinate,
        direction: Direction,
        mirror: Boolean = false,
        circuitCondition: CircuitCondition? = null,
    ) {
        board.place(
            InserterEntity(
                coordinate.area(),
                board.names.inserter,
                material,
                direction,
                long = false,
                mirror = mirror,
                circuitCondition = circuitCondition,
            ),
        )
    }

    /** Places a chest marker (always one tile) of the selected type. */
    private fun placeChest(
        coordinate: Coordinate,
        requestFilter: RequestFilter? = null,
        bar: Int? = null,
        circuitConditionEnabled: Boolean? = null,
    ) {
        board.place(
            ChestEntity(
                coordinate.area(),
                beltEnd.identifier,
                requestFilter = requestFilter,
                bar = bar,
                circuitConditionEnabled = circuitConditionEnabled,
            ),
        )
    }
}

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
