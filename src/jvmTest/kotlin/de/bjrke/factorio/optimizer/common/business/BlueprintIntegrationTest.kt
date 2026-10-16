package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import java.util.Base64
import java.util.zip.Inflater
import kotlin.collections.ArrayDeque
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * End-to-end check on the real prototype dump: optimize a target, reduce it to a plan, lay it out
 * and encode it, then decode the string with the JDK's zlib and check it is a blueprint with
 * entities.
 */
class BlueprintIntegrationTest {

    private fun optimize(target: String) =
        Optimizer(
            RecipeProvider.forActiveMods().forSurface(Surface.NAUVIS),
            FactoryConfig().defaults,
        )[Material(target)]

    private fun planFor(target: String): BlueprintPlan {
        val result = optimize(target)
        val logistics =
            LogisticsCalculator(
                result.logistics,
                Belt.FAST_TRANSPORT_BELT,
                Inserter.FAST_INSERTER,
                lanes = 2,
                beltStack = 1,
                inserterStack = 1,
            )
        return assertIs<BlueprintPlanResult.Planned>(blueprintPlan(result, logistics)).plan
    }

    private fun decode(blueprint: String): String {
        val compressed = Base64.getDecoder().decode(blueprint.substring(1))
        val inflater = Inflater()
        inflater.setInput(compressed)
        val output = ByteArray(1 shl 21)
        val size = inflater.inflate(output)
        inflater.end()
        return output.decodeToString(0, size)
    }

    @Test
    fun aRealTargetProducesADecodableBlueprint() {
        val result = optimize("electronic-circuit")
        val logistics =
            LogisticsCalculator(
                result.logistics,
                Belt.FAST_TRANSPORT_BELT,
                Inserter.FAST_INSERTER,
                lanes = 2,
                beltStack = 1,
                inserterStack = 1,
            )

        val planned = assertIs<BlueprintPlanResult.Planned>(blueprintPlan(result, logistics)).plan
        val json = decode(blueprintString(buildBlueprint(planned, logistics).toJson()))

        assertTrue(json.contains("\"blueprint\""), json.take(120))
        assertTrue(json.contains("\"entities\""), "no entities in the blueprint")
    }

    @Test
    fun advancedCircuitsImportPlasticButBuildTheCopperCable() {
        val planned = planFor("advanced-circuit")
        val categories = planned.recipes.flatMap { it.recipe.categories }.toSet()

        assertAll(
            {
                assertTrue(
                    planned.sources.any { it.material == Material("plastic-bar") },
                    "plastic should be imported, sources: ${planned.sources.map { it.material }}",
                )
            },
            {
                assertTrue(
                    planned.recipes.any { it.recipe.name == "copper-cable" },
                    "copper cable should be built, recipes: ${planned.recipes.map { it.recipe.name }}",
                )
            },
            {
                assertTrue(
                    planned.sources.none { it.material == Material("copper-cable") },
                    "copper cable should not be imported",
                )
            },
            {
                assertTrue(
                    planned.sources.any { it.material == Material("copper-plate") },
                    "copper plate should be imported instead",
                )
            },
            {
                assertTrue(
                    categories.none {
                        it in setOf(Category.OIL_PROCESSING, Category.ORGANIC, Category.CRYOGENICS)
                    },
                    "the oil chain should be cut off, categories: $categories",
                )
            },
        )
    }

    @Test
    fun lubricantIsBuiltAndItsHeavyOilIsImported() {
        val planned = planFor("lubricant")

        assertAll(
            { assertTrue(planned.recipes.any { it.recipe.name == "lubricant" }) },
            {
                assertTrue(
                    planned.sources.any { it.material == Material("heavy-oil") && it.fluid },
                    "sources: ${planned.sources.map { it.material }}",
                )
            },
        )
    }

