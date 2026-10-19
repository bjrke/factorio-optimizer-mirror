package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.factorio.optimizer.common.model.Direction.NORTH
import de.bjrke.factorio.optimizer.jvm.parseArgs
import de.bjrke.test.assertAll
import java.util.Base64
import java.util.zip.Inflater
import kotlin.test.*

/** The chest markers the player can pick; each places inserters that need power. */
val chestMarkers =
    listOf(
        BeltEnd.BUFFER_CHEST,
        BeltEnd.STORAGE_CHEST,
        BeltEnd.WOODEN_CHEST,
        BeltEnd.STEEL_CHEST,
    )

fun optimize(target: String) =
    Optimizer(
        RecipeProvider.forActiveMods().forSurface(Surface.NAUVIS),
        FactoryConfig().defaults,
    )[Material(target)]

fun planAndLogistics(target: String): Pair<BlueprintPlan, LogisticsCalculator> {
    val result = optimize(target)
    val logistics =
        LogisticsCalculator(
            result.logistics,
            BeltTier.FAST_TRANSPORT_BELT,
            InserterTier.FAST_INSERTER,
            lanes = 2,
            beltStack = 1,
            inserterStack = 1,
        )
    val plan = assertIs<BlueprintPlanResult.Planned>(blueprintPlan(result, logistics)).plan
    return plan to logistics
}

fun planFor(target: String): BlueprintPlan = planAndLogistics(target).first

/** Resolves a pasted web-UI URL exactly like the CLI, so a reported layout can be replayed. */
fun planFromUrl(url: String): Triple<BlueprintPlan, LogisticsCalculator, BeltEnd> {
    val cli = parseArgs(arrayOf(url))
    val parsedRecipes = RecipeProvider.forActiveMods(cli.spaceAge).forSurface(cli.surface)
    val factoryConfig = FactoryConfig(cli.spaceAge)
    val factories =
        cli.factories
            .filter { it in factoryConfig.available }
            .fold(factoryConfig.fromUrl(cli.selection)) { chosen, factory ->
                factoryConfig.add(chosen, factory)
            }
    val logisticsConfig = LogisticsConfig(parsedRecipes.logistics, cli.spaceAge)
    val inserter =
        cli.inserter?.takeIf { it in logisticsConfig.availableInserters }
            ?: logisticsConfig.inserter(cli.selection)
    val belt =
        cli.belt?.takeIf { it in logisticsConfig.availableBelts }
            ?: logisticsConfig.belt(cli.selection)
    val lanes =
        if (cli.fullBelt == true) BeltTier.MAX_LANES else logisticsConfig.beltLanes(cli.selection)
    val beltStack = logisticsConfig.beltStack(cli.beltStack, inserter)
    val inserterStack = logisticsConfig.inserterStack(cli.inserterStack, inserter)
    val logistics =
        LogisticsCalculator(
            parsedRecipes.logistics,
            belt,
            inserter,
            lanes,
            beltStack,
            inserterStack,
        )
    val result = Optimizer(parsedRecipes, factories)[Material(cli.material)]
    val plan =
        assertIs<BlueprintPlanResult.Planned>(
                blueprintPlan(result, logistics, cli.normalizeTarget),
            )
            .plan
    return Triple(plan, logistics, cli.beltEnd)
}

fun decode(blueprint: String): String {
    val compressed = Base64.getDecoder().decode(blueprint.substring(1))
    val inflater = Inflater()
    inflater.setInput(compressed)
    val output = ByteArray(1 shl 21)
    val size = inflater.inflate(output)
    inflater.end()
    return output.decodeToString(0, size)
}

fun entitiesFor(
    target: String,
    beltEnd: BeltEnd = BeltEnd.CONSTANT_COMBINATOR,
): List<BlueprintEntity> {
    val result = optimize(target)
    val logistics =
        LogisticsCalculator(
            result.logistics,
            BeltTier.FAST_TRANSPORT_BELT,
            InserterTier.FAST_INSERTER,
            lanes = 2,
            beltStack = 1,
            inserterStack = 1,
        )
    val planned = assertIs<BlueprintPlanResult.Planned>(blueprintPlan(result, logistics)).plan
    return buildBlueprint(planned, logistics, beltEnd).entities
}

