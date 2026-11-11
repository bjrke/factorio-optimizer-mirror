package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.assertConnectedUndergroundBelts
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.assertNoOverlap
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.assertPolesCoverAndConnect
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.chestMarkers
import de.bjrke.test.assertAll
import de.bjrke.test.assertTable
import kotlin.test.Test
import kotlin.test.assertEquals

/** The real dumps with each chest belt end: overlap, tunnels, power and the output wire. */
class BlueprintBeltEndIntegrationTest {

    @Test
    fun realChestMarkerBlueprintsHaveNoOverlappingEntities() =
        assertTable(chestMarkers) { beltEnd ->
            val layout = Layout("electronic-circuit", beltEnd = beltEnd)
            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertNoOverlap(layout.blueprint.entities) },
            )
        }

    @Test
    fun realChestMarkerBlueprintsHaveConnectedUndergroundBelts() =
        assertTable(chestMarkers) { beltEnd ->
            val layout = Layout("advanced-circuit", beltEnd = beltEnd)
            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertConnectedUndergroundBelts(layout.blueprint.entities) },
            )
        }

    @Test
    fun realChestMarkerBlueprintsPowerEveryInserter() =
        assertTable(chestMarkers) { beltEnd ->
            val layout = Layout("advanced-circuit", beltEnd = beltEnd)
            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertPolesCoverAndConnect(layout.blueprint.entities) },
            )
        }

    @Test
    fun theRealChestOutputWiresItsInsertersToTheChest() {
        val layout = Layout(target = "advanced-circuit", beltEnd = BeltEnd.BUFFER_CHEST)
        val blueprint = layout.blueprint
        val chest =
            blueprint.entities.withIndex().single { (_, entity) ->
                entity.name == "buffer-chest" && entity.circuitConditionEnabled == false
            }
        val chestNumber = chest.index + 1
        val wired = blueprint.wires.flatMap { listOf(it.first, it.second) }.toSet()

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(2, blueprint.wires.size) },
            { assertEquals(setOf(chestNumber - 2, chestNumber - 1, chestNumber), wired) },
        )
    }
}
