package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.factorio.optimizer.common.model.Direction.*
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import de.bjrke.test.assertTable
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val ore = Material("iron-ore")
private val coal = Material("coal")
private val plate = Material("iron-plate")
private val gear = Material("iron-gear-wheel")
private val water = Material("water")
private val lubricant = Material("lubricant")

private fun recipe(
    name: String,
    category: Category,
    result: Map<Material, Double>,
    ingredients: Map<Material, Double> = emptyMap(),
) = Recipe(name, ingredients, result, 1.0, setOf(category))

private fun planned(
    recipe: Recipe,
    material: Material,
    factory: Factory,
    directInsert: Set<Material> = emptySet(),
) =
    PlannedRecipe(
        production = FactoryProduction(material, recipe, factory, 1.0, 0),
        machines = 1.0,
        directInsert = directInsert,
    )

/**
 * The connected pipe networks of [entities]. Pipes connect by touching, and a pipe-to-ground also
 * connects to the next pipe-to-ground straight ahead within its reach, so the two ends of an
 * underground run belong to the same network.
 */
private fun plumbingComponents(entities: List<BlueprintEntity>): List<Set<Coordinate>> {
    val tiles =
        entities
            .filter { it.name == "pipe" || it.name == "pipe-to-ground" }
            .associateBy { it.area.topLeft }
    val grounds =
        tiles.filterValues { it.name == "pipe-to-ground" }.mapValues { it.value.direction }

    fun neighbours(tile: Coordinate): List<Coordinate> = buildList {
        for (direction in Direction.entries) {
            val next = tile + direction.step
            if (next in tiles) add(next)
        }
        val direction = grounds[tile]
        if (direction != null) {
            for (distance in 1..9) {
                val next = tile + direction.step * distance
                if (grounds[next] == direction) {
                    add(next)
                    break
                }
            }
        }
    }

    val seen = mutableSetOf<Coordinate>()
    val components = mutableListOf<Set<Coordinate>>()
    for (start in tiles.keys) {
        if (!seen.add(start)) continue
        val component = mutableSetOf<Coordinate>()
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            component += current
            for (next in neighbours(current)) if (seen.add(next)) queue += next
        }
        components += component
    }
    return components
}

class BlueprintLayoutTest {

    private val logistics =
        LogisticsCalculator(
            testLogistics.copy(fluids = setOf(water, lubricant)),
            BeltTier.EXPRESS_TRANSPORT_BELT,
            InserterTier.FAST_INSERTER,
            lanes = 2,
            beltStack = 1,
            inserterStack = 1,
        )

    private val smelt =
        recipe("iron-plate", Category.CRAFTING, mapOf(plate to 1.0), mapOf(ore to 1.0))
    private val craft =
        recipe("iron-gear-wheel", Category.CRAFTING, mapOf(gear to 1.0), mapOf(plate to 2.0))

    private val plan =
        BlueprintPlan(
            target = gear,
            recipes =
                listOf(
                    planned(craft, gear, Factory.ASSEMBLING_MACHINE_3, directInsert = setOf(plate)),
                    planned(smelt, plate, Factory.ASSEMBLING_MACHINE_1),
                ),
            sources = listOf(PlannedSource(ore, 1.0, fluid = false)),
            scale = 1.0,
            label = "iron-gear-wheel",
        )

    @Test
    fun noTwoEntitiesOverlap() {
        val blueprint = buildBlueprint(plan, logistics)
        val seen = mutableSetOf<Coordinate>()
        val collisions = blueprint.entities.flatMap { it.area }.filter { !seen.add(it) }
        assertEquals(emptyList(), collisions, "overlapping tiles")
    }

