package de.bjrke.factorio.optimizer.common.blueprint.fixtures

import de.bjrke.factorio.optimizer.common.blueprint.fromPlaced
import de.bjrke.factorio.optimizer.common.blueprint.model.BlueprintEntity
import de.bjrke.factorio.optimizer.common.blueprint.planning.blueprintPlan
import de.bjrke.factorio.optimizer.common.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlanResult
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculatorSelection
import de.bjrke.factorio.optimizer.common.production.RecipeLoader
import de.bjrke.factorio.optimizer.common.prototype.BeltTier
import de.bjrke.factorio.optimizer.common.prototype.FactoryConfig
import de.bjrke.factorio.optimizer.common.prototype.LogisticsConfig
import de.bjrke.factorio.optimizer.common.prototype.Surface
import de.bjrke.factorio.optimizer.common.prototype.selection.forSurface
import de.bjrke.factorio.optimizer.common.prototype.selection.fullBeltIn
import de.bjrke.factorio.optimizer.common.service.OptimizationResult
import de.bjrke.factorio.optimizer.common.service.Optimizer
import de.bjrke.factorio.optimizer.jvm.parseArgs
import java.util.*
import java.util.zip.Inflater
import kotlin.test.assertIs

/** Plans, logistics and blueprint decoding for the JVM integration suites, keyed by target name. */
val chestMarkers =
    listOf(
        BeltEnd.BUFFER_CHEST,
        BeltEnd.STORAGE_CHEST,
        BeltEnd.WOODEN_CHEST,
        BeltEnd.STEEL_CHEST,
    )

fun optimize(target: String) =
    Optimizer(
        RecipeLoader.forActiveMods().forSurface(Surface.NAUVIS),
        FactoryConfig().defaults,
    )[Material(target)]

fun planAndLogistics(target: String): Pair<BlueprintPlan, LogisticsCalculator> {
    val result = optimize(target)
    val logistics = LogisticsCalculator(result.logistics)
    return testBlueprintPlan(result, logistics) to logistics
}

fun planFor(target: String): BlueprintPlan = planAndLogistics(target).first

/** Resolves a pasted web-UI URL exactly like the CLI, so a reported layout can be replayed. */
fun planFromUrl(url: String): Triple<BlueprintPlan, LogisticsCalculator, BeltEnd> {
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
    val scale = cli.normalizeTarget
    return Triple(testBlueprintPlan(result, logistics, scale), logistics, cli.beltEnd)
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

fun entitiesFor(
    target: String,
    beltEnd: BeltEnd = BeltEnd.CONSTANT_COMBINATOR,
): List<BlueprintEntity> {
    val result = optimize(target)
    val logistics = LogisticsCalculator(result.logistics)
    return fromPlaced(testBlueprintPlan(result, logistics), logistics, beltEnd).entities
}

fun feedFailuresFor(target: String): List<String> {
    val result = optimize(target)
    val logistics = LogisticsCalculator(result.logistics)
    return blueprintFeedFailures(testBlueprintPlan(result, logistics), logistics)
}

fun transportMaterialsFor(target: String): Map<Coordinate, Material> {
    val result = optimize(target)
    val logistics = LogisticsCalculator(result.logistics)
    return blueprintTransportMaterials(testBlueprintPlan(result, logistics), logistics)
}
