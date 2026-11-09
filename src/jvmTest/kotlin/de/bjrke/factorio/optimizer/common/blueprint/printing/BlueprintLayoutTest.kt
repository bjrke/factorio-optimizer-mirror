package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.SplitterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.TransportEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.basePlan
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.planned
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Direction.NORTH
import de.bjrke.factorio.optimizer.common.core.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.north
import de.bjrke.factorio.optimizer.common.core.model.south
import de.bjrke.factorio.optimizer.common.fixtures.concrete
import de.bjrke.factorio.optimizer.common.fixtures.concreteRecipe
import de.bjrke.factorio.optimizer.common.fixtures.copperCable
import de.bjrke.factorio.optimizer.common.fixtures.copperCableRecipe
import de.bjrke.factorio.optimizer.common.fixtures.copperPlate
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuit
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.engineUnit
import de.bjrke.factorio.optimizer.common.fixtures.engineUnitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.pipe
import de.bjrke.factorio.optimizer.common.fixtures.smallElectricPoleRecipe
import de.bjrke.factorio.optimizer.common.fixtures.steelPlate
import de.bjrke.factorio.optimizer.common.fixtures.stoneBrick
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.factorio.optimizer.common.fixtures.wood
import de.bjrke.factorio.optimizer.common.game.prototype.BeltTier
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.InserterTier
import de.bjrke.factorio.optimizer.common.model.FactoryProduction
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Row geometry, belt feeding and compaction of a solid plan. */
class BlueprintLayoutTest {

    @Test
    fun noTwoEntitiesOverlap() {
        val blueprint = Layout(basePlan).blueprint
        val seen = mutableSetOf<Coordinate>()
        val collisions = blueprint.entities.flatMap { it.area }.filter { !seen.add(it) }
        assertEquals(emptyList(), collisions, "overlapping tiles")
    }

    @Test
    fun theTargetIsBuiltWithItsProducerAndSourceLane() {
        val names = Layout(basePlan).blueprint.entities.map { it.name }
        assertAll(
            { assertEquals(1, names.count { it == Factory.ASSEMBLING_MACHINE_3.identifier }) },
            { assertEquals(1, names.count { it == Factory.ASSEMBLING_MACHINE_1.identifier }) },
            { assertContains(names, InserterTier.FAST_INSERTER.identifier) },
            { assertContains(names, BeltTier.EXPRESS_TRANSPORT_BELT.identifier) },
            { assertContains(names, "constant-combinator") },
            { assertContains(names, "medium-electric-pole") },
        )
    }

    @Test
    fun insertersFaceTheMaterialTheyMove() {
        val layout = Layout(basePlan)
        val byTile = layout.entities.associateBy { it.area.topLeft }
        val occupied = layout.entities.flatMap { it.area }.toSet()
        val inserters = layout.entities.filterIsInstance<InserterEntity>()

        assertAll(
            // every inserter takes from the tile it faces, which is always north here: the lane
            // above for an input, the machine above for an output
            { assertTrue(inserters.isNotEmpty()) },
            {
                assertEquals(
                    emptyList(),
                    inserters.filterNot { it.direction == NORTH },
                    inserters.toString(),
                )
            },
            {
                assertEquals(
                    emptyList(),
                    inserters.filter {
                        it.area.topLeft.north(if (it.long) 2 else 1) !in occupied
                    },
                    "inserter takes from nothing",
                )
            },
            {
                // the tile it draws from (or the belt it drops onto) must carry its own material,
                // so an inserter can never be left moving a foreign one
                val wrong = inserters.filter { inserter ->
                    val pos = inserter.area.topLeft
                    val reach = if (inserter.long) 2 else 1
                    val nearby =
                        listOf(pos.north(reach), pos.south(reach), pos.north(1), pos.south(1))
                            .mapNotNull { (byTile[it] as? TransportEntity)?.material }
                    inserter.material !in nearby
                }
                assertEquals(emptyList(), wrong, "inserter carries the wrong material")
            },
        )
    }

