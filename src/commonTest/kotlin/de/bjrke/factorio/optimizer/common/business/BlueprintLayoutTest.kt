package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
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

class BlueprintLayoutTest {

    private val logistics =
        LogisticsCalculator(
            testLogistics.copy(fluids = setOf(water, lubricant)),
            Belt.EXPRESS_TRANSPORT_BELT,
            Inserter.FAST_INSERTER,
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
        assertTrue(collisions.isEmpty(), "overlapping tiles: ${collisions.take(5)}")
    }

    @Test
    fun theTargetIsBuiltWithItsProducerAndSourceLane() {
        val names = buildBlueprint(plan, logistics).entities.map { it.name }
        assertAll(
            { assertEquals(1, names.count { it == Factory.ASSEMBLING_MACHINE_3.identifier }) },
            { assertEquals(1, names.count { it == Factory.ASSEMBLING_MACHINE_1.identifier }) },
            { assertTrue(Inserter.FAST_INSERTER.identifier in names) },
            { assertTrue(Belt.EXPRESS_TRANSPORT_BELT.identifier in names) },
            { assertTrue("constant-combinator" in names) },
            { assertTrue("medium-electric-pole" in names) },
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
        assertTrue("pipe-to-ground" in names, names.toString())
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
            { assertTrue("pipe" in names) },
            { assertTrue(Belt.EXPRESS_TRANSPORT_BELT.identifier !in names) },
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
        fun pipesOn(name: String) =
            entities.filter { it.name == name }.mapTo(mutableSetOf()) { it.area.topLeft }
        val pipes = pipesOn("pipe") + pipesOn("pipe-to-ground")
        val grounds = pipesOn("pipe-to-ground")

        // merge pipes that touch; one fluid's lane must be one network with one source, so two
        // different fluids sharing a network shows up as a component holding two pipe-to-grounds
        val components = mutableListOf<Set<Coordinate>>()
        val seen = mutableSetOf<Coordinate>()
        for (pipe in pipes) {
            if (!seen.add(pipe)) continue
            val component = mutableSetOf<Coordinate>()
            var at: Coordinate? = pipe
            val queue = ArrayDeque<Coordinate>()
            while (at != null) {
                component += at
                for (next in
                    listOf(
                        at.west(),
                        at.east(),
                        at.north(),
                        at.south(),
                    )) {
                    if (next in pipes && seen.add(next)) queue += next
                }
                at = queue.removeFirstOrNull()
            }
            components += component
        }

        assertAll(
            { assertEquals(2, grounds.size, "one pipe-to-ground per fluid") },
            {
                assertTrue(
                    components.all { component -> component.count { it in grounds } <= 1 },
                    "two different fluids share one pipe network",
                )
            },
        )
    }

    @Test
    fun insertersFaceTheMaterialTheyMove() {
        val blueprint = buildBlueprint(plan, logistics)
        val occupied = blueprint.entities.flatMap { it.area }.toSet()
        val inserters = blueprint.entities.filter { it.name.endsWith("inserter") }

        assertAll(
            // every inserter takes from the tile it faces, which is always north here: the lane
            // above for an input, the machine above for an output
            { assertTrue(inserters.isNotEmpty()) },
            { assertTrue(inserters.all { it.direction == 0 }, inserters.toString()) },
            {
                val dangling = inserters.filter {
                    val reach = if (it.name == "long-handed-inserter") 2 else 1
                    val inserterPos = it.area.topLeft
                    inserterPos.north(reach) !in occupied
                }
                assertTrue(dangling.isEmpty(), "inserter takes from nothing: $dangling")
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
        assertTrue("long-handed-inserter" in names, names.toString())
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

        // one producer feeding two recipes needs a splitter to fan its belt out
        val names = buildBlueprint(shared, logistics).entities.map { it.name }
        assertTrue(names.any { it.endsWith("-splitter") }, names.toString())
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
            inserters.any { it.name == "long-handed-inserter" && it.direction == 8 },
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
        assertTrue(gear in markers, markers.toString())
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
        val pipes =
            entities
                .filter { it.name == "pipe" || it.name == "pipe-to-ground" }
                .mapTo(mutableSetOf()) { it.area.topLeft }

        val machineTiles =
            entities
                .filter { it.name == Factory.CHEMICAL_PLANT.identifier }
                .flatMapTo(mutableSetOf()) { it.area }

        val reachable = mutableSetOf<Coordinate>()
        var at: Coordinate? = pipes.first()
        val queue = ArrayDeque<Coordinate>()
        while (at != null) {
            if (reachable.add(at)) {
                for (next in
                    listOf(
                        at.west(),
                        at.east(),
                        at.north(),
                        at.south(),
                    )) {
                    if (next in pipes) queue += next
                }
            }
            at = queue.removeFirstOrNull()
        }
        fun touchesMachine(tile: Coordinate) =
            listOf(
                    tile.west(),
                    tile.east(),
                    tile.north(),
                    tile.south(),
                )
                .any { it in machineTiles }

        assertAll(
            { assertEquals(pipes.size, reachable.size, "the fluid lane is split") },
            { assertTrue(reachable.any(::touchesMachine), "no pipe reaches the machine") },
        )
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
        val feed = entities.singleOrNull { it.area.topLeft == feedCoordinate }
        assertAll(
            { assertTrue(feed != null, "no feed east of the lane") },
            { assertEquals(12, feed?.direction, "the feed does not face the lane: $feed") },
        )
    }
}