@OptIn(ExperimentalKotlinTestApi::class)
fun assertNoOverlap(entities: List<BlueprintEntity>) {
    val seen = mutableMapOf<Coordinate, BlueprintEntity>()
    for (entity in entities) {
        for (tile in entity.area) {
            val existing = seen.put(tile, entity)
            assertNull(existing) { "${entity.name} and  ${existing?.name} overlaps at $tile" }
        }
    }
}

fun assertNoDanglingInserter(entities: List<BlueprintEntity>) {
    val occupied = entities.flatMapTo(mutableSetOf()) { it.area }

    // an inserter always takes from the tile it faces; the long inserter reaches two tiles
    val dangling =
        entities
            .filter { it.name.endsWith("inserter") }
            .filter {
                val direction = it.direction ?: return@filter false
                val reach = if (it.name == "long-handed-inserter") 2 else 1
                val tile = it.area.topLeft + direction.step * reach
                tile !in occupied
            }
            .map { "${it.name}@${it.area.topLeft}" }
    assertEquals(emptyList(), dangling, "inserter facing nothing")
}

/**
 * A material must reach a belt from one allocation only. `producerOf` in the layout has one belt
 * source per material, so a second belt-fed producer would be built with a product belt no delivery
 * ever uses — the "factory with an unconnected output belt" a band already covers.
 */
fun assertOneBeltSourcePerMaterial(plan: BlueprintPlan) {
    // a band inserts its link machine to machine, so that producer never puts material on a belt
    val inserted =
        plan.bands.flatMapTo(mutableSetOf()) { band ->
            band.links.map { link ->
                val machine = if (link.producerIsLeft) link.gap else link.gap + 1
                band.machines[machine] to link.material
            }
        }
    val sources =
        plan.recipes
            .withIndex()
            .flatMap { (index, allocation) ->
                allocation.recipe.result.keys.map { index to it }
            }
            .filterNot { it in inserted }
            .groupBy({ it.second }, { it.first })
    val doubled = sources.filterValues { it.size > 1 }

    assertEquals(emptyMap(), doubled, "material put on a belt by more than one allocation")
}

/** An import connector: a label, chest or inserter sits right next to the buried run. */
fun List<BlueprintEntity>.isImportConnector(entity: BlueprintEntity) = any {
    (it.name == "constant-combinator" ||
        it.name.endsWith("chest") ||
        it.name.endsWith("inserter")) && it.area.topLeft.manhattanDist(entity.area.topLeft) == 1
}

/**
 * Every underground belt must pair up (input with the output up to nine tiles ahead) and lead into
 * something: an exit is useless when the tile it faces is empty, and an entry is useless when
 * nothing feeds its back.
 */
fun assertConnectedUndergroundBelts(entities: List<BlueprintEntity>) {
    val occupied = entities.flatMapTo(mutableSetOf()) { it.area }

    val underground = entities.filter { it.name.endsWith("underground-belt") }
    val inputs = underground.filter { it.type == Io.INPUT }.map { it.area.topLeft }.toSet()
    val outputs = underground.filter { it.type == Io.OUTPUT }.map { it.area.topLeft }.toSet()
    val problems = mutableListOf<String>()
    for (entity in underground) {
        if (entities.isImportConnector(entity)) continue
        val step = (entity.direction ?: NORTH).step
        val topLeft = entity.area.topLeft
        if (entity.type == Io.INPUT) {
            val exit = (1..9).any { outputs.contains(topLeft + step * it) }
            if (!exit) problems += "input $topLeft has no exit ahead"
            if ((topLeft - step) !in occupied) problems += "input $topLeft is not fed"
            // an exit empties onto a surface belt; an input right there cannot take it
            if ((topLeft - step) in outputs)
                problems += "input $topLeft is fed by an exit, not a belt"
        } else {
            val entry = (1..9).any { inputs.contains(topLeft - step * it) }
            if (!entry) problems += "exit $topLeft has no entry behind"
            if ((topLeft + step) !in occupied) problems += "exit $topLeft leads nowhere"
            // an exit feeds a surface belt, never the entrance of the next run directly
            if ((topLeft + step) in inputs) problems += "exit $topLeft leads straight into an input"
        }
    }
    assertEquals(emptyList(), problems)
}

/**
 * A splitter output may not enter an underground belt directly: that feeds one side of the tunnel,
 * and a route can be left with an orphaned tunnel end when the two disagree.
 */