    private fun entitiesFor(target: String, cheats: Boolean = false): List<BlueprintEntity> {
        val result = optimize(target)
        val logistics =
            LogisticsCalculator(
                result.logistics,
                Belt.FAST_TRANSPORT_BELT,
                Inserter.FAST_INSERTER,
                lanes = 2,
                beltStack = 1,
                inserterStack = 1,
            )
        val planned = assertIs<BlueprintPlanResult.Planned>(blueprintPlan(result, logistics)).plan
        return buildBlueprint(planned, logistics, cheats).entities
    }

    private fun assertNoOverlap(entities: List<BlueprintEntity>) {
        val seen = mutableSetOf<Coordinate>()
        val collisions = mutableListOf<Coordinate>()
        for (entity in entities) {
            for (tile in entity.area) {
                if (!seen.add(tile)) collisions += tile
            }
        }
        assertTrue(collisions.isEmpty(), "overlaps at ${collisions.take(5)}")
    }

    @Test
    fun realBlueprintsHaveNoOverlappingEntities() =
        assertAll(
            { assertNoOverlap(entitiesFor("advanced-circuit")) },
            { assertNoOverlap(entitiesFor("electronic-circuit")) },
            { assertNoOverlap(entitiesFor("logistic-science-pack")) },
            { assertNoOverlap(entitiesFor("processing-unit")) },
            { assertNoOverlap(entitiesFor("utility-science-pack")) },
            { assertNoOverlap(entitiesFor("lubricant")) },
        )

    @Test
    fun realCheatBlueprintsHaveNoOverlappingEntities() =
        assertAll(
            { assertNoOverlap(entitiesFor("advanced-circuit", cheats = true)) },
            { assertNoOverlap(entitiesFor("electronic-circuit", cheats = true)) },
            { assertNoOverlap(entitiesFor("utility-science-pack", cheats = true)) },
        )

    private fun assertNoDanglingInserter(entities: List<BlueprintEntity>) {
        val occupied = entities.flatMapTo(mutableSetOf()) { it.area }

        // an inserter always takes from the tile it faces; the long inserter reaches two tiles
        val dangling =
            entities
                .filter { it.name.endsWith("inserter") }
                .filter {
                    val direction = it.direction ?: return@filter false
                    val reach = if (it.name == "long-handed-inserter") 2 else 1
                    val d =
                        Coordinate(
                            when (direction) {
                                4 -> 1
                                12 -> -1
                                else -> 0
                            },
                            when (direction) {
                                0 -> -1
                                8 -> 1
                                else -> 0
                            },
                        )
                    val tile = it.area.topLeft + d * reach
                    tile !in occupied
                }
                .map { "${it.name}@${it.area.topLeft}" }
        assertTrue(dangling.isEmpty(), "inserter facing nothing: $dangling")
    }

    @Test
    fun realBlueprintsHaveNoDanglingInserter() =
        assertAll(
            { assertNoDanglingInserter(entitiesFor("advanced-circuit")) },
            { assertNoDanglingInserter(entitiesFor("electronic-circuit")) },
            { assertNoDanglingInserter(entitiesFor("logistic-science-pack")) },
            { assertNoDanglingInserter(entitiesFor("processing-unit")) },
            { assertNoDanglingInserter(entitiesFor("utility-science-pack")) },
        )

