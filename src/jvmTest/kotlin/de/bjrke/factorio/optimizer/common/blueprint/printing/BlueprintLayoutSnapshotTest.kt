package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedLink
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.basePlan
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.planned
import de.bjrke.factorio.optimizer.common.blueprint.printing.model.Blueprint
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateRecipe
import de.bjrke.factorio.optimizer.common.fixtures.petroleum
import de.bjrke.factorio.optimizer.common.fixtures.solidFuel
import de.bjrke.factorio.optimizer.common.fixtures.solidFuelFromPetroleumGasRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Characterization tests for the layout pipeline: they pin the exact entity order and every entity
 * field, so a refactor that changes geometry, wiring or placement order fails loudly.
 */
class BlueprintLayoutSnapshotTest {

    @Test fun baseLayoutIsStable() = Layout(basePlan).assertSignature("577596b91c039b08")

    @Test
    fun chestBeltEndIsStable() =
        Layout(basePlan, beltEnd = BeltEnd.BUFFER_CHEST).assertSignature("3c6ccdc7d3988898")

    @Test
    fun fluidLayoutIsStable() {
        val plan =
            BlueprintPlan(
                target = solidFuel,
                rows =
                    listOf(
                        PlannedRow.copies(
                            planned(
                                solidFuelFromPetroleumGasRecipe,
                                solidFuel,
                                Factory.CHEMICAL_PLANT,
                            ),
                            1,
                        ),
                    ),
                sources = listOf(PlannedSource(petroleum, 1.0, fluid = true)),
                scale = 1.0,
                label = "solid-fuel-from-petroleum-gas",
            )
        Layout(plan).assertSignature("749bb1b2462f902c")
    }

    @Test
    fun bandLayoutIsStable() {
        val gearRow = planned(ironGearWheelRecipe, gear, Factory.ASSEMBLING_MACHINE_3)
        val plateRow = planned(ironPlateRecipe, ironPlate, Factory.ASSEMBLING_MACHINE_3)
        val plan =
            BlueprintPlan(
                target = gear,
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
                scale = 1.0,
                label = "iron-gear-wheel",
            )
        Layout(plan).assertSignature("94533f5e06df28d2")
    }

    private fun Layout.assertSignature(expected: String) {
        assertEquals(
            expected,
            signature(blueprint),
            "layout changed; the canonical form is:\n${canonical(blueprint)}",
        )
    }

    private fun signature(blueprint: Blueprint): String {
        var hash = -0x340d631b7bdddcdbL
        for (char in canonical(blueprint)) {
            hash = hash xor char.code.toLong()
            hash *= 0x100000001b3L
        }
        return hash.toULong().toString(16).padStart(16, '0')
    }

    private fun canonical(blueprint: Blueprint) = buildString {
        append(blueprint.label).append('\n')
        append(blueprint.icon).append('\n')
        blueprint.entities.forEach { append(it).append('\n') }
        blueprint.wires.forEach { append(it).append('\n') }
    }
}
