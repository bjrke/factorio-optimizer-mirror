package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.PowerPole
import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.planning.blueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlanResult
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.decode
import de.bjrke.factorio.optimizer.common.blueprint.printing.json.toJson
import de.bjrke.factorio.optimizer.common.blueprint.printing.output.blueprintString
import de.bjrke.factorio.optimizer.common.game.production.RecipeLoader
import de.bjrke.factorio.optimizer.common.game.prototype.FactoryConfig
import de.bjrke.factorio.optimizer.common.game.prototype.MAX_INTERMEDIATE_LIMIT
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.factorio.optimizer.common.optimize.Optimizer
import de.bjrke.test.assertAll
import de.bjrke.test.assertTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.assertThrows

/**
 * Sweeps a curated set of materials per surface through the whole pipeline — optimizer, plan,
 * placed layout, blueprint JSON and the encoded string — at three normalize targets. It costs
 * minutes, so it is opt-in: run `./gradlew jvmTest -Pbenchmark`, or narrow it to one surface with
 * `--tests "*BlueprintBenchmarkTest.vulcanusBlueprints"`.
 *
 * The lists are a curated cross-section, not every producible material: a material no recipe
 * produces on the surface is an import rather than a layout, and many working chains were left out
 * to keep the run short. A listed case must plan more than one recipe and place without failures,
 * so when the dump or the planner turns one into a lone row the sweep fails instead of silently
 * skipping it.
 *
 * `knownFailures` holds the combinations that do not pass today: a layout that still reports
 * failures, and — on a surface whose chain the planner does not build yet, such as Fulgora's
 * recycler rows — a listed target whose plan is a single row. Keeping them as cases leaves the
 * material covered in every other combination; delete an entry once it passes. The passing sweep
 * ([assertSweep]) runs everything else, and [assertKnownFailures] requires each listed combination
 * to still fail, so the pair covers every case.
 *
 * The lists were derived from the prototype dump with the surface filter, the `unplannedRecipes`
 * policy and the planner's `buildable` rule.
 */
class BlueprintBenchmarkTest {

    @Test
    fun baseGameBlueprints() =
        assertSweep(
            spaceAge = false,
            surface = Surface.NAUVIS,
            materials = baseGameMaterials,
        )

    @Test
    fun nauvisBlueprints() =
        assertSweep(
            spaceAge = true,
            surface = Surface.NAUVIS,
            materials = nauvisMaterials,
        )

    @Test
    fun vulcanusBlueprints() =
        assertSweep(
            spaceAge = true,
            surface = Surface.VULCANUS,
            materials = vulcanusMaterials,
        )

    @Test
    fun glebaBlueprints() =
        assertSweep(
            spaceAge = true,
            surface = Surface.GLEBA,
            materials = glebaMaterials,
        )

    @Test
    fun fulgoraBlueprints() =
        assertSweep(
            spaceAge = true,
            surface = Surface.FULGORA,
            materials = fulgoraMaterials,
            knownFailures = fulgoraKnownFailures,
        )

    @Test
    fun aquiloBlueprints() =
        assertSweep(
            spaceAge = true,
            surface = Surface.AQUILO,
            materials = aquiloMaterials,
        )

    @Test
    fun spaceBlueprints() =
        assertSweep(
            spaceAge = true,
            surface = Surface.SPACE,
            materials = spaceMaterials,
        )

    @Test
    fun fulgoraBlueprintFailures() =
        assertKnownFailures(
            spaceAge = true,
            surface = Surface.FULGORA,
            knownFailures = fulgoraKnownFailures,
        )
}

/** One material at one normalize target; the surface and game mode come from the sweep. */
private data class SweepCase(val material: String, val normalize: Double)

private val normalizeTargets = listOf(0.5, 1.0, 2.0)

/** One material at each of [normalize]. */
private fun failures(material: String, vararg normalize: Double) =
    (if (normalize.isNotEmpty()) normalize.toList() else normalizeTargets).map {
        SweepCase(material, it)
    }

/** Targets every sweep builds. */
private val commonMaterials =
    listOf(
        "advanced-circuit",
        "assembling-machine-1",
        "assembling-machine-3",
        "automation-science-pack",
        "battery",
        "beacon",
        "bulk-inserter",
        "chemical-plant",
        "chemical-science-pack",
        "efficiency-module-2",
        "electric-furnace",
        "electronic-circuit",
        "explosive-rocket",
        "explosives",
        "fast-inserter",
        "fast-splitter",
        "fast-transport-belt",
        "fast-underground-belt",
        "heat-exchanger",
        "heat-pipe",
        "lab",
        "logistic-science-pack",
        "low-density-structure",
        "military-science-pack",
        "offshore-pump",
        "processing-unit",
        "production-science-pack",
        "productivity-module-2",
        "requester-chest",
        "roboport",
        "rocket",
        "rocket-silo",
        "speed-module-2",
        "steam-turbine",
        "utility-science-pack",
    )