    @Test
    fun theTargetIsBuiltWithItsProducerAndSourceLane() {
        val names = buildBlueprint(plan, logistics).entities.map { it.name }
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
    fun solidImportMarkersHideTheirBeltUnderTheLabel() {
        val dump = blueprintLayoutDump(plan, logistics)
        val marker =
            dump.entities.filterIsInstance<MarkerEntity>().single { it.signal.material == ore }
        val input =
            dump.entities.filterIsInstance<UndergroundBeltEntity>().single {
                it.area.topLeft == marker.area.topLeft.west()
            }
        val output =
            dump.entities.filterIsInstance<UndergroundBeltEntity>().single {
                it.area.topLeft == marker.area.topLeft.east()
            }

        assertAll(
            { assertEquals(Io.INPUT, input.io) },
            { assertEquals(Io.OUTPUT, output.io) },
            { assertEquals(EAST, input.direction) },
            { assertEquals(EAST, output.direction) },
            { assertEquals(input.material, output.material) },
        )
    }

    @Test
    fun targetOutputHidesItsBeltUnderTheLabel() {
        val dump = blueprintLayoutDump(plan, logistics)
        val marker =
            dump.entities.filterIsInstance<MarkerEntity>().single { it.signal.material == gear }
        val input =
            dump.entities.filterIsInstance<UndergroundBeltEntity>().single {
                it.area.topLeft == marker.area.topLeft.west()
            }
        val output =
            dump.entities.filterIsInstance<UndergroundBeltEntity>().single {
                it.area.topLeft == marker.area.topLeft.east()
            }

        assertAll(
            { assertEquals(Io.INPUT, input.io) },
            { assertEquals(Io.OUTPUT, output.io) },
            { assertEquals(EAST, input.direction) },
            { assertEquals(EAST, output.direction) },
            { assertEquals(gear, input.material) },
            { assertEquals(input.material, output.material) },
        )
    }

    @Test
    fun fluidImportMarkersHideTheirPipeUnderTheLabel() {
        val boil = recipe("water-boil", Category.CHEMISTRY, mapOf(gear to 1.0), mapOf(water to 1.0))
        val fluidPlan =
            BlueprintPlan(
                target = gear,
                recipes = listOf(planned(boil, gear, Factory.CHEMICAL_PLANT)),
                sources = listOf(PlannedSource(water, 1.0, fluid = true)),
                scale = 1.0,
                label = "water-boil",
            )
        val dump = blueprintLayoutDump(fluidPlan, logistics)
        val marker = dump.entities.filterIsInstance<MarkerEntity>().single { it.signal.fluid }
        val input =
            dump.entities.filterIsInstance<PipeToGroundEntity>().single {
                it.area.topLeft == marker.area.topLeft.west()
            }
        val output =
            dump.entities.filterIsInstance<PipeToGroundEntity>().single {
                it.area.topLeft == marker.area.topLeft.east()
            }

        assertAll(
            { assertEquals(Io.INPUT, input.io) },
            { assertEquals(Io.OUTPUT, output.io) },
            { assertEquals(EAST, input.direction) },
            { assertEquals(EAST, output.direction) },
            { assertEquals(input.material, output.material) },
        )
    }

    @Test
    fun polesStayEastOfTheImportExtension() {
        val dump = blueprintLayoutDump(plan, logistics)
        val marker =
            dump.entities.filterIsInstance<MarkerEntity>().single { it.signal.material == ore }
        val poles = dump.entities.filterIsInstance<PoleEntity>()

        assertAll(
            { assertTrue(poles.isNotEmpty(), "no poles were placed") },
            {
                assertTrue(
                    poles.all { it.area.topLeft.x >= marker.area.topLeft.x + 2 },
                    "a pole sits in the import extension: $poles",
                )
            },
        )
    }

    @Test
    fun aFluidSourceBecomesAPipeLane() {
        val boil = recipe("water-boil", Category.CHEMISTRY, mapOf(gear to 1.0), mapOf(water to 1.0))
        val fluidPlan =
            BlueprintPlan(
                target = gear,
                recipes = listOf(planned(boil, gear, Factory.CHEMICAL_PLANT)),
                sources = listOf(PlannedSource(water, 1.0, fluid = true)),
                scale = 1.0,
                label = "water-boil",
            )

        val names = buildBlueprint(fluidPlan, logistics).entities.map { it.name }
        assertContains(names, "pipe-to-ground")
    }

    @Test
    fun aFluidTargetGetsAPipeOutputInsteadOfABelt() {
        val crack =
            recipe("lubricant", Category.CHEMISTRY, mapOf(lubricant to 1.0), mapOf(water to 1.0))
        val fluidPlan =
            BlueprintPlan(
                target = lubricant,
                recipes = listOf(planned(crack, lubricant, Factory.CHEMICAL_PLANT)),
                sources = listOf(PlannedSource(water, 1.0, fluid = true)),
                scale = 1.0,
                label = "lubricant",
            )

        val names = buildBlueprint(fluidPlan, logistics).entities.map { it.name }
        assertAll(
            { assertContains(names, "pipe") },
            { assertContainsNot(names, BeltTier.EXPRESS_TRANSPORT_BELT.identifier) },
        )
    }

    @Test
    fun differentFluidLanesDoNotTouch() {
        val coolant =
            recipe(
                "coolant",
                Category.CHEMISTRY,
                mapOf(gear to 1.0),
                mapOf(water to 1.0, lubricant to 1.0),
            )
        val fluidPlan =
            BlueprintPlan(
                target = gear,
                recipes = listOf(planned(coolant, gear, Factory.CHEMICAL_PLANT)),
                sources =
                    listOf(
                        PlannedSource(water, 1.0, fluid = true),
                        PlannedSource(lubricant, 1.0, fluid = true),
                    ),
                scale = 1.0,
                label = "coolant",
            )

        val entities = buildBlueprint(fluidPlan, logistics).entities
        val fluids = blueprintPipeFluids(fluidPlan, logistics)
        val components = plumbingComponents(entities)

        // each connected pipe network must carry exactly one fluid, so two fluids never merge
        assertAll(
            { assertEquals(setOf(water, lubricant), fluids.values.toSet()) },
            {
                assertTrue(
                    components.all { component ->
                        component.mapNotNull { fluids[it] }.distinct().size <= 1
                    },
                    "two different fluids share one pipe network",
                )
            },
        )
    }

    @Test
    fun insertersFaceTheMaterialTheyMove() {
        val dump = blueprintLayoutDump(plan, logistics)
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
                recipes =
                    listOf(
                        PlannedRecipe(
                            production =
                                FactoryProduction(
                                    gear,
                                    craft,
                                    Factory.ASSEMBLING_MACHINE_3,
                                    1.0,
                                    0,
                                ),
                            machines = 2.5,
                            directInsert = emptySet(),
                        ),
                    ),
                sources = listOf(PlannedSource(plate, 1.0, fluid = false)),
                scale = 1.0,
                label = "iron-gear-wheel",
            )

        val built =
            buildBlueprint(scaled, logistics).entities.count {
                it.name == Factory.ASSEMBLING_MACHINE_3.identifier
            }
        assertEquals(3, built)
    }

