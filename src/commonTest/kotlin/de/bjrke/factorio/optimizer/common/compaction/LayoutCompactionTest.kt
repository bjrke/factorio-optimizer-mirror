package de.bjrke.factorio.optimizer.common.compaction

import de.bjrke.factorio.optimizer.common.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.layout.entity.PipeEntity
import de.bjrke.factorio.optimizer.common.layout.entity.Signal
import de.bjrke.factorio.optimizer.common.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.model.Area
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Dimension
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Direction.NORTH
import de.bjrke.factorio.optimizer.common.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.areaTile
import de.bjrke.factorio.optimizer.common.model.manhattanDist
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
    BeltEntity(areaTile(x, y), BELT, material, direction)

private fun marker(x: Int, y: Int, material: Material = ore) =
    MarkerEntity(areaTile(x, y), MARKER, Signal(material))

private fun machine(x: Int, y: Int) =
    MachineEntity(Area(Coordinate(x, y), Dimension(3, 3)), MACHINE, "test")

private fun pipe(x: Int, y: Int, fluid: Material = water) = PipeEntity(areaTile(x, y), PIPE, fluid)

private fun underground(
    x: Int,
    y: Int,
    direction: Direction,
    io: Io,
    material: Material = ore,
) = UndergroundBeltEntity(areaTile(x, y), UNDERGROUND_BELT, material, direction, io)

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
                add(InserterEntity(areaTile(0, -1), "fast-inserter", ore, NORTH, long = false))
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
            val entities = buildList {
                add(belt(-1, 0))
                add(underground(0, 0, EAST, Io.INPUT))
                add(underground(case.x, case.y, EAST, Io.OUTPUT))
                add(belt(case.x + 1, case.y))
            }
            val errors = validateLayout(entities)
            if (case.valid) {
                assertEquals(emptyList(), errors)
            } else {
                assertContains(errors.joinToString("\n"), "unpaired", message = errors.toString())
            }
        }

    private data class TunnelFeedCase(
        val label: String,
        val back: LayoutEntity?,
        val valid: Boolean,
    )

    @Test
    fun aRoutedTunnelEntranceNeedsABeltHeadingIntoIt() =
        assertTableOf(
            TunnelFeedCase("straight belt", belt(0, 0, direction = SOUTH), valid = true),
            TunnelFeedCase("sideways belt", belt(0, 0, direction = EAST), valid = false),
            TunnelFeedCase("no belt", null, valid = false),
            TunnelFeedCase(
                "tunnel exit",
                underground(0, 0, SOUTH, Io.OUTPUT),
                valid = false,
            ),
        ) { case ->
            val entities = buildList {
                case.back?.let(::add)
                add(underground(0, 1, SOUTH, Io.INPUT))
                add(underground(0, 4, SOUTH, Io.OUTPUT))
                add(belt(0, 5, direction = SOUTH))
            }
            val errors = validateLayout(entities)
            if (case.valid) {
                assertEquals(emptyList(), errors)
            } else {
                assertContains(
                    errors.joinToString("\n"),
                    "is not fed straight",
                    message = errors.toString(),
                )
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

    /**
     * A corridor made only of empty tiles would leave one side of the layout empty. It must never
     * count as a shrink, so the compaction returns no result instead of an empty layout.
     */
    @Test
    fun aCorridorOfOnlyEmptyTilesIsNotAShrink() =
        assertTableOf(SeamOrientation.VERTICAL, SeamOrientation.HORIZONTAL) { orientation ->
            // one marker at the west/north edge: every corridor beyond it is empty
            assertNull(nextCompaction(listOf(marker(0, 0)), orientation))
        }

    /**
     * An import feed side-loads the westbound lane above it. Pinning only the connector lets a wide
     * corridor cut from the feed tile to the lane's end, which slides the far side past the feed
     * and leaves the material on the wrong lane tile. Pinning the lane tile the feed must reach
     * keeps the corridor narrower, so the feed still lands where the route put it.
     */
    @Test
    fun aPinnedLaneTileKeepsTheCorridorOffTheFeed() {
        val entities = sideLoadedImport()
        val connectorOnly = setOf(Coordinate(1, 1), Coordinate(2, 1))
        val withLaneTile = connectorOnly + Coordinate(4, 0)

        val loose =
            assertNotNull(
                nextCompaction(entities, SeamOrientation.VERTICAL, pinned = connectorOnly),
            )
        val tight =
            assertNotNull(nextCompaction(entities, SeamOrientation.VERTICAL, pinned = withLaneTile))

        assertAll(
            {
                assertEquals(emptyList(), validateLayout(entities), "the fixture is a valid layout")
            },
            { assertTrue(tight.width < loose.width, "the lane pin narrows the corridor") },
            { assertEquals(emptyList(), validateLayout(tight.entities)) },
        )
    }

    /**
     * A westbound lane in row 0, wall to wall, side-loaded from row 1. The connector marker sits at
     * (1, 1), its feed at (2, 1) and the feed side-loads (3, 0); (4, 0) is the lane tile that must
     * keep receiving the material. Row 2 is wall to wall, so the only corridor is the gap at (4,
     * 1).
     */
    private fun sideLoadedImport(): List<LayoutEntity> = buildList {
        add(marker(0, 0))
        for (x in 1..9) add(belt(x, 0, direction = Direction.WEST))
        add(marker(10, 0))
        add(marker(1, 1))
        add(belt(2, 1))
        add(belt(3, 1))
        add(belt(5, 1))
        add(marker(6, 1))
        add(marker(1, 2))
        for (x in 2..9) add(belt(x, 2))
        add(marker(10, 2))
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