private val baseGameMaterials =
    commonMaterials +
        listOf(
            "artillery-shell",
            "atomic-bomb",
            "boiler",
            "burner-mining-drill",
            "cannon-shell",
            "centrifuge",
            "concrete",
            "construction-robot",
            "efficiency-module-3",
            "electric-mining-drill",
            "explosive-cannon-shell",
            "express-splitter",
            "express-transport-belt",
            "express-underground-belt",
            "firearm-magazine",
            "flamethrower-ammo",
            "grenade",
            "land-mine",
            "logistic-robot",
            "medium-electric-pole",
            "nuclear-reactor",
            "oil-refinery",
            "piercing-rounds-magazine",
            "pipe",
            "productivity-module-3",
            "pumpjack",
            "rocket-fuel",
            "rocket-part",
            "satellite",
            "shotgun-shell",
            "solar-panel",
            "space-science-pack",
            "speed-module-3",
            "steam-engine",
            "substation",
            "sulfuric-acid",
            "uranium-fuel-cell",
            "uranium-rounds-magazine",
        )

private val nauvisMaterials =
    commonMaterials +
        listOf(
            "agricultural-tower",
            "atomic-bomb",
            "biolab",
            "boiler",
            "burner-mining-drill",
            "cannon-shell",
            "centrifuge",
            "concrete",
            "construction-robot",
            "efficiency-module-3",
            "electric-mining-drill",
            "explosive-cannon-shell",
            "firearm-magazine",
            "flamethrower-ammo",
            "grenade",
            "land-mine",
            "logistic-robot",
            "medium-electric-pole",
            "nuclear-reactor",
            "nutrients",
            "oil-refinery",
            "piercing-rounds-magazine",
            "pipe",
            "productivity-module-3",
            "pumpjack",
            "quality-module-2",
            "railgun-ammo",
            "rocket-fuel",
            "rocket-part",
            "shotgun-shell",
            "solar-panel",
            "steam-engine",
            "substation",
            "sulfuric-acid",
            "tesla-ammo",
            "uranium-fuel-cell",
            "uranium-rounds-magazine",
        )

private val vulcanusMaterials =
    commonMaterials +
        listOf(
            "artillery-shell",
            "big-mining-drill",
            "cannon-shell",
            "concrete",
            "construction-robot",
            "electric-mining-drill",
            "explosive-cannon-shell",
            "firearm-magazine",
            "flamethrower-ammo",
            "foundry",
            "grenade",
            "land-mine",
            "logistic-robot",
            "medium-electric-pole",
            "metallurgic-science-pack",
            "oil-refinery",
            "piercing-rounds-magazine",
            "pumpjack",
            "quality-module-2",
            "railgun",
            "railgun-ammo",
            "rocket-fuel",
            "rocket-part",
            "shotgun-shell",
            "solar-panel",
            "speed-module-3",
            "substation",
            "tungsten-carbide",
            "turbo-splitter",
            "turbo-transport-belt",
            "turbo-underground-belt",
        )

private val glebaMaterials =
    commonMaterials +
        listOf(
            "agricultural-science-pack",
            "agricultural-tower",
            "biochamber",
            "cannon-shell",
            "capture-robot-rocket",
            "carbon-fiber",
            "concrete",
            "construction-robot",
            "efficiency-module-3",
            "electric-mining-drill",
            "explosive-cannon-shell",
            "firearm-magazine",
            "flamethrower-ammo",
            "grenade",
            "land-mine",
            "logistic-robot",
            "medium-electric-pole",
            "nutrients",
            "oil-refinery",
            "piercing-rounds-magazine",
            "pipe",
            "quality-module-2",
            "railgun-ammo",
            "rocket-fuel",
            "rocket-part",
            "shotgun-shell",
            "solar-panel",
            "stack-inserter",
            "substation",
            "sulfuric-acid",
        )

private val fulgoraMaterials =
    commonMaterials +
        listOf(
            "concrete",
            "construction-robot",
            "electromagnetic-science-pack",
            "lightning-collector",
            "logistic-robot",
            "medium-electric-pole",
            "quality-module-2",
            "quality-module-3",
            "railgun",
            "rocket-part",
            "stone",
            "sulfuric-acid",
            "supercapacitor",
            "superconductor",
            "tesla-ammo",
        )