    @Test
    fun aRowOfMachinesTouches() {
        val row =
            BlueprintPlan(
                target = gear,
                recipes =
                    listOf(
                        PlannedRecipe(
                            production =
                                FactoryProduction(
                                    gear,
                                    craft,
                                    Factory.ASSEMBLING_MACHINE_3,
                                    1.0,
                                    0,
                                ),
                            machines = 3.0,
                            directInsert = emptySet(),
                        ),
                    ),
                sources = listOf(PlannedSource(plate, 1.0, fluid = false)),
                scale = 1.0,
                label = "iron-gear-wheel",
            )
        val machines =
            buildBlueprint(row, logistics)
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
                recipes = listOf(planned(two, gear, Factory.ASSEMBLING_MACHINE_3)),
                sources =
                    listOf(
                        PlannedSource(plate, 1.0, fluid = false),
                        PlannedSource(ore, 1.0, fluid = false),
                    ),
                scale = 1.0,
                label = "two",
            )

        // the second input sits on the far lane, which only a long inserter reaches
        val names = buildBlueprint(twoPlan, logistics).entities.map { it.name }
        assertContains(names, "long-handed-inserter")
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
                recipes =
                    listOf(
                        planned(makeAlpha, alpha, Factory.ASSEMBLING_MACHINE_3),
                        planned(makeBeta, beta, Factory.ASSEMBLING_MACHINE_3),
                        planned(makeCable, cable, Factory.ASSEMBLING_MACHINE_3),
                    ),
                sources = listOf(PlannedSource(ore, 1.0, fluid = false)),
                scale = 1.0,
                label = "alpha",
            )

        // one producer feeding two recipes needs a splitter on its belt
        val names = buildBlueprint(shared, logistics).entities.map { it.name }
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
                recipes =
                    listOf(
                        planned(makeAlpha, alpha, Factory.ASSEMBLING_MACHINE_3),
                        planned(makeBeta, beta, Factory.ASSEMBLING_MACHINE_3),
                        planned(makeCable, cable, Factory.ASSEMBLING_MACHINE_3),
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
        val dump = blueprintLayoutDump(plan, logistics)
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
        val dump = blueprintLayoutDump(plan, logistics)
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
        val coal = Material("coal")
        val three =
            recipe(
                "three",
                Category.CRAFTING,
                mapOf(gear to 1.0),
                mapOf(ore to 1.0, plate to 1.0, coal to 1.0),
            )
        val threePlan =
            BlueprintPlan(
                target = gear,
                recipes = listOf(planned(three, gear, Factory.ASSEMBLING_MACHINE_3)),
                sources =
                    listOf(
                        PlannedSource(ore, 1.0, fluid = false),
                        PlannedSource(plate, 1.0, fluid = false),
                        PlannedSource(coal, 1.0, fluid = false),
                    ),
                scale = 1.0,
                label = "three",
            )

        // the third input sits on a second belt below the row, reached by a south facing inserter
        val inserters =
            buildBlueprint(threePlan, logistics).entities.filter { it.name.endsWith("inserter") }
        assertTrue(
            inserters.any { it.name == "long-handed-inserter" && it.direction == SOUTH },
            inserters.toString(),
        )
    }

    @Test
    fun theTargetOutputIsBeltedAndMarked() {
        val markers =
            buildBlueprint(plan, logistics)
                .entities
                .filter { it.name == "constant-combinator" }
                .map { it.marker }

        // the product leaves the target on a belt labelled with the target signal
        assertContains(markers, gear)
    }

    @Test
    fun aFluidLaneConnectsToItsMachine() {
        val boil = recipe("water-boil", Category.CHEMISTRY, mapOf(gear to 1.0), mapOf(water to 1.0))
        val fluidPlan =
            BlueprintPlan(
                target = gear,
                recipes = listOf(planned(boil, gear, Factory.CHEMICAL_PLANT)),
                sources = listOf(PlannedSource(water, 1.0, fluid = true)),
                scale = 1.0,
                label = "water-boil",
            )
        val entities = buildBlueprint(fluidPlan, logistics).entities
        val machineTiles =
            entities
                .filter { it.name == Factory.CHEMICAL_PLANT.identifier }
                .flatMapTo(mutableSetOf()) { it.area }
        val components = plumbingComponents(entities)

        fun touchesMachine(tile: Coordinate) =
            listOf(tile.west(), tile.east(), tile.north(), tile.south()).any { it in machineTiles }

        assertAll(
            { assertEquals(1, components.size, "the fluid lane is split") },
            {
                assertTrue(components.single().any(::touchesMachine), "no pipe reaches the machine")
            },
        )
    }

    @Test
    fun feedRoutesStayEastOfTheWestBound() {
        val entities = blueprintLayoutDump(plan, logistics).entities
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
        val dump = blueprintLayoutDump(plan, logistics)
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

    @Test
    fun importMarkersShareOneColumnAfterThePrefixIsTrimmed() {
        val twoImports =
            recipe(
                "gear",
                Category.CRAFTING,
                mapOf(gear to 1.0),
                mapOf(ore to 1.0, coal to 1.0),
            )
        val mixedPlan =
            BlueprintPlan(
                target = gear,
                recipes = listOf(planned(twoImports, gear, Factory.ASSEMBLING_MACHINE_3)),
                sources =
                    listOf(
                        PlannedSource(ore, 1.0, fluid = false),
                        PlannedSource(coal, 1.0, fluid = false),
                    ),
                scale = 1.0,
                label = "gear",
            )
        val dump = blueprintLayoutDump(mixedPlan, logistics)
        val markers =
            dump.entities.filterIsInstance<MarkerEntity>().filter {
                it.signal.material == ore || it.signal.material == coal
            }
        val machine = dump.entities.filterIsInstance<MachineEntity>().single { it.recipe == "gear" }

        assertAll(
            { assertEquals(2, markers.size, "one marker per imported input") },
            {
                assertEquals(
                    emptyList(),
                    markers.map { it.area.topLeft.x }.distinct().drop(1),
                    "import markers share one column",
                )
            },
            { assertTrue(markers.all { it.area.topLeft.x < machine.area.topLeft.x }) },
            { assertEquals(emptyList(), dump.failures) },
            { assertEquals(emptyList(), validateLayout(dump.entities)) },
        )
    }

    @Test
    fun fluidRunsUseUndergroundPipes() {
        val coolant =
            recipe(
                "coolant",
                Category.CHEMISTRY,
                mapOf(gear to 1.0),
                mapOf(water to 1.0, lubricant to 1.0),
            )
        val fluidPlan =
            BlueprintPlan(
                target = gear,
                recipes = listOf(planned(coolant, gear, Factory.CHEMICAL_PLANT)),
                sources =
                    listOf(
                        PlannedSource(water, 1.0, fluid = true),
                        PlannedSource(lubricant, 1.0, fluid = true),
                    ),
                scale = 1.0,
                label = "coolant",
            )
        val entities = buildBlueprint(fluidPlan, logistics).entities
        val grounds = entities.count { it.name == "pipe-to-ground" }
        val components = plumbingComponents(entities)

        // more pipe-to-ground ends than the two source markers, and one network per fluid
        assertAll(
            { assertTrue(grounds > 2, "no underground pipes were used: $grounds") },
            { assertEquals(2, components.size, "the pipe runs are split") },
        )
    }

    @Test
    fun pipeRunsAreStretched() {
        val coolant =
            recipe(
                "coolant",
                Category.CHEMISTRY,
                mapOf(gear to 1.0),
                mapOf(water to 1.0, lubricant to 1.0),
            )
        val fluidPlan =
            BlueprintPlan(
                target = gear,
                recipes = listOf(planned(coolant, gear, Factory.CHEMICAL_PLANT)),
                sources =
                    listOf(
                        PlannedSource(water, 1.0, fluid = true),
                        PlannedSource(lubricant, 1.0, fluid = true),
                    ),
                scale = 1.0,
                label = "coolant",
            )
        val byTile =
            blueprintLayoutDump(fluidPlan, logistics).entities.associateBy { it.area.topLeft }

        fun plainPipe(tile: Coordinate, fluid: Material?) =
            (byTile[tile] as? PipeEntity)?.material == fluid

        val problems = mutableListOf<String>()
        for (input in
            byTile.values.filterIsInstance<PipeToGroundEntity>().filter { it.io == Io.INPUT }) {
            // the import connector is a fixed marker pair, not a route to stretch
            if (byTile[input.area.topLeft.east()] is MarkerEntity) continue
            val step = input.direction.step
            var output: PipeToGroundEntity? = null
            var reach = 0
            for (distance in 2..9) {
                val entity = byTile[input.area.topLeft + step * distance] as? PipeToGroundEntity
                if (entity != null && entity.io == Io.OUTPUT && entity.material == input.material) {
                    output = entity
                    reach = distance
                    break
                }
            }
            if (output == null || reach >= 9) continue
            val back = input.area.topLeft - step
            if (plainPipe(back, input.material) && back - step in byTile) {
                problems += "input ${input.area.topLeft} can still grow back"
            }
            val forward = output.area.topLeft + step
            if (plainPipe(forward, input.material) && forward + step in byTile) {
                problems += "output ${output.area.topLeft} can still grow forward"
            }
        }
        assertEquals(emptyList(), problems)
    }

    @Test
    fun infinityChestsAreOptIn() {
        val without = buildBlueprint(plan, logistics).entities
        val with = buildBlueprint(plan, logistics, beltEnd = BeltEnd.INFINITY_CHEST).entities

        assertAll(
            { assertTrue(without.none { it.name == "infinity-chest" }, "chests by default") },
            {
                assertTrue(
                    with.any { it.name == "infinity-chest" && it.infinity?.item == ore },
                    "no input chest for the imported ore",
                )
            },
            { assertTrue(with.any { it.name.endsWith("-loader") }, "no loader") },
        )
    }

    @Test
    fun aProducedLaneIsFedFacingIntoTheLane() {
        val cable = Material("copper-cable")
        val top = Material("top")
        val makeCable =
            recipe("copper-cable", Category.CRAFTING, mapOf(cable to 1.0), mapOf(ore to 1.0))
        val makeTop = recipe("top", Category.CRAFTING, mapOf(top to 1.0), mapOf(cable to 1.0))
        val produced =
            BlueprintPlan(
                target = top,
                recipes =
                    listOf(
                        planned(makeTop, top, Factory.ASSEMBLING_MACHINE_3),
                        planned(makeCable, cable, Factory.ASSEMBLING_MACHINE_3),
                    ),
                sources = listOf(PlannedSource(ore, 1.0, fluid = false)),
                scale = 1.0,
                label = "top",
            )

        val entities = blueprintLayoutDump(produced, logistics).entities
        val machineArea =
            entities.filterIsInstance<MachineEntity>().single { it.recipe == "top" }.area
        val laneRow = machineArea.topLeft.y - 2

        // a produced lane runs west into the machine; compaction may shorten it, so take the
        // eastmost remaining lane belt rather than a fixed coordinate
        val feed =
            assertNotNull(
                entities
                    .filterIsInstance<BeltEntity>()
                    .filter { it.material == cable && it.area.topLeft.y == laneRow }
                    .maxByOrNull { it.area.topLeft.x },
                "no feed at the produced lane",
            )
        assertEquals(WEST, feed.direction, "the feed does not face the lane")
    }

    @Test
    fun aChestMarkerFeedsTheBeltOutOfTheChest() {
        val dump = blueprintLayoutDump(plan, logistics, beltEnd = BeltEnd.BUFFER_CHEST)
        val chest =
            dump.entities.filterIsInstance<ChestEntity>().single {
                it.requestFilter?.name == ore.name
            }
        val row = chest.area.topLeft.y
        val left = chest.area.topLeft.x - 2
        val byTile = dump.entities.associateBy { it.area.topLeft }

        assertAll(
            { assertEquals(BeltEnd.BUFFER_CHEST.identifier, chest.name) },
            {
                assertEquals(
                    RequestFilter(ore.name, 100, trashNotRequested = true),
                    chest.requestFilter,
                )
            },
            {
                assertEquals(
                    Io.INPUT,
                    (byTile[Coordinate(left, row)] as? UndergroundBeltEntity)?.io,
                )
            },
            {
                assertEquals(
                    Io.OUTPUT,
                    (byTile[Coordinate(left + 4, row)] as? UndergroundBeltEntity)?.io,
                )
            },
            {
                assertEquals(
                    EAST,
                    (byTile[Coordinate(left + 1, row)] as? InserterEntity)?.direction,
                )
            },
            {
                val far = byTile[Coordinate(left + 3, row)] as? InserterEntity
                assertEquals(WEST, far?.direction)
                assertEquals(true, far?.mirror)
            },
            { assertEquals(emptyList(), validateLayout(dump.entities)) },
        )
    }

    @Test
    fun theTargetOutputUsesTheChestOutputVariant() {
        val dump = blueprintLayoutDump(plan, logistics, beltEnd = BeltEnd.BUFFER_CHEST)
        val chest =
            dump.entities.filterIsInstance<ChestEntity>().single {
                it.requestFilter?.name == gear.name
            }
        val row = chest.area.topLeft.y
        val left = chest.area.topLeft.x - 2
        val byTile = dump.entities.associateBy { it.area.topLeft }
        val near = byTile[Coordinate(left + 1, row)] as? InserterEntity
        val far = byTile[Coordinate(left + 3, row)] as? InserterEntity
        val condition = CircuitCondition(gear.name, "<", 200)

        assertAll(
            {
                assertEquals(
                    RequestFilter(gear.name, 200, trashNotRequested = true),
                    chest.requestFilter,
                )
            },
            { assertEquals(false, chest.circuitConditionEnabled) },
            { assertEquals(WEST, near?.direction) },
            { assertEquals(EAST, far?.direction) },
            { assertEquals(condition, near?.circuitCondition) },
            { assertEquals(condition, far?.circuitCondition) },
        )
    }

    @Test
    fun eachFilteredChestMarkerKeepsItsRequest() =
        assertTable(
            listOf(BeltEnd.BUFFER_CHEST to 100, BeltEnd.STORAGE_CHEST to 1),
        ) { (beltEnd, count) ->
            val chest =
                buildBlueprint(plan, logistics, beltEnd = beltEnd).entities.single {
                    it.name == beltEnd.identifier && it.requestFilter?.name == ore.name
                }
            assertEquals(
                RequestFilter(ore.name, count, trashNotRequested = beltEnd == BeltEnd.BUFFER_CHEST),
                chest.requestFilter,
            )
        }

    @Test
    fun plainChestMarkersAreBarred() =
        assertTableOf(BeltEnd.WOODEN_CHEST, BeltEnd.STEEL_CHEST) { beltEnd ->
            val chests =
                buildBlueprint(plan, logistics, beltEnd = beltEnd).entities.filter {
                    it.name == beltEnd.identifier
                }
            assertAll(
                { assertEquals(2, chests.size, "one input and one output chest") },
                { assertTrue(chests.all { it.bar == 4 }, chests.toString()) },
                { assertTrue(chests.all { it.requestFilter == null }, chests.toString()) },
            )
        }

    @Test
    fun fluidImportsKeepTheCombinatorLabelEvenWithAChestMarker() {
        val boil = recipe("water-boil", Category.CHEMISTRY, mapOf(gear to 1.0), mapOf(water to 1.0))
        val fluidPlan =
            BlueprintPlan(
                target = gear,
                recipes = listOf(planned(boil, gear, Factory.CHEMICAL_PLANT)),
                sources = listOf(PlannedSource(water, 1.0, fluid = true)),
                scale = 1.0,
                label = "water-boil",
            )
        val dump = blueprintLayoutDump(fluidPlan, logistics, beltEnd = BeltEnd.BUFFER_CHEST)
        val marker = dump.entities.filterIsInstance<MarkerEntity>().single { it.signal.fluid }

        assertAll(
            { assertEquals(water, marker.signal.material) },
            { assertEquals(emptyList(), dump.failures) },
        )
    }

    @Test
    fun theChestOutputWiresBothInsertersToTheChest() {
        val blueprint = buildBlueprint(plan, logistics, beltEnd = BeltEnd.BUFFER_CHEST)
        val chest = blueprint.entities.indexOfFirst { it.requestFilter?.name == gear.name } + 1

        assertEquals(
            listOf(
                BlueprintWire(chest - 2, 1, chest, 1),
                BlueprintWire(chest, 1, chest - 1, 1),
            ),
            blueprint.wires,
        )
    }

    @Test
    fun theChestInputNeedsNoCircuitWire() =
        assertTable(listOf(BeltEnd.BUFFER_CHEST, BeltEnd.STORAGE_CHEST)) { beltEnd ->
            val blueprint = buildBlueprint(plan, logistics, beltEnd = beltEnd)
            val inputChest =
                blueprint.entities.indexOfFirst { it.requestFilter?.name == ore.name } + 1
            val wired = blueprint.wires.flatMap { listOf(it.first, it.second) }

            assertContainsNot(wired, inputChest)
        }

    @Test
    fun plainAndCheatMarkersNeedNoCircuitWire() =
        assertTableOf(BeltEnd.WOODEN_CHEST, BeltEnd.STEEL_CHEST, BeltEnd.INFINITY_CHEST) { beltEnd
            ->
            assertEquals(emptyList<BlueprintWire>(), buildBlueprint(plan, logistics, beltEnd).wires)
        }
}
