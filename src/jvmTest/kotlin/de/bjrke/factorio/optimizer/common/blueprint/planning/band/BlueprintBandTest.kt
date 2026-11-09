package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedLink
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.planned
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.InserterTier
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals

/** Direct-feed bands, where a producer inserts straight into its consumer. */
class BlueprintBandTest {

    @Test
    fun aBandFeedsItsConsumerMachineToMachine() {
        val gearRow = planned(ironGearWheelRecipe, Factory.ASSEMBLING_MACHINE_3)
        val plateRow = planned(ironPlateRecipe, Factory.ASSEMBLING_MACHINE_3)
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
                                    PlannedLink(0, ironPlate, flow = 0.1, producerIsLeft = false),
                                ),
                        ),
                    ),
                sources = listOf(PlannedSource(ironOre, 1.0, fluid = false)),
                label = "iron-gear-wheel",
            )
        val layout = Layout(bandPlan)
        val blueprintEntities = layout.blueprint.entities
        val machines = blueprintEntities.filter {
            it.name == Factory.ASSEMBLING_MACHINE_3.identifier
        }
        val left = machines.minBy { it.area.topLeft.x }
        val gap = left.area.topLeft.x + left.area.dimension.width
        val interfaces =
            blueprintEntities
                .filter { it.name == InserterTier.FAST_INSERTER.identifier }
                .filter { it.area.topLeft.x == gap }

        assertAll(
            { assertEquals(2, machines.size) },
            { assertEquals(1, machines.map { it.area.topLeft.y }.distinct().size) },
            // the inserter picks the plate up on the east side, where the producer stands
            {
                assertEquals(
                    setOf(ironPlate),
                    interfaces.mapNotNull { layout.beltMaterials[it.area.topLeft] }.toSet(),
                )
            },
            { assertEquals(emptyList(), interfaces.filterNot { it.direction == EAST }) },
            // the plate never reaches a belt: it is inserted straight into the consumer
            {
                assertEquals(
                    emptyList(),
                    blueprintEntities
                        .filter { it.name.endsWith("transport-belt") }
                        .mapNotNull { layout.beltMaterials[it.area.topLeft] }
                        .filter { it == ironPlate },
                )
            },
        )
    }
}
