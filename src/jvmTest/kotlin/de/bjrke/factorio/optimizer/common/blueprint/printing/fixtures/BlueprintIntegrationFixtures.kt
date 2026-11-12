package de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures

import de.bjrke.factorio.optimizer.common.blueprint.RowLayout
import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LoaderEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.Plumbing
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.TransportEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.planning.blueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlanResult
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.FluidPort
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculatorSelection
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.isInputPort
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.isOutputPort
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.prototypeFluidPorts
import de.bjrke.factorio.optimizer.common.core.model.Coordinate
import de.bjrke.factorio.optimizer.common.core.model.Io
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.core.model.manhattanDist
import de.bjrke.factorio.optimizer.common.game.production.RecipeLoader
import de.bjrke.factorio.optimizer.common.game.prototype.BeltTier
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.FactoryConfig
import de.bjrke.factorio.optimizer.common.game.prototype.LogisticsConfig
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.factorio.optimizer.common.game.selection.forSurface
import de.bjrke.factorio.optimizer.common.game.selection.fullBeltIn
import de.bjrke.factorio.optimizer.common.model.OptimizationResult
import de.bjrke.factorio.optimizer.common.optimize.Optimizer
import de.bjrke.factorio.optimizer.jvm.parseArgs
import java.util.*
import java.util.zip.Inflater
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Plans, logistics and blueprint decoding for the JVM integration suites, keyed by target name. */
val chestMarkers =
    listOf(
        BeltEnd.BUFFER_CHEST,
        BeltEnd.STORAGE_CHEST,
        BeltEnd.WOODEN_CHEST,
        BeltEnd.STEEL_CHEST,
    )

fun optimize(target: String, surface: Surface = Surface.NAUVIS) =
    Optimizer(
        RecipeLoader.forActiveMods().forSurface(surface),
        FactoryConfig().defaults,
    )[Material(target)]

/** Resolves a pasted web-UI URL exactly like the CLI, so a reported layout can be replayed. */
internal fun layoutFromUrl(url: String): Layout {
    val cli = parseArgs(arrayOf(url))
    val parsedRecipes = RecipeLoader.forActiveMods(cli.spaceAge).forSurface(cli.surface)
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
        else logisticsConfig.beltLanes(fullBeltIn(cli.selection))
    val beltStack = logisticsConfig.beltStack(cli.beltStack, inserter)
    val inserterStack = logisticsConfig.inserterStack(cli.inserterStack, inserter)
    val logistics =
        LogisticsCalculator(
            parsedRecipes.logistics,
            LogisticsCalculatorSelection(
                belt,
                inserter,
                lanes,
                beltStack,
                inserterStack,
            ),
        )

    val result = Optimizer(parsedRecipes, factories)[Material(cli.material)]
    return Layout(result, logistics, cli.normalizeTarget, cli.beltEnd)
}

fun testBlueprintPlan(
    result: OptimizationResult,
    logistics: LogisticsCalculator,
    scale: Double = 1.0,
) = assertIs<BlueprintPlanResult.Planned>(blueprintPlan(result, logistics, scale)).plan

fun decode(blueprint: String): String {
    val compressed = Base64.getDecoder().decode(blueprint.substring(1))
    val inflater = Inflater()
    inflater.setInput(compressed)
    val output = ByteArray(1 shl 21)
    val size = inflater.inflate(output)
    inflater.end()
    return output.decodeToString(0, size)
}

