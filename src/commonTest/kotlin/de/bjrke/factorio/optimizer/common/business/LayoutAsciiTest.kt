package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
                        Direction.NORTH,
                        false,
                    ),
                    PipeEntity(Coordinate(3, 1).area(), "pipe", material("water")),
                    BeltEntity(
                        Coordinate(0, 2).area(),
                        "fast-transport-belt",
                        Direction.EAST,
                        material("iron-plate"),
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
        assertAll(
            { assertTrue(ascii.contains("11 +"), ascii) },
            { assertTrue(ascii.contains("11^-"), ascii) },
            { assertTrue(ascii.contains("b  C"), ascii) },
        )

    @Test
    fun theLegendDecodesMachinesMaterialsAndMarkers() =
        assertAll(
            { assertTrue(ascii.contains("1 = assembling-machine-3: advanced-circuit"), ascii) },
            // materials are coded in name order: copper-plate=a, iron-plate=b, water=c
            { assertTrue(ascii.contains("a = copper-plate"), ascii) },
            { assertTrue(ascii.contains("b = iron-plate"), ascii) },
            { assertTrue(ascii.contains("c = water"), ascii) },
            { assertTrue(ascii.contains("C = constant-combinator: copper-plate"), ascii) },
        )

    @Test
    fun theFailedRoutesAreListedBelowTheGrid() =
        assertAll(
            { assertTrue(ascii.contains("failures (1)"), ascii) },
            { assertTrue(ascii.contains("water to (2, 3) (no route)"), ascii) },
        )

    @Test
    fun aBeltWithNoTrackedMaterialShowsItsDirection() {
        val dump =
            LayoutDump(
                listOf(
                    BeltEntity(
                        Coordinate(0, 0).area(),
                        "fast-transport-belt",
                        Direction.WEST,
                        null,
                    ),
                ),
                emptyList(),
            )

        val text = layoutAscii(dump)

        assertAll(
            { assertTrue(text.contains("<"), text) },
            { assertFalse(text.contains("failures"), text) },
        )
    }

    @Test
    fun anEmptyLayoutRendersNothingButAHeader() =
        assertEquals("layout: empty\n", layoutAscii(LayoutDump(emptyList(), emptyList())))
}