private val aquiloMaterials =
    commonMaterials +
        listOf(
            "construction-robot",
            "cryogenic-plant",
            "cryogenic-science-pack",
            "fluoroketone-hot",
            "fusion-generator",
            "fusion-power-cell",
            "fusion-reactor",
            "lithium-plate",
            "logistic-robot",
            "medium-electric-pole",
            "oil-refinery",
            "pipe",
            "pumpjack",
            "quality-module-2",
            "quantum-processor",
            "railgun",
            "railgun-ammo",
            "rocket-fuel",
            "rocket-part",
            "substation",
            "sulfuric-acid",
            "water",
        )

private val spaceMaterials =
    commonMaterials +
        listOf(
            "firearm-magazine",
            "piercing-rounds-magazine",
            "pipe",
            "promethium-science-pack",
            "quality-module-2",
            "quantum-processor",
            "railgun",
            "railgun-ammo",
            "solar-panel",
            "space-science-pack",
            "sulfuric-acid",
        )

// A listed case that stops building is a regression, not a skip, so the blacklist only holds
// combinations that fail today; delete an entry once it passes. Fulgora's chains run through the
// recycler, which the planner does not build, so these targets plan a single row.
private val fulgoraKnownFailures =
    listOf(
            failures("advanced-circuit"),
            failures("assembling-machine-1"),
            failures("automation-science-pack"),
            failures("battery"),
            failures("beacon"),
            failures("concrete"),
            failures("electric-furnace"),
            failures("electronic-circuit"),
            failures("heat-pipe"),
            failures("low-density-structure"),
            failures("processing-unit"),
            failures("roboport"),
            failures("stone"),
        )
        .flatten()
        .toSet()

/**
 * Runs every case of one surface, minus its [knownFailures]. The blacklist must stay a subset of
 * the swept cases: an entry for a material the list no longer holds is stale, and a silently
 * ignored blacklist would hide a case that is not covered anywhere. The excluded cases are re-run
 * by [assertKnownFailures].
 */
private fun assertSweep(
    spaceAge: Boolean,
    surface: Surface,
    materials: List<String>,
    knownFailures: Set<SweepCase> = emptySet(),
) {
    val cases = materials.flatMap { material ->
        normalizeTargets.map { SweepCase(material, it) }
    }
    assertEquals(
        emptySet(),
        knownFailures - cases.toSet(),
        "blacklist entry outside the sweep",
    )
    assertTable(cases - knownFailures) { case -> assertCase(spaceAge, surface, case) }
}

/**
 * Runs [knownFailures] and requires every case to still fail. A case that starts passing has been
 * fixed, so delete its entry; when the last entry goes, delete this test method too, which is why
 * the empty set fails instead of passing silently.
 *
 * Only [assertSweep] checks that the blacklist stays inside the material list; this helper does not
 * receive the list, so running it alone cannot detect a stale entry.
 */
private fun assertKnownFailures(
    spaceAge: Boolean,
    surface: Surface,
    knownFailures: Set<SweepCase>,
) {
    assertNotEquals(emptySet(), knownFailures, "no failures to test; delete this test")
    assertTable(knownFailures) { case ->
        assertThrows<AssertionError> { assertCase(spaceAge, surface, case) }
    }
}

private fun assertCase(spaceAge: Boolean, surface: Surface, case: SweepCase) {
    val layout = layoutOf(spaceAge, surface, case)
    val json = layout.blueprint.toJson()
    val recipes = layout.plan.rows.flatMap { row -> row.machines }.map { it.recipe }.distinct()

    assertAll(
        { assertEquals(emptyList(), layout.failures) },
        { assertEquals(emptyList(), validateLayout(layout.entities)) },
        { assertNotEquals(emptyList(), layout.machineEntities, "no machine placed") },
        { assertTrue(recipes.size > 1, "a single-recipe plan on $surface benchmarks nothing") },
        { assertEquals(json, decode(blueprintString(json))) },
    )
}

/**
 * The plan and placed layout for [case]. A target no recipe produces on the surface is an import
 * rather than a layout, so it is excluded from the lists; if one still reaches this point the list
 * is stale and the sweep fails with the plan's reason.
 */
private fun layoutOf(spaceAge: Boolean, surface: Surface, case: SweepCase): Layout {
    val recipes = RecipeLoader.forSurface(spaceAge, surface)
    val factories = FactoryConfig(spaceAge).defaults
    val result =
        Optimizer(recipes, factories, MAX_INTERMEDIATE_LIMIT)[
            recipes.gameCtx.materialRegistry.Material(case.material)]
    val logistics = LogisticsCalculator(recipes.logistics)
    val planned = blueprintPlan(result, logistics, case.normalize)
    val plan = assertIs<BlueprintPlanResult.Planned>(planned, planned.toString()).plan
    return Layout(plan, logistics, powerPole = PowerPole.forSelection(surface, factories))
}