    @Test
    fun aRecipeIsBuiltAsOftenAsItsCountNeeds() {
        val scaled =
            BlueprintPlan(
                target = gear,
                rows =
                    listOf(
                        PlannedRow.copies(
                            FactoryProduction(
                                gear,
                                ironGearWheelRecipe,
                                Factory.ASSEMBLING_MACHINE_3,
                                1.0,
                                0,
                            ),
                            3,
                        ),
                    ),
                sources = listOf(PlannedSource(ironPlate, 1.0, fluid = false)),
                scale = 1.0,
                label = "iron-gear-wheel",
            )

        val built =
            Layout(scaled).blueprint.entities.count {
                it.name == Factory.ASSEMBLING_MACHINE_3.identifier
            }
        assertEquals(3, built)
    }

    @Test
    fun aRowOfMachinesTouches() {
        val row =
            BlueprintPlan(
                target = gear,
                rows =
                    listOf(
                        PlannedRow.copies(
                            FactoryProduction(
                                gear,
                                ironGearWheelRecipe,
                                Factory.ASSEMBLING_MACHINE_3,
                                1.0,
                                0,
                            ),
                            3,
                        ),
                    ),
                sources = listOf(PlannedSource(ironPlate, 1.0, fluid = false)),
                scale = 1.0,
                label = "iron-gear-wheel",
            )
        val machines =
            Layout(row)
                .blueprint
                .entities
                .filter { it.name == Factory.ASSEMBLING_MACHINE_3.identifier }
                .sortedBy { it.area.topLeft.x }

        assertAll(
            { assertEquals(3, machines.size) },
            {
                // the factories touch, so their centres are one footprint apart (assembler width 3)
                assertEquals(
                    emptyList(),
                    machines.zipWithNext().filterNot { (a, b) ->
                        b.area.topLeft.x - a.area.topLeft.x == 3
                    },
                    machines.toString(),
                )
            },
        )
    }

    @Test
    fun aRecipeWithTwoInputsUsesALongInserter() {
        val twoPlan =
            BlueprintPlan(
                target = electronicCircuit,
                rows =
                    listOf(
                        PlannedRow.copies(
                            planned(
                                electronicCircuitRecipe,
                                Factory.ASSEMBLING_MACHINE_3,
                            ),
                            1,
                        ),
                    ),
                sources =
                    listOf(
                        PlannedSource(ironPlate, 1.0, fluid = false),
                        PlannedSource(copperCable, 1.0, fluid = false),
                    ),
                scale = 1.0,
                label = "electronic-circuit",
            )

        // the second input sits on the far lane, which only a long inserter reaches
        val names = Layout(twoPlan).blueprint.entities.map { it.name }
        assertContains(names, "long-handed-inserter")
    }

    @Test
    fun aFluidPortDoesNotCostTheLongInserter() {
        // an assembler with two solid inputs and a fluid port on its top edge: the port takes the
        // middle column, so the pattern's long inserter has to move to the machine's last column.
        val twoPlan =
            BlueprintPlan(
                target = concrete,
                rows =
                    listOf(
                        PlannedRow.copies(planned(concreteRecipe, Factory.ASSEMBLING_MACHINE_3), 1),
                    ),
                sources =
                    listOf(
                        PlannedSource(stoneBrick, 1.0, fluid = false),
                        PlannedSource(ironOre, 1.0, fluid = false),
                        PlannedSource(water, 1.0, fluid = true),
                    ),
                scale = 1.0,
                label = "concrete",
            )
        val layout = Layout(twoPlan)
        val machine = layout.entities.filterIsInstance<MachineEntity>().single()
        val inputRow = machine.area.topLeft.y - 1
        val inputs =
            layout.entities.filterIsInstance<InserterEntity>().filter {
                it.area.topLeft.y == inputRow
            }

        assertAll(
            { assertEquals(2, inputs.size, inputs.toString()) },
            { assertNotNull(inputs.firstOrNull { it.long }, inputs.toString()) },
            {
                assertEquals(
                    setOf(0, 2),
                    inputs.map { it.area.topLeft.x - machine.area.topLeft.x }.toSet(),
                )
            },
        )
    }

