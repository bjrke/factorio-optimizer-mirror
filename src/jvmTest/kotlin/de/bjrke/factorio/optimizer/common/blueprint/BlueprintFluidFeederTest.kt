package de.bjrke.factorio.optimizer.common.blueprint

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.fluidRowPlan
import de.bjrke.factorio.optimizer.common.fixtures.lubricant
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.east
import de.bjrke.factorio.optimizer.common.model.west
import de.bjrke.factorio.optimizer.common.plan.prototypeFluidPorts
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which port the fluid feed reserves on a machine. A machine has several pipes (the chemical plant
 * has two corners on its input edge), but a fluid only ever needs one of them: the feed takes the
 * port the row can reach — the input edge facing the lanes above — and leaves the other corner
 * alone, so no inserter has to make room for a pipe that would feed nothing new.
 */
class BlueprintFluidFeederTest {

    private val boilPlan = fluidRowPlan(Factory.CHEMICAL_PLANT)

    @Test
    fun aWaterChemicalPlantIsFedThroughExactlyOneTopCorner() {
        val layout = Layout(boilPlan)
        val machine = layout.machineEntities.single().area

        assertAll(
            {
                assertEquals(
                    listOf(Coordinate(machine.x, machine.y - 1)),
                    layout.portPipes.keys.toList(),
                    "the chemical plant should be fed through one pipe, on its top-left corner",
                )
            },
            {
                assertEquals(
                    mapOf(Coordinate(machine.x, machine.y - 1) to water),
                    layout.inputPipes,
                )
            },
            {
                assertEquals(
                    emptyMap(),
                    layout.outputPipes,
                    "nothing is drained from the input side",
                )
            },
        )
    }

    @Test
    fun aSecondFluidTakesTheOtherTopCornerAndStillOnePortEach() {
        val row =
            Layout(
                fluidRowPlan(Factory.CHEMICAL_PLANT, fluidInputs = mapOf(lubricant to 1.0)),
            )
        val machine = row.machineEntities.single().area
        val feed = row.portPipes

        assertAll(
            // one port per fluid, both on the input edge the row reaches: the two top corners
            { assertEquals(2, feed.size, "each fluid needs exactly one port") },
            { assertEquals(setOf(water, lubricant), feed.values.toSet()) },
            { assertEquals(feed.keys.toSet(), row.inputPipes.keys) },
            {
                assertEquals(
                    setOf(
                        Coordinate(machine.x, machine.y - 1),
                        Coordinate(machine.x + 2, machine.y - 1),
                    ),
                    feed.keys,
                    "the two fluids should use the two top corners",
                )
            },
        )
    }

    @Test
    fun everyFluidMachineIsFedThroughOnePort() =
        assertTableOf(
            Factory.ASSEMBLING_MACHINE_2,
            Factory.ASSEMBLING_MACHINE_3,
            Factory.CHEMICAL_PLANT,
            Factory.BIOCHAMBER,
            Factory.FOUNDRY,
            Factory.CRYOGENIC_PLANT,
            Factory.OIL_REFINERY,
            Factory.ELECTROMAGNETIC_PLANT,
        ) { factory ->
            val layout = Layout(fluidRowPlan(factory))
            assertAll(
                {
                    assertEquals(
                        emptyList(),
                        layout.failures,
                        "$factory left a fluid unrouted",
                    )
                },
                {
                    assertEquals(
                        1,
                        layout.portPipes.size,
                        "$factory is fed through more than one port",
                    )
                },
                // the one port is on the machine's input side, where the row feeds from above
                {
                    assertEquals(
                        layout.portPipes.keys,
                        layout.inputPipes.keys,
                        "$factory is fed through an output port",
                    )
                },
            )
        }

    /**
     * The assembling machine 1 takes fluid through a side pipe, and no port table lists it, so the
     * feed falls back to the machine's side edge. That edge faces no lane and no belt, so the fluid
     * still reaches the machine through exactly one pipe.
     */
    @Test
    fun aMachineWithoutAPortTableIsFedThroughOneSidePipe() {
        assertEquals(
            emptyList(),
            prototypeFluidPorts(Factory.ASSEMBLING_MACHINE_1),
        )
        val layout = Layout(fluidRowPlan(Factory.ASSEMBLING_MACHINE_1))
        val machine = layout.machineEntities.single().area

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            // the pipe that serves the machine is the one touching its side, not a lane pipe
            {
                assertEquals(
                    listOf(Coordinate(machine.x - 1, machine.y)),
                    layout.plumbing.keys.filter { it.west() in machine || it.east() in machine },
                )
            },
        )
    }
}
