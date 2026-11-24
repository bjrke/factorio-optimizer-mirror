package de.bjrke.factorio.optimizer.common.blueprint.placing.routing

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.IMPORT_FEED
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutBoard
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutNames
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.ChestEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityChestEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeBelt
import de.bjrke.factorio.optimizer.common.blueprint.layout.placeTunnel
import de.bjrke.factorio.optimizer.common.blueprint.placing.special.BeltEndConnector
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.ImportExtension
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.ImportFeed
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.core.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.copperPlate
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.test.assertTable
import kotlin.test.Test
import kotlin.test.assertEquals

private const val ROW = 10
private const val FEED_COLUMN = 30
private const val FOREIGN_COLUMN = FEED_COLUMN - 2

/**
 * Every solid marker design the trimmer has to align: a three-tile label and the five-tile chests.
 */
private val MARKER_DESIGNS =
    listOf(
        BeltEnd.CONSTANT_COMBINATOR,
        BeltEnd.INFINITY_CHEST,
        BeltEnd.BUFFER_CHEST,
        BeltEnd.STORAGE_CHEST,
        BeltEnd.WOODEN_CHEST,
        BeltEnd.STEEL_CHEST,
    )

class ImportTrimmerTest : BaseGameCtxTest() {

    private inner class ImportFixture(
        val beltEnd: BeltEnd,
        val board: LayoutBoard,
        val imports: ImportExtension,
        val connector: BeltEndConnector,
    ) {
        fun trim() = ImportTrimmer(board, connector, beltEnd, imports).trim()

        /**
         * The feed tile east of the marker strip, i.e. the column the trimmer aligns every marker
         * on: the chest designs sit three tiles west of it, the label two (see
         * `BeltEndConnector.placeSolidConnector`).
         */
        fun column(): Int =
            when (beltEnd) {
                BeltEnd.CONSTANT_COMBINATOR ->
                    board.placed
                        .filterIsInstance<MarkerEntity>()
                        .single { it.signal.material == ironPlate }
                        .area
                        .topLeft
                        .x + 2
                BeltEnd.INFINITY_CHEST ->
                    board.placed
                        .filterIsInstance<InfinityChestEntity>()
                        .single { it.settings.item == ironPlate }
                        .area
                        .topLeft
                        .x + 3
                else ->
                    board.placed
                        .filterIsInstance<ChestEntity>()
                        .single { it.area.y == ROW }
                        .area
                        .topLeft
                        .x + 3
            }
    }

    /** Registers an import on [row] without placing its belt, so a test can shape that row. */
    private fun ImportFixture.claimImportRow(row: Int, material: Material = ironPlate) {
        imports.importFeeds +=
            ImportFeed(
                material,
                Coordinate(IMPORT_FEED, row),
                Coordinate(FEED_COLUMN, row),
            )
    }

    /** A straight imported belt with its inline marker, ready for the trimmer. */
    private fun importFixture(beltEnd: BeltEnd = BeltEnd.CONSTANT_COMBINATOR): ImportFixture {
        val board =
            LayoutBoard(
                names =
                    LayoutNames(
                        belt = "transport-belt",
                        inserter = "inserter",
                        longInserter = "long-handed-inserter",
                    ),
                undergroundRange = 5,
                fluidPredicate = { false },
                temperatureOf = { 0 },
            )
        val imports = ImportExtension()
        val connector = BeltEndConnector(board, beltEnd)
        connector.placeSolidConnector(ironPlate, IMPORT_FEED, ROW, input = true)
        for (x in IMPORT_FEED..FEED_COLUMN) {
            board.placeBelt(Coordinate(x, ROW), ironPlate, EAST)
        }
        board.reserve(Coordinate(IMPORT_FEED, ROW), ironPlate)
        imports.markerRows += ROW
        imports.importFeeds +=
            ImportFeed(
                ironPlate,
                Coordinate(IMPORT_FEED, ROW),
                Coordinate(FEED_COLUMN, ROW),
            )
        return ImportFixture(beltEnd, board, imports, connector)
    }

    @Test
    fun everyMarkerDesignTrimsToTheStraightEnd() {
        assertTable(MARKER_DESIGNS) { beltEnd ->
            val fixture = importFixture(beltEnd)

            fixture.trim()

            assertEquals(FEED_COLUMN, fixture.column())
        }
    }

    @Test
    fun everyMarkerDesignStaysWestOfAForeignEntity() {
        assertTable(MARKER_DESIGNS) { beltEnd ->
            val fixture = importFixture(beltEnd)
            fixture.board.placeBelt(Coordinate(FOREIGN_COLUMN, ROW + 1), ironPlate, EAST)

            fixture.trim()

            assertEquals(FOREIGN_COLUMN, fixture.column())
        }
    }

    @Test
    fun aForeignBeltOnAnotherRowInTheMarkerColumnMovesTheMarkerWest() {
        val fixture = importFixture()
        fixture.board.placeBelt(Coordinate(FOREIGN_COLUMN, ROW + 1), ironPlate, EAST)

        fixture.trim()

        assertEquals(FOREIGN_COLUMN, fixture.column())
    }

    @Test
    fun aForeignTunnelCrossingTheImportRowMovesTheMarkerWestOfIt() {
        val fixture = importFixture()
        // the entries stand above and below the import row; only the buried span crosses it
        fixture.board.placeBelt(Coordinate(FOREIGN_COLUMN, ROW - 3), ironPlate, SOUTH)
        fixture.board.placeTunnel(
            ironPlate,
            Coordinate(FOREIGN_COLUMN, ROW - 2),
            Coordinate(FOREIGN_COLUMN, ROW + 2),
            SOUTH,
        )
        fixture.board.placeBelt(Coordinate(FOREIGN_COLUMN, ROW + 3), ironPlate, SOUTH)

        fixture.trim()

        assertEquals(FOREIGN_COLUMN, fixture.column())
    }

    @Test
    fun aForeignBeltWestOfTheMarkerColumnMovesTheMarkerWestOfIt() {
        val fixture = importFixture()
        fixture.board.placeBelt(Coordinate(IMPORT_FEED + 1, ROW + 1), ironPlate, EAST)

        fixture.trim()

        assertEquals(IMPORT_FEED + 1, fixture.column())
    }

    @Test
    fun aStraightEastBeltOnAnImportRowDoesNotBlockTheTrim() {
        val fixture = importFixture()

        fixture.trim()

        assertEquals(FEED_COLUMN, fixture.column())
    }

    @Test
    fun aBeltAgainstTheImportDirectionOnAnImportRowBlocksTheTrim() {
        val fixture = importFixture()
        fixture.claimImportRow(ROW + 1)
        fixture.board.placeBelt(Coordinate(FOREIGN_COLUMN, ROW + 1), ironPlate, WEST)

        fixture.trim()

        assertEquals(FOREIGN_COLUMN, fixture.column())
    }

    @Test
    fun aBeltOfAnotherMaterialOnAnImportRowBlocksTheTrim() {
        val fixture = importFixture()
        fixture.claimImportRow(ROW + 1, copperPlate)
        fixture.board.placeBelt(Coordinate(FOREIGN_COLUMN, ROW + 1), ironPlate, EAST)

        fixture.trim()

        assertEquals(FOREIGN_COLUMN, fixture.column())
    }
}