    /**
     * Every underground belt must pair up (input with the output up to nine tiles ahead) and lead
     * into something: an exit is useless when the tile it faces is empty, and an entry is useless
     * when nothing feeds its back.
     */
    private fun assertConnectedUndergroundBelts(entities: List<BlueprintEntity>) {
        val occupied = entities.flatMapTo(mutableSetOf()) { it.area }

        fun step(direction: Int) =
            when (direction) {
                0 -> Coordinate(0, -1)
                4 -> Coordinate(1, 0)
                8 -> Coordinate(0, 1)
                else -> Coordinate(-1, 0)
            }

        val underground = entities.filter { it.name.endsWith("underground-belt") }
        val inputs = underground.filter { it.type == "input" }.map { it.area.topLeft }.toSet()
        val outputs = underground.filter { it.type == "output" }.map { it.area.topLeft }.toSet()
        val problems = mutableListOf<String>()
        for (entity in underground) {
            val step = step(entity.direction ?: 0)
            val topLeft = entity.area.topLeft
            if (entity.type == "input") {
                val exit = (1..9).any { outputs.contains(topLeft + step * it) }
                if (!exit) problems += "input $topLeft has no exit ahead"
                if ((topLeft - step) !in occupied) problems += "input $topLeft is not fed"
            } else {
                val entry = (1..9).any { inputs.contains(topLeft - step * it) }
                if (!entry) problems += "exit $topLeft has no entry behind"
                if ((topLeft + step) !in occupied) problems += "exit $topLeft leads nowhere"
            }
        }
        assertTrue(problems.isEmpty(), problems.take(5).toString())
    }

    @Test
    fun realBlueprintsHaveConnectedUndergroundBelts() =
        assertAll(
            { assertConnectedUndergroundBelts(entitiesFor("advanced-circuit")) },
            { assertConnectedUndergroundBelts(entitiesFor("electronic-circuit")) },
            { assertConnectedUndergroundBelts(entitiesFor("logistic-science-pack")) },
            { assertConnectedUndergroundBelts(entitiesFor("processing-unit")) },
            { assertConnectedUndergroundBelts(entitiesFor("utility-science-pack")) },
        )

    private fun feedFailuresFor(target: String): List<String> {
        val result = optimize(target)
        val logistics =
            LogisticsCalculator(
                result.logistics,
                Belt.FAST_TRANSPORT_BELT,
                Inserter.FAST_INSERTER,
                lanes = 2,
                beltStack = 1,
                inserterStack = 1,
            )
        val plan = assertIs<BlueprintPlanResult.Planned>(blueprintPlan(result, logistics)).plan
        return blueprintFeedFailures(plan, logistics)
    }

    /** Every input belt must reach its lane; a failure means the machine is starved. */
    @Test
    fun realBlueprintsFeedEveryInput() =
        assertAll(
            { assertTrue(feedFailuresFor("advanced-circuit").isEmpty(), "advanced-circuit") },
            { assertTrue(feedFailuresFor("electronic-circuit").isEmpty(), "electronic-circuit") },
            {
                assertTrue(
                    feedFailuresFor("logistic-science-pack").isEmpty(),
                    "logistic-science-pack",
                )
            },
            { assertTrue(feedFailuresFor("processing-unit").isEmpty(), "processing-unit") },
            { assertTrue(feedFailuresFor("copper-cable").isEmpty(), "copper-cable") },
            { assertTrue(feedFailuresFor("iron-gear-wheel").isEmpty(), "iron-gear-wheel") },
        )

    /** Every machine tile must sit in a pole's supply area, and the poles must form one network. */
    private fun assertPolesCoverAndConnect(entities: List<BlueprintEntity>) {
        val poles =
            entities
                .asSequence()
                .filter { it.name == "medium-electric-pole" }
                .map { it.area.topLeft }

        val coveredByPoles =
            poles.flatMapTo(mutableSetOf()) { p ->
                sequence {
                    for (x in p.x - 3..p.x + 3) {
                        for (y in p.y - 3..p.y + 3) {
                            yield(Coordinate(x, y))
                        }
                    }
                }
            }

        val unpowered = entities.filter {
            it.isPowered() && (it.area intersect coveredByPoles).isEmpty()
        }

        val seen = mutableSetOf<Coordinate>()
        var components = 0
        for (pole in poles) {
            if (!seen.add(pole)) continue
            components++
            val queue = ArrayDeque(listOf(pole))
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                for (other in poles) {
                    if (other in seen) continue
                    val reach = current.distance(other)
                    if (reach <= 9.0) {
                        seen += other
                        queue += other
                    }
                }
            }
        }

