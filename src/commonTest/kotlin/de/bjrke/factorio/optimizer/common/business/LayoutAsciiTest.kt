package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.blueprint.LayoutDump
import de.bjrke.factorio.optimizer.common.blueprint.layoutAscii
import de.bjrke.factorio.optimizer.common.layout.BeltEntity
import de.bjrke.factorio.optimizer.common.layout.ChestEntity
import de.bjrke.factorio.optimizer.common.layout.InserterEntity
import de.bjrke.factorio.optimizer.common.layout.MachineEntity
import de.bjrke.factorio.optimizer.common.layout.MarkerEntity
import de.bjrke.factorio.optimizer.common.layout.PipeEntity
import de.bjrke.factorio.optimizer.common.layout.PoleEntity
import de.bjrke.factorio.optimizer.common.layout.Signal
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.area
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class LayoutAsciiTest {

    private fun material(name: String) = Material(name)

    private val layout =
        LayoutDump(
            entities =
                listOf(
                    MachineEntity(
                        Coordinate(0, 0).area(2, 2),
                        "assembling-machine-3",
                        "advanced-circuit",
                    ),
                    PoleEntity(Coordinate(3, 0).area(), "medium-electric-pole"),
                    InserterEntity(
                        Coordinate(2, 1).area(),
                        "fast-inserter",
                        material("iron-plate"),
                        Direction.NORTH,
                        false,
                    ),
                    PipeEntity(Coordinate(3, 1).area(), "pipe", material("water")),
                    BeltEntity(
                        Coordinate(0, 2).area(),
                        "fast-transport-belt",
                        material("iron-plate"),
                        Direction.EAST,
                    ),
                    MarkerEntity(
                        Coordinate(3, 2).area(),
                        "constant-combinator",
                        Signal(material("copper-plate")),
                    ),
                ),
            failures = listOf("water to (2, 3) (no route)"),
        )

    private val ascii = layoutAscii(layout)

    @Test
    fun machinesAreDigitsAndBeltsShowTheirMaterialCode() =
        assertTableOf("11 +", "11^-", "b  C") { expected ->
            assertContains(ascii, expected, message = ascii)
        }

    @Test
    fun theLegendDecodesMachinesMaterialsAndMarkers() =
        assertTableOf(
            "1 = assembling-machine-3: advanced-circuit",
            // materials are coded in name order: copper-plate=a, iron-plate=b, water=c
            "a = copper-plate",
            "b = iron-plate",
            "c = water",
            "C = constant-combinator: copper-plate",
        ) { expected ->
            assertContains(ascii, expected, message = ascii)
        }

    @Test
    fun theFailedRoutesAreListedBelowTheGrid() =
        assertTableOf("failures (1)", "water to (2, 3) (no route)") { expected ->
            assertContains(ascii, expected, message = ascii)
        }

    @Test
    fun anEmptyLayoutRendersNothingButAHeader() =
        assertEquals("layout: empty\n", layoutAscii(LayoutDump(emptyList(), emptyList())))

    @Test
    fun chestMarkersGetTheirOwnGlyphAndLegend() {
        val dump =
            LayoutDump(
                entities =
                    listOf(
                        ChestEntity(Coordinate(0, 0).area(), "buffer-chest"),
                        ChestEntity(Coordinate(1, 0).area(), "wooden-chest"),
                    ),
                failures = emptyList(),
            )
        val text = layoutAscii(dump)

        assertTableOf("BW", "B = buffer-chest", "W = wooden-chest") { expected ->
            assertContains(text, expected, message = text)
        }
    }
}
