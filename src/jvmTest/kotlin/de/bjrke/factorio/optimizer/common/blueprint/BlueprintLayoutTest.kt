package de.bjrke.factorio.optimizer.common.blueprint

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.basePlan
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.craftRecipe
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.gear
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.logistics
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.ore
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.planned
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.plate
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.recipe
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.water
import de.bjrke.factorio.optimizer.common.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.layout.entity.SplitterEntity
import de.bjrke.factorio.optimizer.common.layout.entity.TransportEntity
import de.bjrke.factorio.optimizer.common.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.model.Direction.NORTH
import de.bjrke.factorio.optimizer.common.model.Direction.SOUTH
import de.bjrke.factorio.optimizer.common.model.Direction.WEST
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.north
import de.bjrke.factorio.optimizer.common.model.south
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.prototype.BeltTier
import de.bjrke.factorio.optimizer.common.prototype.Category
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.prototype.InserterTier
import de.bjrke.factorio.optimizer.common.service.FactoryProduction
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
        val blueprint = fromPlaced(basePlan, logistics)
        val seen = mutableSetOf<Coordinate>()
        val collisions = blueprint.entities.flatMap { it.area }.filter { !seen.add(it) }
        assertEquals(emptyList(), collisions, "overlapping tiles")
    }

    @Test
    fun theTargetIsBuiltWithItsProducerAndSourceLane() {
        val names = fromPlaced(basePlan, logistics).entities.map { it.name }
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
        val dump = blueprintLayoutDump(basePlan, logistics)
        val byTile = dump.entities.associateBy { it.area.topLeft }
        val occupied = dump.entities.flatMap { it.area }.toSet()
        val inserters = dump.entities.filterIsInstance<InserterEntity>()

        assertAll(
            // every inserter takes from the tile it faces, which is always north here: the lane
            // above for an input, the machine above for an output
            { assertTrue(inserters.isNotEmpty()) },
            { assertTrue(inserters.all { it.direction == NORTH }, inserters.toString()) },
            {
                val dangling = inserters.filter {
                    it.area.topLeft.north(if (it.long) 2 else 1) !in occupied
                }
                assertEquals(emptyList(), dangling, "inserter takes from nothing")
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
                                craftRecipe,
                                Factory.ASSEMBLING_MACHINE_3,
                                1.0,
                                0,
                            ),
                            3,
                        ),
                    ),
                sources = listOf(PlannedSource(plate, 1.0, fluid = false)),
                scale = 1.0,
                label = "iron-gear-wheel",
            )

        val built =
            fromPlaced(scaled, logistics).entities.count {
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
                                craftRecipe,
                                Factory.ASSEMBLING_MACHINE_3,
                                1.0,
                                0,
                            ),
                            3,
                        ),
                    ),
                sources = listOf(PlannedSource(plate, 1.0, fluid = false)),
                scale = 1.0,
                label = "iron-gear-wheel",
            )
        val machines =
            fromPlaced(row, logistics)
                .entities
                .filter { it.name == Factory.ASSEMBLING_MACHINE_3.identifier }
                .sortedBy { it.area.topLeft.x }

        assertAll(
            { assertEquals(3, machines.size) },
            {
                // the factories touch, so their centres are one footprint apart (assembler width 3)
                assertTrue(
                    machines.zipWithNext().all { (a, b) ->
                        b.area.topLeft.x - a.area.topLeft.x == 3
                    },
                    machines.toString(),
                )
            },
        )
    }

    @Test
    fun aRecipeWithTwoInputsUsesALongInserter() {
        val two =
            recipe("two", Category.CRAFTING, mapOf(gear to 1.0), mapOf(plate to 1.0, ore to 1.0))
        val twoPlan =
            BlueprintPlan(
                target = gear,
                rows =
                    listOf(PlannedRow.copies(planned(two, gear, Factory.ASSEMBLING_MACHINE_3), 1)),
                sources =
                    listOf(
                        PlannedSource(plate, 1.0, fluid = false),
                        PlannedSource(ore, 1.0, fluid = false),
                    ),
                scale = 1.0,
                label = "two",
            )

        // the second input sits on the far lane, which only a long inserter reaches
        val names = fromPlaced(twoPlan, logistics).entities.map { it.name }
        assertContains(names, "long-handed-inserter")
    }

    @Test
    fun aFluidPortDoesNotCostTheLongInserter() {
        // an assembler with two solid inputs and a fluid port on its top edge: the port takes the
        // middle column, so the pattern's long inserter has to move to the machine's last column
        val two =
            recipe(
                "two",
                Category.CRAFTING,
                mapOf(gear to 2.0),
                mapOf(plate to 1.0, ore to 1.0, water to 1.0),
            )
        val twoPlan =
            BlueprintPlan(
                target = gear,
                rows =
                    listOf(PlannedRow.copies(planned(two, gear, Factory.ASSEMBLING_MACHINE_3), 1)),
                sources =
                    listOf(
                        PlannedSource(plate, 1.0, fluid = false),
                        PlannedSource(ore, 1.0, fluid = false),
                        PlannedSource(water, 1.0, fluid = true),
                    ),
                scale = 1.0,
                label = "two",
            )
        val dump = blueprintLayoutDump(twoPlan, logistics)
        val machine = dump.entities.filterIsInstance<MachineEntity>().single()
        val inputRow = machine.area.topLeft.y - 1
        val inputs =
            dump.entities.filterIsInstance<InserterEntity>().filter {
                it.area.topLeft.y == inputRow
            }

        assertAll(
            { assertEquals(2, inputs.size, inputs.toString()) },
            { assertTrue(inputs.any { it.long }, inputs.toString()) },
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
        val cable = Material("copper-cable")
        val alpha = Material("alpha")
        val beta = Material("beta")
        val makeCable =
            recipe("copper-cable", Category.CRAFTING, mapOf(cable to 1.0), mapOf(ore to 1.0))
        val makeAlpha = recipe("alpha", Category.CRAFTING, mapOf(alpha to 1.0), mapOf(cable to 1.0))
        val makeBeta = recipe("beta", Category.CRAFTING, mapOf(beta to 1.0), mapOf(cable to 1.0))
        val shared =
            BlueprintPlan(
                target = alpha,
                rows =
                    listOf(
                        PlannedRow.copies(
                            planned(makeAlpha, alpha, Factory.ASSEMBLING_MACHINE_3),
                            1,
                        ),
                        PlannedRow.copies(
                            planned(makeBeta, beta, Factory.ASSEMBLING_MACHINE_3),
                            1,
                        ),
                        PlannedRow.copies(
                            planned(makeCable, cable, Factory.ASSEMBLING_MACHINE_3),
                            1,
                        ),
                    ),
                sources = listOf(PlannedSource(ore, 1.0, fluid = false)),
                scale = 1.0,
                label = "alpha",
            )

        // one producer feeding two recipes needs a splitter on its belt
        val names = fromPlaced(shared, logistics).entities.map { it.name }
        assertTrue(names.any { it.endsWith("-splitter") }, names.toString())
    }

    @Test
    fun aSharedInputSplitsOnItsBeltNotAtTheProducer() {
        val cable = Material("copper-cable")
        val alpha = Material("alpha")
        val beta = Material("beta")
        val makeCable =
            recipe("copper-cable", Category.CRAFTING, mapOf(cable to 1.0), mapOf(ore to 1.0))
        val makeAlpha = recipe("alpha", Category.CRAFTING, mapOf(alpha to 1.0), mapOf(cable to 1.0))
        val makeBeta = recipe("beta", Category.CRAFTING, mapOf(beta to 1.0), mapOf(cable to 1.0))
        val shared =
            BlueprintPlan(
                target = alpha,
                rows =
                    listOf(
                        PlannedRow.copies(
                            planned(makeAlpha, alpha, Factory.ASSEMBLING_MACHINE_3),
                            1,
                        ),
                        PlannedRow.copies(
                            planned(makeBeta, beta, Factory.ASSEMBLING_MACHINE_3),
                            1,
                        ),
                        PlannedRow.copies(
                            planned(makeCable, cable, Factory.ASSEMBLING_MACHINE_3),
                            1,
                        ),
                    ),
                sources = listOf(PlannedSource(ore, 1.0, fluid = false)),
                scale = 1.0,
                label = "alpha",
            )

        val dump = blueprintLayoutDump(shared, logistics)
        val producer =
            dump.entities.filterIsInstance<MachineEntity>().single { it.recipe == "copper-cable" }
        // compaction may move the outlet into the lane, so take the eastmost belt of the output
        // lane
        val outletRow = producer.area.bottomRight.y + 2
        val outlet =
            dump.entities
                .filterIsInstance<BeltEntity>()
                .filter { it.material == cable && it.area.topLeft.y == outletRow }
                .maxByOrNull { it.area.topLeft.x }
                ?.area
                ?.topLeft

        assertAll(
            { assertTrue(dump.entities.any { it is SplitterEntity }, "no split at all") },
            {
                // the producer outlet is a plain belt; the split happens further down the trunk
                assertNotNull(outlet, "no trunk belt at the producer outlet")
                assertTrue(
                    dump.entities.none { it is SplitterEntity && it.area.topLeft == outlet },
                    "a splitter sits directly at the producer",
                )
            },
            { assertEquals(emptyList(), dump.failures) },
        )
    }

    @Test
    fun feedsEndOnTheSurfaceFacingTheirLane() {
        val dump = blueprintLayoutDump(basePlan, logistics)
        val craftMachine =
            dump.entities.filterIsInstance<MachineEntity>().single {
                it.recipe == "iron-gear-wheel"
            }
        val smeltMachine =
            dump.entities.filterIsInstance<MachineEntity>().single { it.recipe == "iron-plate" }

        // compaction may shorten a lane, so pick a lane belt instead of a fixed end coordinate
        fun laneBelt(machine: MachineEntity, material: Material): BeltEntity? {
            val row = machine.area.topLeft.y - 2
            return dump.entities
                .filterIsInstance<BeltEntity>()
                .filter { it.material == material && it.area.topLeft.y == row }
                .filter {
                    it.area.topLeft.x in
                        (machine.area.xRange.first - 1)..(machine.area.bottomRight.x + 1)
                }
                .maxByOrNull { it.area.topLeft.x }
        }

        // the plate lane is produced, so it runs west into the machines; the ore lane runs east
        val plateLane = laneBelt(craftMachine, plate)
        val oreLane = laneBelt(smeltMachine, ore)

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
            { assertEquals(emptyList(), dump.failures) },
        )
    }

    @Test
    fun importedLaneStartsAtTheMachineEdge() {
        val dump = blueprintLayoutDump(basePlan, logistics)
        val smeltMachine =
            dump.entities.filterIsInstance<MachineEntity>().single { it.recipe == "iron-plate" }
        val laneRow = smeltMachine.area.topLeft.y - 2
        val lane =
            dump.entities.filterIsInstance<BeltEntity>().filter {
                it.material == ore && it.area.topLeft.y == laneRow
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
        val localCoal = Material("coal")
        val three =
            recipe(
                "three",
                Category.CRAFTING,
                mapOf(gear to 2.0),
                mapOf(ore to 1.0, plate to 1.0, localCoal to 1.0),
            )
        val threePlan =
            BlueprintPlan(
                target = gear,
                rows =
                    listOf(
                        PlannedRow.copies(planned(three, gear, Factory.ASSEMBLING_MACHINE_3), 1),
                    ),
                sources =
                    listOf(
                        PlannedSource(ore, 1.0, fluid = false),
                        PlannedSource(plate, 1.0, fluid = false),
                        PlannedSource(localCoal, 1.0, fluid = false),
                    ),
                scale = 1.0,
                label = "three",
            )

        // the third input sits on a second belt below the row, reached by a south facing inserter
        val inserters =
            fromPlaced(threePlan, logistics).entities.filter {
                it.name.endsWith("inserter")
            }
        assertTrue(
            inserters.any { it.name == "long-handed-inserter" && it.direction == SOUTH },
            inserters.toString(),
        )
    }

    @Test
    fun theTargetOutputIsBeltedAndMarked() {
        val markers =
            fromPlaced(basePlan, logistics)
                .entities
                .filter { it.name == "constant-combinator" }
                .map { it.marker }

        // the product leaves the target on a belt labelled with the target signal
        assertContains(markers, gear)
    }

    @Test
    fun feedRoutesStayEastOfTheWestBound() {
        val entities = blueprintLayoutDump(basePlan, logistics).entities
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
        val dump = blueprintLayoutDump(basePlan, logistics)
        val minX = dump.entities.minOf { it.area.topLeft.x }
        val maxX = dump.entities.maxOf { it.area.bottomRight.x }
        val minY = dump.entities.minOf { it.area.topLeft.y }
        val maxY = dump.entities.maxOf { it.area.bottomRight.y }
        val width = maxX - minX + 1
        val height = maxY - minY + 1

        assertAll(
            { assertTrue(width < 10, "layout is still $width tiles wide") },
            { assertTrue(height < 20, "layout is still $height tiles tall") },
            { assertEquals(emptyList(), dump.failures) },
            { assertEquals(emptyList(), validateLayout(dump.entities)) },
        )
    }
}