data class Layout(
    val plan: BlueprintPlan,
    val logistics: LogisticsCalculator =
        de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.logistics,
    val beltEnd: BeltEnd = BeltEnd.CONSTANT_COMBINATOR,
) {

    constructor(
        result: OptimizationResult,
        logistics: LogisticsCalculator = LogisticsCalculator(result.logistics),
        scale: Double,
        beltEnd: BeltEnd,
    ) : this(testBlueprintPlan(result, logistics, scale), logistics, beltEnd)

    constructor(
        target: String,
        scale: Double = 1.0,
        beltEnd: BeltEnd = BeltEnd.CONSTANT_COMBINATOR,
        surface: Surface = Surface.NAUVIS,
    ) : this(result = optimize(target, surface), scale = scale, beltEnd = beltEnd)

    private val rowLayout by lazy { RowLayout(plan, logistics, beltEnd) }

    internal val dump by lazy { rowLayout.layoutDump() }

    val entities
        get() = dump.entities

    /** The items the layout could not place; empty for a valid layout. */
    val failures
        get() = dump.failures

    val blueprint by lazy { rowLayout.blueprint() }

    val machineEntities by lazy { entities.filterIsInstance<MachineEntity>() }

    fun machine(name: String) = machineEntities.single {
        it.name == name
    }

    fun direction(machine: MachineEntity) =
        blueprint.entities
            .single {
                it.area.topLeft == machine.area.topLeft && it.name == machine.name
            }
            .direction

    /** Every pipe or pipe-to-ground of the layout, mapped to its fluid. */
    val plumbing by lazy {
        entities.filterIsInstance<Plumbing>().associate { it.area.topLeft to it.material }
    }

    /** Every inserter of the layout, by tile. */
    val inserters by lazy {
        entities.filterIsInstance<InserterEntity>().associateBy { it.area.topLeft }
    }

    /** The material each transport entity carries, keyed by tile; tests check nothing merges. */
    val beltMaterials by lazy {
        entities.filterIsInstance<TransportEntity>().associate {
            it.area.topLeft to it.material
        }
    }

    /** Both lanes each belt tile carries, so a mixed merge can be told from an accidental one. */
    val beltLoads by lazy {
        entities.filterIsInstance<BeltEntity>().associate { it.area.topLeft to it.load }
    }

    /** Every tile an underground belt passes under, matched to its own output end. */
    val tunnelSpans by lazy {
        val underground = entities.filterIsInstance<UndergroundBeltEntity>()
        val spans = mutableSetOf<Coordinate>()
        for (input in underground.filter { it.io == Io.INPUT }) {
            val step = input.direction.step
            val output =
                (2..11)
                    .map { reach -> input.area.topLeft + step * reach }
                    .firstOrNull { tile ->
                        underground.any { candidate ->
                            candidate.io == Io.OUTPUT &&
                                candidate.area.topLeft == tile &&
                                candidate.material == input.material &&
                                candidate.direction == input.direction
                        }
                    } ?: continue
            val distance = input.area.topLeft.manhattanDist(output)
            for (offset in 1 until distance) spans += input.area.topLeft + step * offset
        }
        spans
    }

    /**
     * Every pipe or pipe-to-ground of the layout that stands on a machine's *port* tile, mapped to
     * its fluid. A route can run along a lane next to a machine without feeding it, so a pipe only
     * counts when the port table puts a port of that machine there. One entry is one fed (or
     * drained) port; a port the route tunneled through still carries its fluid.
     */
    val portPipes by lazy {
        machineEntities
            .flatMap { (factory, machine) ->
                prototypeFluidPorts(factory).map { machine.topLeft + it.tile }
            }
            .distinct()
            .mapNotNull { tile -> plumbing[tile]?.let { tile to it } }
            .toMap()
    }

    /**
     * The fed pipes on a machine's own input side: every port the edge flow leaves open to inflow.
     */
    val inputPipes by lazy {
        portPipes.filterKeys { tile ->
            portEntryAt(tile)?.let { (factory, port) -> isInputPort(factory, port) } == true
        }
    }

    /** The fed pipes on a machine's own output side: every port the edge flow lets fluid out of. */
    val outputPipes by lazy {
        portPipes.filterKeys { tile ->
            portEntryAt(tile)?.let { (factory, port) -> isOutputPort(factory, port) } == true
        }
    }

    /** The port table entry a pipe at [tile] stands on, with the machine that owns it. */
    private fun portEntryAt(tile: Coordinate): Pair<Factory, FluidPort>? =
        machineEntities.firstNotNullOfOrNull { machine ->
            val factory = machine.factory
            prototypeFluidPorts(factory)
                .firstOrNull { machine.area.topLeft + it.tile == tile }
                ?.let { factory to it }
        }
}

/** A loader is two tiles long, so neither tile may sit above a tunnel span. */
internal fun assertNoLoaderOverTunnel(layout: Layout) {
    val loaders = layout.entities.filterIsInstance<LoaderEntity>()
    val over = loaders.filter { loader -> loader.area.any { it in layout.tunnelSpans } }
    assertEquals(emptyList(), over, "loaders cover a tunnel span")
}
