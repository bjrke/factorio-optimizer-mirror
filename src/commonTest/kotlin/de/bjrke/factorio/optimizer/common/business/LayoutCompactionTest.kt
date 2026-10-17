package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.factorio.optimizer.common.model.Direction.*
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val BELT = "express-transport-belt"
private const val UNDERGROUND_BELT = "express-underground-belt"
private const val PIPE = "pipe"
private const val MARKER = "constant-combinator"
private const val MACHINE = "assembling-machine-3"

private val ore = Material("iron-ore")
private val plate = Material("iron-plate")
private val water = Material("water")
private val oil = Material("crude-oil")

private fun belt(x: Int, y: Int, material: Material = ore, direction: Direction = EAST) =
    BeltEntity(AreaTile(x, y), BELT, material, direction)

private fun marker(x: Int, y: Int, material: Material = ore) =
    MarkerEntity(AreaTile(x, y), MARKER, Signal(material))

private fun machine(x: Int, y: Int) =
    MachineEntity(Area(Coordinate(x, y), Dimension(3, 3)), MACHINE, "test")

private fun pipe(x: Int, y: Int, fluid: Material = water) = PipeEntity(AreaTile(x, y), PIPE, fluid)

private fun underground(
    x: Int,
    y: Int,
    direction: Direction,
    io: Io,
    material: Material = ore,
) = UndergroundBeltEntity(AreaTile(x, y), UNDERGROUND_BELT, material, direction, io)

class LayoutCompactionTest {

    @Test
    fun theWidestEmptyCorridorIsRemovedInOneShift() =
        assertTableOf(SeamOrientation.VERTICAL, SeamOrientation.HORIZONTAL) { orientation ->
            // two non-removable markers keep the outer belts from being cut themselves
            val entities =
                if (orientation == SeamOrientation.VERTICAL) {
                    listOf(
                        marker(-1, 0),
                        belt(0, 0),
                        belt(5, 0),
                        marker(6, 0),
                    )
                } else {
                    listOf(
                        marker(0, -1),
                        belt(0, 0, direction = SOUTH),
                        belt(0, 5, direction = SOUTH),
                        marker(0, 6),
                    )
                }

            val result = assertNotNull(nextCompaction(entities, orientation))
            val belts = result.entities.filterIsInstance<BeltEntity>()

            assertAll(
                { assertEquals(4, result.width, "corridor width") },
                { assertEquals(2, belts.size, "belts kept") },
                { assertEquals(emptyList(), validateLayout(result.entities)) },
                {
                    assertEquals(
                        1,
                        belts[0].area.topLeft.manhattanDist(belts[1].area.topLeft),
                        "the two belts meet after the splice",
                    )
                },
            )
        }

    @Test
    fun theWholeEmptyGapIsRemovedInOneShift() {
        val result =
            assertNotNull(
                nextCompaction(listOf(machine(0, 0), machine(0, 10)), SeamOrientation.HORIZONTAL),
            )
        val machines = result.entities.filterIsInstance<MachineEntity>().sortedBy { it.area.y }

        assertAll(
            { assertEquals(7, result.width, "the seven empty rows go at once") },
            { assertEquals(2, machines.size) },
            { assertEquals(3, machines[1].area.y, "the lower machine slides up to the upper one") },
            { assertEquals(emptyList(), validateLayout(result.entities)) },
        )
    }

    @Test
    fun differentMaterialsAreNeverMerged() {
        val entities = listOf(marker(-1, 0), belt(0, 0, ore), belt(5, 0, plate), marker(6, 0))
        val result = assertNotNull(nextCompaction(entities, SeamOrientation.VERTICAL))
        val belts = result.entities.filterIsInstance<BeltEntity>().sortedBy { it.area.topLeft.x }

        assertAll(
            { assertTrue(result.width < 4, "a narrower corridor avoids the merge") },
            { assertEquals(emptyList(), validateLayout(result.entities)) },
            { assertTrue(belts[0].area.topLeft.manhattanDist(belts[1].area.topLeft) >= 2) },
        )
    }

    @Test
    fun aMaterialRunningIntoAnotherIsRejected() {
        val errors = validateLayout(listOf(belt(0, 0, ore), belt(1, 0, plate)))
        assertContains(errors.joinToString("\n"), "runs into", message = errors.toString())
    }

