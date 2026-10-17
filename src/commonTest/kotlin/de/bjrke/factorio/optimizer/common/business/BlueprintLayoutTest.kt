package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.factorio.optimizer.common.model.Direction.*
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val ore = Material("iron-ore")
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
        val outlet = Coordinate(producer.area.bottomRight.x + 1, producer.area.bottomRight.y + 2)
        val byTile = dump.entities.associateBy { it.area.topLeft }

        assertAll(
            { assertTrue(dump.entities.any { it is SplitterEntity }, "no split at all") },
            {
                // the producer outlet is a plain belt; the split happens further down the trunk
                assertTrue(
                    dump.entities.none { it is SplitterEntity && it.area.topLeft == outlet },
                    "a splitter sits directly at the producer",
                )
            },
            {
                assertTrue(
                    byTile[outlet] is BeltEntity,
                    "no trunk belt at the producer outlet: ${byTile[outlet]}",
                )
            },
            { assertEquals(emptyList(), dump.failures) },
        )
    }

    @Test
    fun feedsEndOnTheSurfaceFacingTheirLane() {
        val dump = blueprintLayoutDump(plan, logistics)
        val byTile = dump.entities.associateBy { it.area.topLeft }
        val craftMachine =
            dump.entities.filterIsInstance<MachineEntity>().single {
                it.recipe == "iron-gear-wheel"
            }
        val smeltMachine =
            dump.entities.filterIsInstance<MachineEntity>().single { it.recipe == "iron-plate" }

        // the plate lane is produced, so it is fed from the east; the ore lane is imported
        val plateFeed =
            Coordinate(craftMachine.area.bottomRight.x + 1, craftMachine.area.topLeft.y - 2)
        val oreFeed = Coordinate(smeltMachine.area.topLeft.x - 1, smeltMachine.area.topLeft.y - 2)

        assertAll(
            {
                assertEquals(
                    WEST,
                    (byTile[plateFeed] as? BeltEntity)?.direction,
                    "feed does not face the produced lane: ${byTile[plateFeed]}",
                )
            },
            {
                assertEquals(
                    EAST,
                    (byTile[oreFeed] as? BeltEntity)?.direction,
                    "feed does not face the imported lane: ${byTile[oreFeed]}",
                )
            },
            { assertEquals(emptyList(), dump.failures) },
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
        val belts =
            buildBlueprint(plan, logistics).entities.filter {
                it.name.contains("transport-belt") || it.name.contains("splitter")
            }
        val west = belts.map { it.area.topLeft }.filter { it.x < 2 }
        assertEquals(emptyList(), west, "belts west of the bound")
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
    fun testChestsAreOptIn() {
        val without = buildBlueprint(plan, logistics).entities
        val with = buildBlueprint(plan, logistics, cheats = true).entities

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

        val entities = buildBlueprint(produced, logistics).entities
        val machineArea = entities.single { it.recipe == "top" }.area
        val laneRow = machineArea.topLeft.y - 2
        val eastEnd = machineArea.bottomRight.x

        // a produced lane is fed from its east end, and that feed must face west into the lane
        val feedCoordinate = Coordinate(eastEnd + 1, laneRow)
        val feed =
            assertNotNull(
                entities.singleOrNull { it.area.topLeft == feedCoordinate },
                "no feed east of the lane",
            )
        assertEquals(WEST, feed.direction, "the feed does not face the lane")
    }
}