fun assertSplittersOnlyFeedSurface(entities: List<BlueprintEntity>) {
    val inputs =
        entities
            .filter { it.name.endsWith("underground-belt") && it.type == Io.INPUT }
            .map { it.area.topLeft }
            .toSet()
    val problems =
        entities
            .filter { it.name.endsWith("splitter") }
            .flatMap { splitter ->
                val direction = splitter.direction ?: return@flatMap emptyList()
                splitter.area.mapNotNull { tile ->
                    val downstream = tile + direction.step
                    if (downstream in inputs) "$tile -> $downstream" else null
                }
            }
    assertEquals(emptyList(), problems, "splitter output feeds underground belt")
}

/**
 * A run shorter than the belt tier's reach must not still have a belt of its own material to
 * swallow on either end: `stretchUndergroundRuns` grows it as far as the straight run allows.
 */
fun assertUndergroundRunsAreStretched(
    entities: List<BlueprintEntity>,
    materials: Map<Coordinate, Material>,
) {
    val byTile = entities.associateBy { it.area.topLeft }
    fun range(name: String) =
        when {
            name.contains("turbo") -> 11
            name.contains("express") -> 9
            name.contains("fast") -> 7
            else -> 5
        }
    fun plainBelt(tile: Coordinate, direction: Direction, material: Material?) =
        byTile[tile]?.let {
            it.name.endsWith("transport-belt") && it.direction == direction
        } == true && materials[tile] == material

    fun underground(tile: Coordinate) = byTile[tile]?.name?.endsWith("underground-belt") == true

    val problems = mutableListOf<String>()
    for (input in entities.filter { it.name.endsWith("underground-belt") && it.type == Io.INPUT }) {
        // the import connector is a fixed marker pair, not a route to stretch
        if (entities.isImportConnector(input)) continue
        val direction = input.direction ?: continue
        val step = direction.step
        val limit = range(input.name)
        var reach = 0
        var output: BlueprintEntity? = null
        for (k in 2..limit) {
            val entity = byTile[input.area.topLeft + step * k]
            // the partner carries the same material: another run may tunnel in the same column
            if (
                entity != null &&
                    entity.type == Io.OUTPUT &&
                    entity.name == input.name &&
                    materials[entity.area.topLeft] == materials[input.area.topLeft]
            ) {
                reach = k
                output = entity
                break
            }
        }
        if (output == null || reach >= limit) continue
        val material = materials[input.area.topLeft]
        val back = input.area.topLeft - step
        // the new end must still join a surface belt, not the next run's underground end
        if (
            plainBelt(back, direction, material) &&
                back - step in byTile &&
                !underground(back - step)
        ) {
            problems += "input ${input.area.topLeft} can still grow back"
        }
        val forward = output.area.topLeft + step
        if (
            plainBelt(forward, direction, material) &&
                forward + step in byTile &&
                !underground(forward + step)
        ) {
            problems += "output ${output.area.topLeft} can still grow forward"
        }
    }
    assertEquals(emptyList(), problems.take(5))
}

fun feedFailuresFor(target: String): List<String> {
    val result = optimize(target)
    val logistics =
        LogisticsCalculator(
            result.logistics,
            BeltTier.FAST_TRANSPORT_BELT,
            InserterTier.FAST_INSERTER,
            lanes = 2,
            beltStack = 1,
            inserterStack = 1,
        )
    val plan = assertIs<BlueprintPlanResult.Planned>(blueprintPlan(result, logistics)).plan
    return blueprintFeedFailures(plan, logistics)
}

/** Every machine tile must sit in a pole's supply area, and the poles must form one network. */
fun assertPolesCoverAndConnect(entities: List<BlueprintEntity>) {
    val poles =
        entities.asSequence().filter { it.name == "medium-electric-pole" }.map { it.area.topLeft }

    val coveredByPoles =
        poles.flatMapTo(mutableSetOf()) { p ->
            p.area().grow(3)
        }

    val unpowered = entities.filter {
        it.isPowered() && (it.area intersect coveredByPoles).isEmpty()
    }

    val seen = mutableSetOf<Coordinate>()
    var components = 0
    for (pole in poles) {
        if (!seen.add(pole)) continue
        components++
        val queue = ArrayDeque<Coordinate>()
        var current: Coordinate? = pole
        while (current != null) {
            for (other in poles) {
                if (other !in seen) {
                    if (current.distanceSq(other) <= 9 * 9) {
                        seen += other
                        queue += other
                    }
                }
            }
            current = queue.removeFirstOrNull()
        }
    }

    assertAll(
        { assertEquals(emptyList(), unpowered) },
        { assertTrue(components <= 1, "$components separate power networks") },
    )
}