    @Test
    fun aSharedInputIsSplitBetweenItsConsumers() {
        val shared =
            BlueprintPlan(
                target = electronicCircuit,
                rows =
                    listOf(
                        PlannedRow.copies(
                            planned(electronicCircuitRecipe, Factory.ASSEMBLING_MACHINE_3),
                            1,
                        ),
                        PlannedRow.copies(
                            planned(smallElectricPoleRecipe, Factory.ASSEMBLING_MACHINE_3),
                            1,
                        ),
                        PlannedRow.copies(
                            planned(copperCableRecipe, Factory.ASSEMBLING_MACHINE_3),
                            1,
                        ),
                    ),
                sources =
                    listOf(
                        PlannedSource(copperPlate, 1.0, fluid = false),
                        PlannedSource(ironPlate, 1.0, fluid = false),
                        PlannedSource(wood, 1.0, fluid = false),
                    ),
                scale = 1.0,
                label = "electronic-circuit",
            )

        // one producer feeding two recipes needs a splitter on its belt
        val names = Layout(shared).blueprint.entities.map { it.name }
        assertNotNull(names.firstOrNull { it.endsWith("-splitter") }, names.toString())
    }

    @Test
    fun aSharedInputSplitsOnItsBeltNotAtTheProducer() {
        val row1 =
            PlannedRow.copies(planned(electronicCircuitRecipe, Factory.ASSEMBLING_MACHINE_3), 1)
        val row2 =
            PlannedRow.copies(planned(smallElectricPoleRecipe, Factory.ASSEMBLING_MACHINE_3), 1)
        val row3 = PlannedRow.copies(planned(copperCableRecipe, Factory.ASSEMBLING_MACHINE_3), 1)
        val shared =
            BlueprintPlan(
                target = electronicCircuit,
                rows = listOf(row1, row2, row3),
                sources =
                    listOf(
                        PlannedSource(copperPlate, 1.0, fluid = false),
                        PlannedSource(ironPlate, 1.0, fluid = false),
                        PlannedSource(wood, 1.0, fluid = false),
                    ),
                scale = 1.0,
                label = "electronic-circuit",
            )

        val layout = Layout(shared)
        val producer = layout.machineEntities.single { it.recipe == "copper-cable" }
        // compaction may move the outlet into the lane, so take the eastmost belt of the output
        // lane
        val outletRow = producer.area.bottomRight.y + 2
        val outlet =
            layout.entities
                .filterIsInstance<BeltEntity>()
                .filter { it.material == copperCable && it.area.topLeft.y == outletRow }
                .map { it.area.topLeft }
                .maxByOrNull { it.x }

        assertAll(
            {
                assertNotNull(
                    layout.entities.firstOrNull { it is SplitterEntity },
                    "no split at all",
                )
            },
            // the producer outlet is a plain belt; the split happens further down the trunk
            { assertNotNull(outlet, "no trunk belt at the producer outlet") },
            {
                assertEquals(
                    emptyList(),
                    layout.entities.filter { it is SplitterEntity && it.area.topLeft == outlet },
                    "a splitter sits directly at the producer",
                )
            },
            { assertEquals(emptyList(), layout.failures) },
        )
    }

    @Test
    fun feedsEndOnTheSurfaceFacingTheirLane() {
        val layout = Layout(basePlan)
        val craftMachine =
            layout.entities.filterIsInstance<MachineEntity>().single {
                it.recipe == "iron-gear-wheel"
            }
        val smeltMachine =
            layout.entities.filterIsInstance<MachineEntity>().single { it.recipe == "iron-plate" }

        // compaction may shorten a lane, so pick a lane belt instead of a fixed end coordinate
        fun laneBelt(machine: MachineEntity, material: Material): BeltEntity? {
            val row = machine.area.topLeft.y - 2
            return layout.entities
                .filterIsInstance<BeltEntity>()
                .filter { it.material == material && it.area.topLeft.y == row }
                .filter {
                    it.area.topLeft.x in
                        (machine.area.xRange.first - 1)..(machine.area.bottomRight.x + 1)
                }
                .maxByOrNull { it.area.topLeft.x }
        }

        // the plate lane is produced, so it runs west into the machines; the ore lane runs east
        val plateLane = laneBelt(craftMachine, ironPlate)
        val oreLane = laneBelt(smeltMachine, ironOre)

        assertAll(
            {
                assertEquals(
                    WEST,
                    plateLane?.direction,
                    "feed does not face the produced lane: $plateLane",
                )
            },
            {
                assertEquals(
                    EAST,
                    oreLane?.direction,
                    "feed does not face the imported lane: $oreLane",
                )
            },
            { assertEquals(emptyList(), layout.failures) },
        )
    }

