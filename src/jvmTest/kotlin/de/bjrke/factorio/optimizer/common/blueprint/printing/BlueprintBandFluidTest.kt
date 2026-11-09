package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeToGroundEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedLink
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.fluidNetworkProblems
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.planned
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.fixtures.battery
import de.bjrke.factorio.optimizer.common.fixtures.electricEngineUnit
import de.bjrke.factorio.optimizer.common.fixtures.electricEngineUnitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuit
import de.bjrke.factorio.optimizer.common.fixtures.engineUnit
import de.bjrke.factorio.optimizer.common.fixtures.engineUnitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.flyingRobotFrame
import de.bjrke.factorio.optimizer.common.fixtures.flyingRobotFrameRecipe
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateRecipe
import de.bjrke.factorio.optimizer.common.fixtures.lubricant
import de.bjrke.factorio.optimizer.common.fixtures.pipe
import de.bjrke.factorio.optimizer.common.fixtures.steelPlate
import de.bjrke.factorio.optimizer.common.fixtures.sulfur
import de.bjrke.factorio.optimizer.common.fixtures.sulfuricAcid
import de.bjrke.factorio.optimizer.common.fixtures.sulfuricAcidRecipe
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * Fluid ports on a direct-feed band: a producer's ingredient, a consumer's ingredient, the target.
 */
class BlueprintBandFluidTest {

    private val engineSources =
        listOf(
            PlannedSource(steelPlate, 1.0, fluid = false),
            PlannedSource(gear, 1.0, fluid = false),
            PlannedSource(pipe, 1.0, fluid = false),
        )

    @Test
    fun aBandMachineThatTakesAFluidIsFedFromItsLane() {
        // the engine unit is inserted machine to machine, so the row is a band though the consumer
        // also takes lubricant; the fluid lane has to reach the consumer's own top port
        val machines =
            listOf(
                planned(engineUnitRecipe, Factory.ASSEMBLING_MACHINE_3),
                planned(electricEngineUnitRecipe, Factory.ASSEMBLING_MACHINE_3),
            )
        val links = listOf(PlannedLink(0, engineUnit, 0.1, producerIsLeft = true))
        val sources =
            engineSources +
                listOf(
                    PlannedSource(electronicCircuit, 1.0, fluid = false),
                    PlannedSource(lubricant, 1.0, fluid = true),
                )
        val plan =
            BlueprintPlan(
                target = electricEngineUnit,
                rows = listOf(PlannedRow(machines, links)),
                sources = sources,
                label = "electric-engine-unit",
            )

        assertFed(Layout(plan), "electric-engine-unit", 2)
    }

    @Test
    fun aBandProducerThatTakesAFluidIsFedFromItsLane() {
        // the electric engine machine sits between its two consumers and takes lubricant; its top
        // port still has to reach the lane
        val machines =
            listOf(
                planned(flyingRobotFrameRecipe, Factory.ASSEMBLING_MACHINE_3),
                planned(electricEngineUnitRecipe, Factory.ASSEMBLING_MACHINE_3),
                planned(flyingRobotFrameRecipe, Factory.ASSEMBLING_MACHINE_3),
            )
        val links =
            listOf(
                PlannedLink(0, electricEngineUnit, 0.1, producerIsLeft = false),
                PlannedLink(1, electricEngineUnit, 0.1, producerIsLeft = true),
            )
        val sources =
            listOf(
                PlannedSource(engineUnit, 1.0, fluid = false),
                PlannedSource(electronicCircuit, 1.0, fluid = false),
                PlannedSource(battery, 1.0, fluid = false),
                PlannedSource(steelPlate, 1.0, fluid = false),
                PlannedSource(lubricant, 1.0, fluid = true),
            )
        val plan =
            BlueprintPlan(
                target = flyingRobotFrame,
                rows = listOf(PlannedRow(machines, links)),
                sources = sources,
                label = "flying-robot-frame",
            )

        assertFed(Layout(plan), "electric-engine-unit", 3)
    }

    @Test
    fun aBandFluidTargetGetsAPipeOutput() {
        // the iron plate is inserted machine to machine, and the band's target is sulfuric acid,
        // a fluid that leaves through the pipe network instead of a belt
        val machines =
            listOf(
                planned(ironPlateRecipe, Factory.ASSEMBLING_MACHINE_3),
                planned(sulfuricAcidRecipe, Factory.CHEMICAL_PLANT),
            )
        val links = listOf(PlannedLink(0, ironPlate, 1.0, producerIsLeft = true))
        val sources =
            listOf(
                PlannedSource(ironOre, 1.0, fluid = false),
                PlannedSource(sulfur, 1.0, fluid = false),
                PlannedSource(water, 1.0, fluid = true),
            )
        val plan =
            BlueprintPlan(
                target = sulfuricAcid,
                rows = listOf(PlannedRow(machines, links)),
                sources = sources,
                label = "sulfuric-acid",
            )
        val layout = Layout(plan)
        val names = layout.blueprint.entities.map { it.name }
        val beltMaterials =
            layout.entities.filterIsInstance<BeltEntity>().mapNotNull {
                layout.beltMaterials[it.area.topLeft]
            }

        assertAll(
            { assertContains(names, "pipe") },
            { assertContainsNot(beltMaterials, sulfuricAcid) },
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
        )
    }

    private fun assertFed(layout: Layout, recipe: String, machineCount: Int) {
        val shaftEnd = layout.portShaft(recipe)

        assertAll(
            { assertEquals(machineCount, layout.entities.filterIsInstance<MachineEntity>().size) },
            { assertEquals(Io.OUTPUT, shaftEnd?.io) },
            { assertEquals(Direction.SOUTH, shaftEnd?.direction) },
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
            { assertEquals(emptyList(), fluidNetworkProblems(layout.entities)) },
        )
    }

    /** The machine's top input port at `(1, -1)`, where a lane's port-side shaft has to sit. */
    private fun Layout.portShaft(recipe: String): PipeToGroundEntity? {
        val machine = entities.filterIsInstance<MachineEntity>().single { it.recipe == recipe }
        val port = Coordinate(machine.area.topLeft.x + 1, machine.area.topLeft.y - 1)
        return entities.filterIsInstance<PipeToGroundEntity>().singleOrNull {
            it.area.topLeft == port
        }
    }
}