/**
 * Every fluid's pipe tiles must form one network. A pipe-to-ground only joins on the side its
 * surface pipe attaches on (plus its buried partner), so a run that turned on an end would leave
 * part of the fluid unreachable.
 */
fun fluidNetworkProblems(entities: List<LayoutEntity>): List<String> {
    val plumbing = entities.filterIsInstance<Plumbing>().associateBy { it.area.topLeft }
    val reach = 10

    fun mouthOf(pipe: PipeToGroundEntity) =
        if (pipe.io == Io.INPUT) pipe.direction.opposite() else pipe.direction

    fun links(tile: Coordinate): Set<Coordinate> {
        val entity = plumbing[tile] ?: return emptySet()
        val result = mutableSetOf<Coordinate>()
        when (entity) {
            is PipeEntity,
            is InfinityPipeEntity ->
                Direction.entries.forEach { direction ->
                    val next = tile + direction.step
                    if (next in plumbing) result += next
                }
            is PipeToGroundEntity -> {
                val mouth = mouthOf(entity)
                val front = tile + mouth.step
                val neighbour = plumbing[front]
                if (
                    neighbour is PipeEntity ||
                        neighbour is InfinityPipeEntity ||
                        (neighbour is PipeToGroundEntity && mouthOf(neighbour) == mouth.opposite())
                ) {
                    result += front
                }
                for (distance in 2..reach) {
                    val toward =
                        if (entity.io == Io.INPUT) entity.direction.step
                        else entity.direction.step * -1
                    val other = plumbing[tile + toward * distance]
                    if (
                        other is PipeToGroundEntity &&
                            other.material == entity.material &&
                            other.direction == entity.direction &&
                            other.io != entity.io
                    ) {
                        result += other.area.topLeft
                        break
                    }
                }
            }
        }
        return result
    }

    // only mutual links count: a pipe beside a pipe-to-ground's flank does not connect
    val adjacency =
        plumbing.keys.associateWith { tile ->
            links(tile).filter { links(it).contains(tile) }.toSet()
        }
    val seen = mutableSetOf<Coordinate>()
    val component = mutableMapOf<Coordinate, Int>()
    var components = 0
    for (tile in plumbing.keys) {
        if (!seen.add(tile)) continue
        val queue = ArrayDeque(listOf(tile))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            component[current] = components
            for (next in adjacency.getValue(current)) if (seen.add(next)) queue += next
        }
        components++
    }

    return plumbing.values
        .groupBy { it.material }
        .flatMap { (material, pipes) ->
            val parts = pipes.map { component.getValue(it.area.topLeft) }.distinct()
            if (parts.size <= 1) emptyList()
            else listOf("${material.name} is split over ${parts.size} networks")
        }
}

/** A blueprint does not rebuild pole wires, so each pole must list the poles it is wired to. */
fun assertPolesWired(entities: List<BlueprintEntity>) {
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

/** A belt tile must not be fed by two belts of different materials (they would merge). */
fun assertNoMergingBelts(
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

    val merges =
        belts
            .filter { (tile, _) ->
                val feeding = belts.filter { (from, direction) -> from + direction.step == tile }
                feeding.map { materials[it.key] }.distinct().size > 1
            }
            .keys
    assertEquals(emptySet(), merges, "belts fed by several materials")
}

fun transportMaterialsFor(target: String): Map<Coordinate, Material> {
    val result = optimize(target)
    val logistics =
        LogisticsCalculator(
            result.logistics,
            BeltTier.FAST_TRANSPORT_BELT,
            InserterTier.FAST_INSERTER,
            lanes = 2,
            beltStack = 1,
            inserterStack = 1,
        )
    val plan = assertIs<BlueprintPlanResult.Planned>(blueprintPlan(result, logistics)).plan
    return blueprintTransportMaterials(plan, logistics)
}