    @Test
    fun differentFluidsAreNeverMerged() {
        val entities = listOf(marker(-1, 0), pipe(0, 0, water), pipe(5, 0, oil), marker(6, 0))
        val result = assertNotNull(nextCompaction(entities, SeamOrientation.VERTICAL))
        val pipes = result.entities.filterIsInstance<PipeEntity>().sortedBy { it.area.topLeft.x }

        assertAll(
            { assertTrue(result.width < 4, "a narrower corridor avoids the merge") },
            { assertEquals(emptyList(), validateLayout(result.entities)) },
            { assertTrue(pipes[0].area.topLeft.manhattanDist(pipes[1].area.topLeft) >= 2) },
        )
    }

    @Test
    fun touchingFluidsAreRejected() {
        val errors = validateLayout(listOf(pipe(0, 0, water), pipe(1, 0, oil)))
        assertContains(errors.joinToString("\n"), "touch", message = errors.toString())
    }

    private data class InserterCase(
        val label: String,
        val source: LayoutEntity?,
        val valid: Boolean,
    )

    @Test
    fun anInserterNeedsAMachineOrAMatchingTransportAsSource() =
        assertTableOf(
            InserterCase("empty", null, valid = false),
            InserterCase("marker", marker(0, -2), valid = false),
            InserterCase("belt", belt(0, -2, ore), valid = true),
            InserterCase("wrong belt", belt(0, -2, plate), valid = false),
            InserterCase("pipe", pipe(0, -2, water), valid = false),
        ) { case ->
            val entities = buildList {
                add(machine(0, 0))
                add(InserterEntity(AreaTile(0, -1), "fast-inserter", ore, NORTH, long = false))
                case.source?.let(::add)
            }
            val errors = validateLayout(entities)
            if (case.valid) {
                assertEquals(emptyList(), errors)
            } else {
                assertContains(
                    errors.joinToString("\n"),
                    "inserter at",
                    message = errors.toString(),
                )
            }
        }

    private data class UndergroundCase(
        val label: String,
        val x: Int,
        val y: Int,
        val valid: Boolean,
    )

    @Test
    fun undergroundEndsMustStayPairedAndAligned() =
        assertTableOf(
            UndergroundCase("aligned", 3, 0, valid = true),
            UndergroundCase("not aligned", 3, 1, valid = false),
            UndergroundCase("too short", 1, 0, valid = false),
            UndergroundCase("too far", 12, 0, valid = false),
        ) { case ->
            val entities =
                listOf(
                    underground(0, 0, EAST, Io.INPUT),
                    underground(case.x, case.y, EAST, Io.OUTPUT),
                )
            val errors = validateLayout(entities)
            if (case.valid) {
                assertEquals(emptyList(), errors)
            } else {
                assertContains(errors.joinToString("\n"), "unpaired", message = errors.toString())
            }
        }

    @Test
    fun theWestBoundKeepsTheFactoryEdgeInPlace() {
        val entities = listOf(machine(0, 0), belt(5, 0), marker(6, 0))
        val free = assertNotNull(nextCompaction(entities, SeamOrientation.VERTICAL))
        val bounded =
            assertNotNull(
                nextCompaction(entities, SeamOrientation.VERTICAL, westBound = 4),
            )

        assertAll(
            {
                assertEquals(
                    3,
                    free.entities.filterIsInstance<BeltEntity>().single().area.topLeft.x,
                    "the unbound seam pulls the belt west of the bound",
                )
            },
            {
                assertEquals(
                    4,
                    bounded.entities.filterIsInstance<BeltEntity>().single().area.topLeft.x,
                    "the bounded seam still moved the belt west of the bound",
                )
            },
            { assertEquals(emptyList(), validateLayout(bounded.entities)) },
        )
    }

    @Test
    fun aPackedLayoutHasNoCorridor() =
        assertTableOf(SeamOrientation.VERTICAL, SeamOrientation.HORIZONTAL) { orientation ->
            assertNull(nextCompaction(listOf(machine(0, 0)), orientation))
        }

    @Test
    fun aPinnedImportGroupIsNeverSplit() {
        // two import stubs west of an empty gap; the group (markers and feeds) must keep its column
        val entities =
            listOf(
                marker(0, 0),
                belt(1, 0),
                marker(0, 5),
                belt(1, 5),
                marker(7, 0),
                belt(6, 0),
                marker(7, 5),
                belt(6, 5),
            )
        val pinned = setOf(Coordinate(0, 0), Coordinate(1, 0), Coordinate(0, 5), Coordinate(1, 5))

        val result =
            assertNotNull(nextCompaction(entities, SeamOrientation.VERTICAL, pinned = pinned))
        val group =
            result.entities.filter { it.area.topLeft.x <= 1 }.map { it.area.topLeft }.toSet()

        assertAll(
            { assertEquals(emptyList(), validateLayout(result.entities)) },
            { assertEquals(pinned, group, "the pinned group kept its column") },
        )
    }
}