        assertAll(
            { assertEquals(emptyList(), unpowered) },
            { assertTrue(components <= 1, "$components separate power networks") },
        )
    }

    @Test
    fun realBlueprintsPowerEveryMachine() =
        assertTableOf(
            "advanced-circuit",
            "electronic-circuit",
            "logistic-science-pack",
            "processing-unit",
            "utility-science-pack",
            "lubricant",
        ) {
            assertPolesCoverAndConnect(entitiesFor(it))
        }

    /** A blueprint does not rebuild pole wires, so each pole must list the poles it is wired to. */
    private fun assertPolesWired(entities: List<BlueprintEntity>) {
        val poles = entities.withIndex().filter { it.value.name == "medium-electric-pole" }
        val numbers = poles.map { it.index + 1 }.toSet()
        val seen = mutableSetOf<Int>()
        var components = 0
        for ((index) in poles) {
            val number = index + 1
            if (!seen.add(number)) continue
            components++
            val queue = ArrayDeque(listOf(number))
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                for (neighbour in entities[current - 1].neighbours) {
                    if (neighbour in numbers && seen.add(neighbour)) queue += neighbour
                }
            }
        }
        assertAll(
            {
                assertTrue(
                    poles.size < 2 || poles.all { it.value.neighbours.isNotEmpty() },
                    "unwired poles",
                )
            },
            { assertTrue(components <= 1, "$components pole wire networks") },
        )
    }

    @Test
    fun realBlueprintsWireTheirPoles() =
        assertAll(
            { assertPolesWired(entitiesFor("advanced-circuit")) },
            { assertPolesWired(entitiesFor("logistic-science-pack")) },
            { assertPolesWired(entitiesFor("processing-unit")) },
            { assertPolesWired(entitiesFor("utility-science-pack")) },
        )

    /** A belt tile must not be fed by two belts of different materials (they would merge). */
    private fun assertNoMergingBelts(
        entities: List<BlueprintEntity>,
        materials: Map<Coordinate, Material>,
    ) {
        val belts =
            entities
                .filter {
                    it.name.contains("transport-belt") &&
                        !it.name.contains("underground") &&
                        it.direction != null
                }
                .associate {
                    it.area.topLeft to it.direction!!
                }

        fun step(direction: Int) =
            when (direction) {
                0 -> Coordinate(0, -1)
                4 -> Coordinate(1, 0)
                8 -> Coordinate(0, 1)
                else -> Coordinate(-1, 0)
            }

        val merges =
            belts
                .filter { (tile, _) ->
                    val feeding = belts.filter { (from, direction) ->
                        val (dx, dy) = step(direction)
                        Coordinate(from.x + dx, from.y + dy) == tile
                    }
                    feeding.map { materials[it.key] }.distinct().size > 1
                }
                .keys
        assertTrue(merges.isEmpty(), "belts fed by several materials: ${merges.take(5)}")
    }

    private fun beltMaterialsFor(target: String): Map<Coordinate, Material> {
        val result = optimize(target)
        val logistics =
            LogisticsCalculator(
                result.logistics,
                Belt.FAST_TRANSPORT_BELT,
                Inserter.FAST_INSERTER,
                lanes = 2,
                beltStack = 1,
                inserterStack = 1,
            )
        val plan = assertIs<BlueprintPlanResult.Planned>(blueprintPlan(result, logistics)).plan
        return blueprintBeltMaterials(plan, logistics)
    }

    @Test
    fun realBlueprintsDoNotMergeMaterials() =
        assertAll(
            {
                assertNoMergingBelts(
                    entitiesFor("inserter"),
                    beltMaterialsFor("inserter"),
                )
            },
            {
                assertNoMergingBelts(
                    entitiesFor("advanced-circuit"),
                    beltMaterialsFor("advanced-circuit"),
                )
            },
            {
                assertNoMergingBelts(
                    entitiesFor("electronic-circuit"),
                    beltMaterialsFor("electronic-circuit"),
                )
            },
        )
}
