package de.bjrke.factorio.optimizer.common.band

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.blueprintTransportMaterials
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.craftRecipe
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.gear
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.logistics
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.ore
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.planned
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.plate
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.smeltRecipe
import de.bjrke.factorio.optimizer.common.blueprint.fromPlaced
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.plan.PlannedLink
import de.bjrke.factorio.optimizer.common.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.prototype.InserterTier
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Direct-feed bands, where a producer inserts straight into its consumer. */
class BlueprintBandTest {

    @Test
    fun aBandFeedsItsConsumerMachineToMachine() {
        val gearRow = planned(craftRecipe, gear, Factory.ASSEMBLING_MACHINE_3)
        val plateRow = planned(smeltRecipe, plate, Factory.ASSEMBLING_MACHINE_3)
        val bandPlan =
            BlueprintPlan(
                target = gear,
                // the gear machine takes its plate from the smelter on its right
                rows =
                    listOf(
                        PlannedRow(
                            machines = listOf(gearRow, plateRow),
                            links =
                                listOf(
                                    PlannedLink(0, plate, flow = 0.1, producerIsLeft = false),
                                ),
                        ),
                    ),
                sources = listOf(PlannedSource(ore, 1.0, fluid = false)),
                scale = 1.0,
                label = "iron-gear-wheel",
            )
        val blueprint = fromPlaced(bandPlan, logistics)
        val materials = blueprintTransportMaterials(bandPlan, logistics)
        val machines =
            blueprint.entities.filter { it.name == Factory.ASSEMBLING_MACHINE_3.identifier }
        val left = machines.minBy { it.area.topLeft.x }
        val gap = left.area.topLeft.x + left.area.dimension.width
        val interfaces =
            blueprint.entities
                .filter { it.name == InserterTier.FAST_INSERTER.identifier }
                .filter { it.area.topLeft.x == gap }

        assertAll(
            { assertEquals(2, machines.size) },
            { assertEquals(1, machines.map { it.area.topLeft.y }.distinct().size) },
            // the inserter picks the plate up on the east side, where the producer stands
            {
                assertEquals(
                    setOf(plate),
                    interfaces.mapNotNull { materials[it.area.topLeft] }.toSet(),
                )
            },
            { assertTrue(interfaces.all { it.direction == EAST }) },
            // the plate never reaches a belt: it is inserted straight into the consumer
            {
                assertEquals(
                    emptyList(),
                    blueprint.entities
                        .filter { it.name.endsWith("transport-belt") }
                        .mapNotNull { materials[it.area.topLeft] }
                        .filter { it == plate },
                )
            },
        )
    }
}
