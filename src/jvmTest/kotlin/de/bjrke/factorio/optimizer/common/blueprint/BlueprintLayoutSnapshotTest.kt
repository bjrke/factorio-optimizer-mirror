package de.bjrke.factorio.optimizer.common.blueprint

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.basePlan
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.craftRecipe
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.gear
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.logistics
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.ore
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.planned
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.plate
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.recipe
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.smeltRecipe
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.water
import de.bjrke.factorio.optimizer.common.blueprint.model.Blueprint
import de.bjrke.factorio.optimizer.common.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.plan.PlannedLink
import de.bjrke.factorio.optimizer.common.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.prototype.Category
import de.bjrke.factorio.optimizer.common.prototype.Factory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Characterization tests for the layout pipeline: they pin the exact entity order and every entity
 * field, so a refactor that changes geometry, wiring or placement order fails loudly.
 */
class BlueprintLayoutSnapshotTest {

    @Test
    fun baseLayoutIsStable() = assertSignature("1342c18d851e3bfc", fromPlaced(basePlan, logistics))

    @Test
    fun chestBeltEndIsStable() =
        assertSignature(
            "3304c4b8e48c28fe",
            fromPlaced(basePlan, logistics, beltEnd = BeltEnd.BUFFER_CHEST),
        )

    @Test
    fun fluidLayoutIsStable() {
        val boil = recipe("water-boil", Category.CHEMISTRY, mapOf(gear to 1.0), mapOf(water to 1.0))
        val plan =
            BlueprintPlan(
                target = gear,
                rows = listOf(PlannedRow.copies(planned(boil, gear, Factory.CHEMICAL_PLANT), 1)),
                sources = listOf(PlannedSource(water, 1.0, fluid = true)),
                scale = 1.0,
                label = "water-boil",
            )
        assertSignature("3c82696e66a849da", fromPlaced(plan, logistics))
    }

    @Test
    fun bandLayoutIsStable() {
        val gearRow = planned(craftRecipe, gear, Factory.ASSEMBLING_MACHINE_3)
        val plateRow = planned(smeltRecipe, plate, Factory.ASSEMBLING_MACHINE_3)
        val plan =
            BlueprintPlan(
                target = gear,
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
        assertSignature("d7743cdb011ea3fd", fromPlaced(plan, logistics))
    }

    private fun assertSignature(expected: String, blueprint: Blueprint) {
        val actual = signature(blueprint)
        assertEquals(
            expected,
            actual,
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
