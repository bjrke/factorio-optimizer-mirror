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
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateRecipe
import de.bjrke.factorio.optimizer.common.fixtures.petroleum
import de.bjrke.factorio.optimizer.common.fixtures.solidFuel
import de.bjrke.factorio.optimizer.common.fixtures.solidFuelFromPetroleumGasRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Characterization tests for the layout pipeline: they pin the exact entity order and every entity
 * field, so a refactor that changes geometry, wiring or placement order fails loudly.
 */
class BlueprintLayoutSnapshotTest {

    @Test
    fun baseLayoutIsStable() {
        val layout = Layout(basePlan)
        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { layout.assertSignature("920f535525b08a3d") },
        )
    }

    @Test
    fun chestBeltEndIsStable() {
        val layout = Layout(basePlan, beltEnd = BeltEnd.BUFFER_CHEST)
        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { layout.assertSignature("f015baab7b819827") },
        )
    }

    @Test
    fun fluidLayoutIsStable() {
        val plan =
            BlueprintPlan(
                target = solidFuel,
                rows =
                    listOf(
                        PlannedRow.copies(
                            planned(solidFuelFromPetroleumGasRecipe, Factory.CHEMICAL_PLANT),
                            1,
                        ),
                    ),
                sources = listOf(PlannedSource(petroleum, 1.0, fluid = true)),
                label = "solid-fuel-from-petroleum-gas",
            )
        val layout = Layout(plan)
        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { layout.assertSignature("1cb4766452495380") },
        )
    }

    @Test
    fun wideLayoutIsStable() {
        val layout = Layout(sixInputPlan())
        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { layout.assertSignature("0d1adc901089ba3b") },
        )
    }

    @Test
    fun bandLayoutIsStable() {
        val gearRow = planned(ironGearWheelRecipe, Factory.ASSEMBLING_MACHINE_3)
        val plateRow = planned(ironPlateRecipe, Factory.ASSEMBLING_MACHINE_3)
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
                label = "iron-gear-wheel",
            )
        val layout = Layout(plan)
        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { layout.assertSignature("3785e7b4eb4bd068") },
        )
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

/** A six-input row: three mixed belts plus the product. */
private fun sixInputPlan(): BlueprintPlan {
    val inputs = (1..6).map { Material("input-$it") }
    val output = Material("output")
    val recipe =
        Recipe(
            name = "six-inputs",
            ingredients = inputs.associateWith { 1.0 },
            result = mapOf(output to 1.0),
            time = 10.0,
            categories = setOf(Category.CRAFTING),
        )
    return BlueprintPlan(
        target = output,
        rows = listOf(PlannedRow.copies(planned(recipe, Factory.ASSEMBLING_MACHINE_3), 1)),
        sources = inputs.map { PlannedSource(it, 1.0, fluid = false) },
        label = "six-inputs",
    )
}