    @Test
    fun importedLaneStartsAtTheMachineEdge() {
        val layout = Layout(basePlan)
        val smeltMachine =
            layout.entities.filterIsInstance<MachineEntity>().single { it.recipe == "iron-plate" }
        val laneRow = smeltMachine.area.topLeft.y - 2
        val lane =
            layout.entities.filterIsInstance<BeltEntity>().filter {
                it.material == ironOre && it.area.topLeft.y == laneRow
            }

        // compaction must not pull the factory west of the import lane, which would leave the
        // first lane belt over the marker instead of over the machine edge
        assertContains(
            lane.map { it.area.topLeft.x },
            smeltMachine.area.topLeft.x,
            message = "the imported lane does not reach the machine edge: $lane",
        )
    }

    @Test
    fun aRecipeWithThreeInputsUsesASecondBeltBelow() {
        val threePlan =
            BlueprintPlan(
                target = engineUnit,
                rows =
                    listOf(
                        PlannedRow.copies(
                            planned(engineUnitRecipe, Factory.ASSEMBLING_MACHINE_3),
                            1,
                        ),
                    ),
                sources =
                    listOf(
                        PlannedSource(steelPlate, 1.0, fluid = false),
                        PlannedSource(gear, 1.0, fluid = false),
                        PlannedSource(pipe, 1.0, fluid = false),
                    ),
                scale = 1.0,
                label = "engine-unit",
            )

        // the third input sits on a second belt below the row, reached by a long inserter
        val layout = Layout(threePlan)
        val machine = layout.entities.filterIsInstance<MachineEntity>().single()
        val below =
            layout.blueprint.entities.filter {
                it.name.endsWith("inserter") && it.area.topLeft.y > machine.area.bottomRight.y
            }
        assertNotNull(below.firstOrNull { it.name == "long-handed-inserter" }, below.toString())
    }

    @Test
    fun theTargetOutputIsBeltedAndMarked() {
        val markers =
            Layout(basePlan)
                .blueprint
                .entities
                .filter { it.name == "constant-combinator" }
                .map { it.marker }

        // the product leaves the target on a belt labelled with the target signal
        assertContains(markers, gear)
    }

    @Test
    fun feedRoutesStayEastOfTheWestBound() {
        val entities = Layout(basePlan).entities
        // compaction may shift the whole layout, so the import extension is the reference, not x=2
        val westBound = entities.filterIsInstance<MarkerEntity>().minOf { it.area.topLeft.x } - 1
        val belts = entities.filter {
            it.name.contains("transport-belt") || it.name.contains("splitter")
        }
        val west = belts.map { it.area.topLeft }.filter { it.x < westBound }
        assertEquals(emptyList(), west, "belts west of the import column")
    }

    @Test
    fun compactionRemovesMostOfTheEmptySpace() {
        val layout = Layout(basePlan)
        val minX = layout.entities.minOf { it.area.topLeft.x }
        val maxX = layout.entities.maxOf { it.area.bottomRight.x }
        val minY = layout.entities.minOf { it.area.topLeft.y }
        val maxY = layout.entities.maxOf { it.area.bottomRight.y }
        val width = maxX - minX + 1
        val height = maxY - minY + 1

        assertAll(
            { assertTrue(width < 10, "layout is still $width tiles wide") },
            { assertTrue(height < 20, "layout is still $height tiles tall") },
            { assertEquals(emptyList(), layout.failures) },
            { assertEquals(emptyList(), validateLayout(layout.entities)) },
        )
    }
}
