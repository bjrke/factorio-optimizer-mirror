package de.bjrke.factorio.optimizer.common.blueprint.placing.special

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.IMPORT_FEED
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.ChestEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.CircuitCondition
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinitySettings
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.RequestFilter
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.Signal
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeInfinityChest
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeLoader
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeMarker
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeTunnel
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.core.model.Io.INPUT
import de.bjrke.factorio.optimizer.common.core.model.Io.OUTPUT
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.area

/**
 * Places the solid belt's end marker chosen by [beltEnd]: a constant-combinator label, an infinity
 * chest or a chest the inserters feed. A fluid belt never uses this; it always keeps a label.
 */
internal class BeltEndConnector(private val board: LayoutBoard, private val beltEnd: BeltEnd) {

    /**
     * How many tiles the solid belt connector occupies: three for a label or a cheat chest, five
     * for a chest the inserters feed. It hangs off its east side, so [placeSolidConnector] anchors
     * it on the tile east of the strip. An import places it so its east edge is one tile before
     * [IMPORT_FEED]; the target output places it east of the product belt.
     */
    val width
        get() =
            when (beltEnd) {
                BeltEnd.CONSTANT_COMBINATOR,
                BeltEnd.INFINITY_CHEST -> 3
                else -> 5
            }

    /**
     * Places the selected solid belt connector for [material] on [row], ending one tile west of
     * [right]: the strip occupies `right - width until right`. An import passes [IMPORT_FEED], so
     * every marker design starts its belt on that column; the target output passes the column east
     * of the strip, so the connector's west tile `right - width` takes the belt before it. [input]
     * marks a connector that supplies an imported belt, whose first tile is the tunnel input; the
     * target output passes `false` so a cheat chest loads from the belt instead.
     */
    fun placeSolidConnector(material: Material, right: Int, row: Int, input: Boolean) {
        val from = Coordinate(right - 3, row)
        val to = Coordinate(right - 1, row)
        when (beltEnd) {
            BeltEnd.CONSTANT_COMBINATOR -> {
                // the label sits above an underground run, so the belt can pass underneath
                board.placeMarker(Coordinate(right - 2, row), Signal(material))

                board.placeTunnel(material, from, to, EAST)
            }
            BeltEnd.INFINITY_CHEST -> {
                if (input) {
                    board.placeInfinityChest(from, InfinitySettings(item = material))
                    board.placeLoader(Coordinate(right - 2, row), EAST, OUTPUT, material)
                } else {
                    board.placeLoader(from, EAST, INPUT, material)
                    board.placeInfinityChest(to, InfinitySettings(removeUnfiltered = true))
                }
            }
            else -> placeChestConnector(material, right, row, input)
        }
    }

    /**
     * Places a chest marker: a five-tile connector whose belt tunnels under the chest, with an
     * inserter on each side. An input marker feeds the belt out of the chest, an output marker
     * fills the chest from the belt, and the plain wooden and steel chests do both at once.
     */
    private fun placeChestConnector(material: Material, right: Int, row: Int, input: Boolean) {
        board.placeTunnel(
            material,
            Coordinate(right - 5, row),
            Coordinate(right - 1, row),
            EAST,
        )

        val buffer = beltEnd == BeltEnd.BUFFER_CHEST
        val count = if (buffer) if (input) 100 else 200 else 1
        when {
            beltEnd == BeltEnd.WOODEN_CHEST || beltEnd == BeltEnd.STEEL_CHEST -> {
                // both inserters face west, so the chest buffers a pass-through belt
                placeConnectorInserter(material, Coordinate(right - 4, row), WEST)
                placeConnectorInserter(material, Coordinate(right - 2, row), WEST)
                placeChest(Coordinate(right - 3, row), bar = 4)
            }
            input -> {
                placeConnectorInserter(material, Coordinate(right - 4, row), EAST)
                placeConnectorInserter(material, Coordinate(right - 2, row), WEST, mirror = true)
                placeChest(
                    Coordinate(right - 3, row),
                    requestFilter = RequestFilter(material.prototypeName, count, buffer),
                )
            }
            else -> {
                val condition = CircuitCondition(material.prototypeName, "<", 200)
                placeConnectorInserter(
                    material,
                    Coordinate(right - 4, row),
                    WEST,
                    circuitCondition = condition,
                )
                placeConnectorInserter(
                    material,
                    Coordinate(right - 2, row),
                    EAST,
                    circuitCondition = condition,
                )
                placeChest(
                    Coordinate(right - 3, row),
                    requestFilter = RequestFilter(material.prototypeName, count, buffer),
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
