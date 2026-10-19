package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.factorio.optimizer.common.model.Direction.NORTH
import de.bjrke.factorio.optimizer.jvm.parseArgs
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import de.bjrke.test.assertTable
import de.bjrke.test.assertTableOf
import java.util.Base64
import java.util.zip.Inflater
import kotlin.test.*

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

    private fun planAndLogistics(target: String): Pair<BlueprintPlan, LogisticsCalculator> {
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

    private fun planFor(target: String): BlueprintPlan = planAndLogistics(target).first

    /** Resolves a pasted web-UI URL exactly like the CLI, so a reported layout can be replayed. */
    private fun planFromUrl(url: String): Triple<BlueprintPlan, LogisticsCalculator, BeltEnd> {
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
            if (cli.fullBelt == true) BeltTier.MAX_LANES
            else logisticsConfig.beltLanes(cli.selection)
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
                BeltTier.FAST_TRANSPORT_BELT,
                InserterTier.FAST_INSERTER,
                lanes = 2,
                beltStack = 1,
                inserterStack = 1,
            )

        val planned = assertIs<BlueprintPlanResult.Planned>(blueprintPlan(result, logistics)).plan
        val json = decode(blueprintString(buildBlueprint(planned, logistics).toJson()))

        assertContains(json, "\"blueprint\"", message = json.take(120))
        assertContains(json, "\"entities\"", message = "no entities in the blueprint")
    }

    @Test
    fun advancedCircuitsImportPlasticButBuildTheCopperCable() {
        val planned = planFor("advanced-circuit")
        val categories = planned.recipes.flatMap { it.recipe.categories }.toSet()
        val sourceMaterials = planned.sources.map { it.material }
        val recipeNames = planned.recipes.map { it.recipe.name }

        assertAll(
            {
                assertContains(
                    sourceMaterials,
                    Material("plastic-bar"),
                    "plastic should be imported",
                )
            },
            {
                assertContains(
                    recipeNames,
                    "copper-cable",
                    "copper cable should be built",
                )
            },
            {
                assertContainsNot(
                    sourceMaterials,
                    Material("copper-cable"),
                    "copper cable should not be imported",
                )
            },
            {
                assertContains(
                    sourceMaterials,
                    Material("copper-plate"),
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
            { assertContains(planned.recipes.map { it.recipe.name }, "lubricant") },
            {
                assertTrue(
                    planned.sources.any { it.material == Material("heavy-oil") && it.fluid },
                    "sources: ${planned.sources.map { it.material }}",
                )
            },
        )
    }

    private fun entitiesFor(
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
    private fun assertNoOverlap(entities: List<BlueprintEntity>) {
        val seen = mutableMapOf<Coordinate, BlueprintEntity>()
        for (entity in entities) {
            for (tile in entity.area) {
                val existing = seen.put(tile, entity)
                assertNull(existing) { "${entity.name} and  ${existing?.name} overlaps at $tile" }
            }
        }
    }

    @Test
    fun realBlueprintsHaveNoOverlappingEntities() =
        assertTableOf(
            "advanced-circuit",
            "electronic-circuit",
            "logistic-science-pack",
            "processing-unit",
            "utility-science-pack",
            "lubricant",
        ) {
            assertNoOverlap(entitiesFor(it))
        }

    @Test
    fun realCheatBlueprintsHaveNoOverlappingEntities() =
        assertTableOf(
            "advanced-circuit",
            "electronic-circuit",
            "utility-science-pack",
            "processing-unit",
        ) {
            assertNoOverlap(entitiesFor(it, beltEnd = BeltEnd.INFINITY_CHEST))
        }

    @Test
    fun theCheatFluidImportIsAnInfinityPipeAtTheFluidsDefaultTemperature() {
        val entities = entitiesFor("processing-unit", BeltEnd.INFINITY_CHEST)
        val pipe = entities.singleOrNull { it.name == "infinity-pipe" }

        assertNotNull(pipe) { "no infinity pipe for the imported sulfuric acid" }
        assertAll(
            { assertEquals(Material("sulfuric-acid"), pipe.infinityPipe?.fluid) },
            { assertEquals(25, pipe.infinityPipe?.temperature) },
        )
    }

    private fun assertNoDanglingInserter(entities: List<BlueprintEntity>) {
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

    @Test
    fun realBlueprintsHaveNoDanglingInserter() =
        assertTableOf(
            "advanced-circuit",
            "electronic-circuit",
            "logistic-science-pack",
            "processing-unit",
            "utility-science-pack",
        ) {
            assertNoDanglingInserter(entitiesFor(it))
        }

    /**
     * A material must reach a belt from one allocation only. `producerOf` in the layout has one
     * belt source per material, so a second belt-fed producer would be built with a product belt no
     * delivery ever uses — the "factory with an unconnected output belt" a band already covers.
     */
    private fun assertOneBeltSourcePerMaterial(plan: BlueprintPlan) {
        // a band inserts its link machine to machine, so that producer never puts material on a
        // belt
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

    @Test
    fun realBlueprintsPutEveryMaterialOnAtMostOneBelt() =
        assertTableOf(
            "advanced-circuit",
            "electronic-circuit",
            "logistic-science-pack",
            "processing-unit",
            "utility-science-pack",
            "lubricant",
        ) {
            assertOneBeltSourcePerMaterial(planFor(it))
        }

    @Test
    fun theReportedLogisticSciencePackUrlBuildsNoRedundantFactory() {
        val (plan, _, _) =
            planFromUrl("http://localhost:8080/#/2.5/logistic-science-pack/CFSXbmr/1/1")
        val machines =
            plan.recipes
                .groupingBy { it.recipe.name }
                .fold(0) { sum, allocation -> sum + allocation.machines.toInt() }

        assertAll(
            { assertOneBeltSourcePerMaterial(plan) },
            // cable + EC and gear + inserter are the direct-feed bands; the inserter band's output
            // already feeds the science row, so no second EC or inserter factory is built, and the
            // foundry takes a single belt-fed gear machine instead of one per whole consumer
            // machine
            {
                assertEquals(
                    mapOf(
                        "copper-cable" to 1,
                        "electronic-circuit" to 1,
                        "iron-gear-wheel" to 2,
                        "inserter" to 1,
                        "transport-belt" to 1,
                        "logistic-science-pack" to 9,
                    ),
                    machines,
                )
            },
        )
    }

    /**
     * Every underground belt must pair up (input with the output up to nine tiles ahead) and lead
     * into something: an exit is useless when the tile it faces is empty, and an entry is useless
     * when nothing feeds its back.
     */
    /** An import connector: a label, chest or inserter sits right next to the buried run. */
    private fun List<BlueprintEntity>.isImportConnector(entity: BlueprintEntity) = any {
        (it.name == "constant-combinator" ||
            it.name.endsWith("chest") ||
            it.name.endsWith("inserter")) && it.area.topLeft.manhattanDist(entity.area.topLeft) == 1
    }

    private fun assertConnectedUndergroundBelts(entities: List<BlueprintEntity>) {
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
                if ((topLeft + step) in inputs)
                    problems += "exit $topLeft leads straight into an input"
            }
        }
        assertEquals(emptyList(), problems)
    }

    @Test
    fun realBlueprintsHaveConnectedUndergroundBelts() =
        assertTableOf(
            "advanced-circuit",
            "electronic-circuit",
            "logistic-science-pack",
            "processing-unit",
            "utility-science-pack",
        ) {
            assertConnectedUndergroundBelts(entitiesFor(it))
        }

    /**
     * A splitter output may not enter an underground belt directly: that feeds one side of the
     * tunnel, and a route can be left with an orphaned tunnel end when the two disagree.
     */
    private fun assertSplittersOnlyFeedSurface(entities: List<BlueprintEntity>) {
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

    @Test
    fun splitterBranchesStartOnTheSurface() {
        val (plan, logistics, beltEnd) =
            planFromUrl("http://localhost:8080/#/4.5/advanced-circuit/CFSXbmr/1/1")
        val entities = buildBlueprint(plan, logistics, beltEnd).entities

        assertAll(
            { assertSplittersOnlyFeedSurface(entities) },
            { assertConnectedUndergroundBelts(entities) },
        )
    }

    /**
     * Smoke test for the weights: this layout is small enough to stay entirely on the surface, so
     * any underground belt means a cost or lane-direction regression, not a genuine obstacle.
     */
    @Test
    fun compactionHalvesTheAdvancedCircuitLayout() {
        val (plan, logistics) = planAndLogistics("advanced-circuit")
        val dump = blueprintLayoutDump(plan, logistics)
        val width =
            dump.entities.maxOf { it.area.bottomRight.x } -
                dump.entities.minOf { it.area.topLeft.x } + 1
        val height =
            dump.entities.maxOf { it.area.bottomRight.y } -
                dump.entities.minOf { it.area.topLeft.y } + 1

        assertAll(
            { assertTrue(width < 40, "layout is still $width tiles wide") },
            { assertTrue(height < 80, "layout is still $height tiles tall") },
            { assertEquals(emptyList(), dump.failures) },
            { assertEquals(emptyList(), validateLayout(dump.entities)) },
        )
    }

    @Test
    fun reportedUrlKeepsInputLanesAtTheMachineEdge() =
        assertTableOf(BeltEnd.CONSTANT_COMBINATOR, BeltEnd.INFINITY_CHEST) { beltEnd ->
            val (plan, logistics, _) =
                planFromUrl("http://localhost:8080/#/4.5/advanced-circuit/CFSXbmr/1/1")
            val dump = blueprintLayoutDump(plan, logistics, beltEnd)
            val entities = dump.entities
            val recipes = plan.recipes.associate { it.recipe.name to it.recipe }
            val belts = entities.filterIsInstance<BeltEntity>()

            val missing =
                entities.filterIsInstance<MachineEntity>().mapNotNull { machine ->
                    val recipe = recipes[machine.recipe] ?: return@mapNotNull null
                    val solids = recipe.ingredients.keys.filterNot(logistics::isFluid).toSet()
                    if (solids.isEmpty()) return@mapNotNull null
                    val laneRows =
                        setOf(
                            machine.area.topLeft.y - 2,
                            machine.area.topLeft.y - 3,
                            machine.area.bottomRight.y + 3,
                        )
                    val reachesEdge = belts.any {
                        it.area.topLeft.x == machine.area.topLeft.x &&
                            it.area.topLeft.y in laneRows &&
                            it.material in solids
                    }
                    if (reachesEdge) null else "${machine.recipe}@${machine.area.topLeft}"
                }

            assertAll(
                { assertEquals(emptyList(), dump.failures) },
                {
                    assertEquals(
                        emptyList(),
                        missing,
                        "input lanes do not reach the machine edge",
                    )
                },
            )
        }

    /**
     * The reported advanced-circuit URL must still replay without a single failed feed. It is built
     * as a direct-feed band now, which puts the two cable rows in the feed corridor west of the
     * machines; only the band's own copper-cable feed needs to tunnel there, every other material
     * still reaches its lane on the surface.
     */
    @Test
    fun advancedCircuitUrlOnlyTunnelsForItsCableFeed() {
        val (plan, logistics, beltEnd) =
            planFromUrl("http://localhost:8080/#/4.5/advanced-circuit/CFSXbmr/1/1")
        val dump = blueprintLayoutDump(plan, logistics, beltEnd)
        // the import connectors are fixed marker pairs; only route tunnels are checked here
        val routeTunnels =
            dump.entities.filterIsInstance<UndergroundBeltEntity>().filter { tunnel ->
                dump.entities.none {
                    it is MarkerEntity && it.area.topLeft.manhattanDist(tunnel.area.topLeft) == 1
                }
            }

        assertAll(
            { assertEquals(emptyList(), dump.failures) },
            {
                assertEquals(
                    listOf(Material("copper-cable")),
                    routeTunnels.map { it.material }.distinct(),
                    "only the band's cable feed may need an underground belt",
                )
            },
        )
    }

    /**
     * A run shorter than the belt tier's reach must not still have a belt of its own material to
     * swallow on either end: [RowLayout.stretchUndergroundRuns] grows it as far as the straight run
     * allows.
     */
    private fun assertUndergroundRunsAreStretched(
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
        for (input in
            entities.filter { it.name.endsWith("underground-belt") && it.type == Io.INPUT }) {
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

    @Test
    fun realBlueprintsStretchUndergroundRuns() =
        assertTableOf("advanced-circuit", "electronic-circuit", "processing-unit") { target ->
            assertUndergroundRunsAreStretched(
                entitiesFor(target),
                transportMaterialsFor(target),
            )
        }

    private fun feedFailuresFor(target: String): List<String> {
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

    /** Every input belt must reach its lane; a failure means the machine is starved. */
    @Test
    fun realBlueprintsFeedEveryInput() =
        assertTableOf(
            "advanced-circuit",
            "electronic-circuit",
            "logistic-science-pack",
            "processing-unit",
            "utility-science-pack",
            "copper-cable",
            "iron-gear-wheel",
        ) {
            assertEquals(emptyList(), feedFailuresFor(it))
        }

    /** Every machine tile must sit in a pole's supply area, and the poles must form one network. */
    private fun assertPolesCoverAndConnect(entities: List<BlueprintEntity>) {
        val poles =
            entities
                .asSequence()
                .filter { it.name == "medium-electric-pole" }
                .map { it.area.topLeft }

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

    @Test
    fun realBlueprintsHaveValidPlumbing() =
        assertTableOf(
            "advanced-circuit",
            "electronic-circuit",
            "logistic-science-pack",
            "processing-unit",
            "utility-science-pack",
            "lubricant",
            "plastic-bar",
        ) { target ->
            val (plan, logistics) = planAndLogistics(target)
            val dump = blueprintLayoutDump(plan, logistics)
            // only the fluid checks here: utility-science-pack has a pre-existing belt side-load
            val plumbing =
                validateLayout(dump.entities).filter {
                    it.contains("fluid") || it.contains("underground")
                }
            assertEquals(emptyList(), plumbing)
        }

    /**
     * Every fluid's pipe tiles must form one network. A pipe-to-ground only joins on the side its
     * surface pipe attaches on (plus its buried partner), so a run that turned on an end would
     * leave part of the fluid unreachable.
     */
    private fun fluidNetworkProblems(entities: List<LayoutEntity>): List<String> {
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
                            (neighbour is PipeToGroundEntity &&
                                mouthOf(neighbour) == mouth.opposite())
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

    @Test
    fun realFluidNetworksAreConnected() =
        assertTableOf(
            "advanced-circuit",
            "electronic-circuit",
            "logistic-science-pack",
            "processing-unit",
            "utility-science-pack",
            "lubricant",
            "plastic-bar",
        ) { target ->
            val (plan, logistics) = planAndLogistics(target)
            val dump = blueprintLayoutDump(plan, logistics)
            assertEquals(emptyList(), fluidNetworkProblems(dump.entities))
        }

    /** The chest markers the player can pick; each places inserters that need power. */
    private val chestMarkers =
        listOf(
            BeltEnd.BUFFER_CHEST,
            BeltEnd.STORAGE_CHEST,
            BeltEnd.WOODEN_CHEST,
            BeltEnd.STEEL_CHEST,
        )

    @Test
    fun realChestMarkerBlueprintsHaveNoOverlappingEntities() =
        assertTable(chestMarkers) { beltEnd ->
            assertNoOverlap(entitiesFor("electronic-circuit", beltEnd))
        }

    @Test
    fun realChestMarkerBlueprintsHaveConnectedUndergroundBelts() =
        assertTable(chestMarkers) { beltEnd ->
            assertConnectedUndergroundBelts(entitiesFor("advanced-circuit", beltEnd))
        }

    @Test
    fun realChestMarkerBlueprintsPowerEveryInserter() =
        assertTable(chestMarkers) { beltEnd ->
            assertPolesCoverAndConnect(entitiesFor("advanced-circuit", beltEnd))
        }

    @Test
    fun theRealChestOutputWiresItsInsertersToTheChest() {
        val (plan, logistics) = planAndLogistics("advanced-circuit")
        val blueprint = buildBlueprint(plan, logistics, BeltEnd.BUFFER_CHEST)
        val chest =
            blueprint.entities.withIndex().single { (_, entity) ->
                entity.name == "buffer-chest" && entity.circuitConditionEnabled == false
            }
        val chestNumber = chest.index + 1
        val wired = blueprint.wires.flatMap { listOf(it.first, it.second) }.toSet()

        assertAll(
            { assertEquals(2, blueprint.wires.size) },
            { assertEquals(setOf(chestNumber - 2, chestNumber - 1, chestNumber), wired) },
        )
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
        assertTableOf(
            "advanced-circuit",
            "logistic-science-pack",
            "processing-unit",
            "utility-science-pack",
        ) {
            assertPolesWired(entitiesFor(it))
        }

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

        val merges =
            belts
                .filter { (tile, _) ->
                    val feeding = belts.filter { (from, direction) ->
                        from + direction.step == tile
                    }
                    feeding.map { materials[it.key] }.distinct().size > 1
                }
                .keys
        assertEquals(emptySet(), merges, "belts fed by several materials")
    }

    private fun transportMaterialsFor(target: String): Map<Coordinate, Material> {
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

    @Test
    fun realBlueprintsDoNotMergeMaterials() =
        assertTableOf("inserter", "advanced-circuit", "electronic-circuit") { target ->
            assertNoMergingBelts(entitiesFor(target), transportMaterialsFor(target))
        }
}
