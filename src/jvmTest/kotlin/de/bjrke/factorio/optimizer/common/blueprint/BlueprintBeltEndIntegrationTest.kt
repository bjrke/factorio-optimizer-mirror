package de.bjrke.factorio.optimizer.common.blueprint

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.assertConnectedUndergroundBelts
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.assertNoOverlap
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.assertPolesCoverAndConnect
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.chestMarkers
import de.bjrke.factorio.optimizer.common.layout.BeltEnd
import de.bjrke.test.assertAll
import de.bjrke.test.assertTable
import kotlin.test.Test
import kotlin.test.assertEquals

/** The real dumps with each chest belt end: overlap, tunnels, power and the output wire. */
class BlueprintBeltEndIntegrationTest {

    @Test
    fun realChestMarkerBlueprintsHaveNoOverlappingEntities() =
        assertTable(chestMarkers) { beltEnd ->
            assertNoOverlap(Layout("electronic-circuit", beltEnd = beltEnd).blueprint.entities)
        }

    @Test
    fun realChestMarkerBlueprintsHaveConnectedUndergroundBelts() =
        assertTable(chestMarkers) { beltEnd ->
            assertConnectedUndergroundBelts(
                Layout("advanced-circuit", beltEnd = beltEnd).blueprint.entities,
            )
        }

    @Test
    fun realChestMarkerBlueprintsPowerEveryInserter() =
        assertTable(chestMarkers) { beltEnd ->
            assertPolesCoverAndConnect(
                Layout("advanced-circuit", beltEnd = beltEnd).blueprint.entities,
            )
        }

    @Test
    fun theRealChestOutputWiresItsInsertersToTheChest() {
        val blueprint =
            Layout(target = "advanced-circuit", beltEnd = BeltEnd.BUFFER_CHEST).blueprint
        val chest =
            blueprint.entities.withIndex().single { (_, entity) ->
                entity.name == "buffer-chest" && entity.circuitConditionEnabled == false
            }
        val chestNumber = chest.index + 1
        val wired = blueprint.wires.flatMap { listOf(it.first, it.second) }.toSet()

        assertAll(
            { assertEquals(2, blueprint.wires.size) },
            { assertEquals(setOf(chestNumber - 2, chestNumber - 1, chestNumber), wired) },
        )
    }
}
