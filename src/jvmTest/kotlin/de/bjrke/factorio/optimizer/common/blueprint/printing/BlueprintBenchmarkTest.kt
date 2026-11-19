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
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.game.production.RecipeLoader
import de.bjrke.factorio.optimizer.common.game.prototype.FactoryConfig
import de.bjrke.factorio.optimizer.common.game.prototype.MAX_INTERMEDIATE_LIMIT
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.factorio.optimizer.common.optimize.Optimizer
import de.bjrke.test.assertAll
import de.bjrke.test.assertTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Sweeps a curated set of materials through the whole pipeline — optimizer, plan, placed layout,
 * blueprint JSON and the encoded string — on every planet and at three normalize targets. It costs
 * minutes, so it is opt-in: run `./gradlew jvmTest -Pbenchmark`.
 *
 * A material with no recipe on a surface is skipped rather than failed; its absence is a game rule,
 * not a layout regression. Every other case must place without failures and survive validation.
 */
class BlueprintBenchmarkTest {

    private data class BenchmarkCase(
        val material: String,
        val surface: Surface,
        val normalize: Double,
        val spaceAge: Boolean = true,
    )

    @Test
    fun everyCuratedMaterialBuildsAValidBlueprint() =
        assertTable(benchmarkCases() - knownFailures) { case ->
            val layout = layoutOf(case) ?: return@assertTable
            val json = layout.blueprint.toJson()

            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertEquals(emptyList(), validateLayout(layout.entities)) },
                { assertNotEquals(emptyList(), layout.machineEntities, "no machine placed") },
                { assertEquals(json, decode(blueprintString(json))) },
            )
        }

    private companion object {

        val normalizeTargets = listOf(0.5, 1.0, 2.0)

        /**
         * Combinations that have no working layout yet. Keeping them as case data instead of
         * removing a material leaves that material covered in every other combination; delete an
         * entry once its layout works again.
         */
        val knownFailures =
            setOf(
                // Base game — Nauvis
                BenchmarkCase("exoskeleton-equipment", Surface.NAUVIS, 0.5, spaceAge = false),
                BenchmarkCase("exoskeleton-equipment", Surface.NAUVIS, 1.0, spaceAge = false),
                BenchmarkCase("exoskeleton-equipment", Surface.NAUVIS, 2.0, spaceAge = false),
                BenchmarkCase("requester-chest", Surface.NAUVIS, 0.5, spaceAge = false),
                BenchmarkCase("rocket-silo", Surface.NAUVIS, 0.5, spaceAge = false),
                BenchmarkCase("rocket-silo", Surface.NAUVIS, 1.0, spaceAge = false),
                BenchmarkCase("rocket-silo", Surface.NAUVIS, 2.0, spaceAge = false),
                // Space Age — Nauvis
                BenchmarkCase("assembling-machine-3", Surface.NAUVIS, 0.5),
                BenchmarkCase("assembling-machine-3", Surface.NAUVIS, 1.0),
                BenchmarkCase("assembling-machine-3", Surface.NAUVIS, 2.0),
                BenchmarkCase("atomic-bomb", Surface.NAUVIS, 0.5),
                BenchmarkCase("atomic-bomb", Surface.NAUVIS, 1.0),
                BenchmarkCase("atomic-bomb", Surface.NAUVIS, 2.0),
                BenchmarkCase("express-splitter", Surface.NAUVIS, 1.0),
                // Space Age — Vulcanus
                BenchmarkCase("big-mining-drill", Surface.VULCANUS, 0.5),
                BenchmarkCase("big-mining-drill", Surface.VULCANUS, 1.0),
                BenchmarkCase("speed-module-3", Surface.VULCANUS, 0.5),
                BenchmarkCase("speed-module-3", Surface.VULCANUS, 1.0),
                BenchmarkCase("speed-module-3", Surface.VULCANUS, 2.0),
                BenchmarkCase("stack-inserter", Surface.VULCANUS, 0.5),
                BenchmarkCase("stack-inserter", Surface.VULCANUS, 1.0),
                BenchmarkCase("stack-inserter", Surface.VULCANUS, 2.0),
                BenchmarkCase("substation", Surface.VULCANUS, 0.5),
                // Space Age — Gleba
                BenchmarkCase("assembling-machine-3", Surface.GLEBA, 0.5),
                BenchmarkCase("atomic-bomb", Surface.GLEBA, 0.5),
                BenchmarkCase("atomic-bomb", Surface.GLEBA, 1.0),
                BenchmarkCase("atomic-bomb", Surface.GLEBA, 2.0),
                BenchmarkCase("biolab", Surface.GLEBA, 0.5),
                BenchmarkCase("biolab", Surface.GLEBA, 1.0),
                BenchmarkCase("biolab", Surface.GLEBA, 2.0),
                // Space Age — Fulgora
                BenchmarkCase("advanced-circuit", Surface.FULGORA, 0.5),
                BenchmarkCase("advanced-circuit", Surface.FULGORA, 1.0),
                BenchmarkCase("advanced-circuit", Surface.FULGORA, 2.0),
                BenchmarkCase("battery", Surface.FULGORA, 0.5),
                BenchmarkCase("battery", Surface.FULGORA, 1.0),
                BenchmarkCase("battery", Surface.FULGORA, 2.0),
                BenchmarkCase("concrete", Surface.FULGORA, 0.5),
                BenchmarkCase("concrete", Surface.FULGORA, 1.0),
                BenchmarkCase("concrete", Surface.FULGORA, 2.0),
                BenchmarkCase("copper-cable", Surface.FULGORA, 0.5),
                BenchmarkCase("copper-cable", Surface.FULGORA, 1.0),
                BenchmarkCase("copper-cable", Surface.FULGORA, 2.0),
                BenchmarkCase("ice", Surface.FULGORA, 0.5),
                BenchmarkCase("ice", Surface.FULGORA, 1.0),
                BenchmarkCase("ice", Surface.FULGORA, 2.0),
                BenchmarkCase("iron-gear-wheel", Surface.FULGORA, 0.5),
                BenchmarkCase("iron-gear-wheel", Surface.FULGORA, 1.0),
                BenchmarkCase("iron-gear-wheel", Surface.FULGORA, 2.0),
                BenchmarkCase("low-density-structure", Surface.FULGORA, 0.5),
                BenchmarkCase("low-density-structure", Surface.FULGORA, 1.0),
                BenchmarkCase("low-density-structure", Surface.FULGORA, 2.0),
                BenchmarkCase("processing-unit", Surface.FULGORA, 0.5),
                BenchmarkCase("processing-unit", Surface.FULGORA, 1.0),
                BenchmarkCase("processing-unit", Surface.FULGORA, 2.0),
                BenchmarkCase("solid-fuel", Surface.FULGORA, 0.5),
                BenchmarkCase("solid-fuel", Surface.FULGORA, 1.0),
                BenchmarkCase("solid-fuel", Surface.FULGORA, 2.0),
                BenchmarkCase("steel-plate", Surface.FULGORA, 0.5),
                BenchmarkCase("steel-plate", Surface.FULGORA, 1.0),
                BenchmarkCase("steel-plate", Surface.FULGORA, 2.0),
                BenchmarkCase("stone", Surface.FULGORA, 0.5),
                BenchmarkCase("stone", Surface.FULGORA, 1.0),
                BenchmarkCase("stone", Surface.FULGORA, 2.0),
                // Space Age — Aquilo
                BenchmarkCase("assembling-machine-3", Surface.AQUILO, 0.5),
                BenchmarkCase("assembling-machine-3", Surface.AQUILO, 1.0),
                BenchmarkCase("assembling-machine-3", Surface.AQUILO, 2.0),
                BenchmarkCase("bulk-inserter", Surface.AQUILO, 0.5),
                BenchmarkCase("express-splitter", Surface.AQUILO, 1.0),
                // Space Age — Space
                BenchmarkCase("assembling-machine-3", Surface.SPACE, 0.5),
                BenchmarkCase("utility-science-pack", Surface.SPACE, 0.5),
            )

        private fun benchmarkCases() = buildList {
            for (material in benchmarkMaterials) {
                for (normalize in normalizeTargets) {
                    add(BenchmarkCase(material, Surface.NAUVIS, normalize, false))
                    for (surface in Surface.entries) {
                        add(BenchmarkCase(material, surface, normalize))
                    }
                }
            }
        }

        /**
         * The plan and placed layout for [case], or `null` when the case has nothing to lay out:
         * the surface has no recipe for the target, or [blueprintPlan] answers
         * [BlueprintPlanResult.Unavailable]. Every `Unavailable` is a skip, not a failure: an
         * imported target is a game rule, and the planner's other `Unavailable` answer (no factory
         * builds the target) is defensive and no case in the table reaches it.
         */
        private fun layoutOf(case: BenchmarkCase): Layout? {
            val recipes = RecipeLoader.forSurface(case.spaceAge, case.surface)
            val target = Material(case.material)

            val factories = FactoryConfig(case.spaceAge).defaults
            val result = Optimizer(recipes, factories, MAX_INTERMEDIATE_LIMIT)[target]
            val logistics = LogisticsCalculator(recipes.logistics)
            val planned =
                blueprintPlan(result, logistics, case.normalize) as? BlueprintPlanResult.Planned
                    ?: return null
            return Layout(
                planned.plan,
                logistics,
                powerPole = PowerPole.forSelection(case.surface, factories),
            )
        }

        /**
         * A broad, curated cross-section of producible materials rather than every dump entry: all
         * science packs, the core intermediates, every building family, modules, ammo, equipment,
         * the planet-specific products and their fluids.
         */
        val benchmarkMaterials =
            listOf(
                // science
                "automation-science-pack",
                "logistic-science-pack",
                "military-science-pack",
                "chemical-science-pack",
                "production-science-pack",
                "utility-science-pack",
                "space-science-pack",
                "agricultural-science-pack",
                "electromagnetic-science-pack",
                "metallurgic-science-pack",
                "cryogenic-science-pack",
                "promethium-science-pack",
                // intermediates
                "iron-plate",
                "copper-plate",
                "steel-plate",
                "iron-gear-wheel",
                "copper-cable",
                "iron-stick",
                "pipe",
                "electronic-circuit",
                "advanced-circuit",
                "processing-unit",
                "plastic-bar",
                "sulfur",
                "sulfuric-acid",
                "battery",
                "low-density-structure",
                "electric-engine-unit",
                "engine-unit",
                "flying-robot-frame",
                "rocket-fuel",
                "solid-fuel",
                "explosives",
                "uranium-235",
                "uranium-238",
                "uranium-fuel-cell",
                // resources and bulk solids
                "iron-ore",
                "copper-ore",
                "coal",
                "stone",
                "calcite",
                "stone-brick",
                "concrete",
                "landfill",
                // buildings
                "assembling-machine-1",
                "assembling-machine-3",
                "stone-furnace",
                "electric-furnace",
                "burner-mining-drill",
                "electric-mining-drill",
                "big-mining-drill",
                "transport-belt",
                "fast-transport-belt",
                "express-transport-belt",
                "underground-belt",
                "express-underground-belt",
                "splitter",
                "express-splitter",
                "inserter",
                "fast-inserter",
                "bulk-inserter",
                "stack-inserter",
                "medium-electric-pole",
                "substation",
                "requester-chest",
                "roboport",
                "logistic-robot",
                "construction-robot",
                "beacon",
                "lab",
                "oil-refinery",
                "chemical-plant",
                "pumpjack",
                "offshore-pump",
                "centrifuge",
                // power and rocketry
                "boiler",
                "steam-engine",
                "steam-turbine",
                "solar-panel",
                "heat-exchanger",
                "heat-pipe",
                "nuclear-reactor",
                "rocket-silo",
                "rocket-part",
                "satellite",
                "small-lamp",
                // modules
                "efficiency-module",
                "efficiency-module-3",
                "speed-module",
                "speed-module-3",
                "productivity-module",
                "productivity-module-3",
                "quality-module",
                "quality-module-3",
                // ammo and weapons
                "firearm-magazine",
                "piercing-rounds-magazine",
                "uranium-rounds-magazine",
                "cannon-shell",
                "explosive-cannon-shell",
                "shotgun-shell",
                "rocket",
                "explosive-rocket",
                "artillery-shell",
                "flamethrower-ammo",
                "grenade",
                "land-mine",
                "atomic-bomb",
                // equipment
                "modular-armor",
                "power-armor-mk2",
                "mech-armor",
                "exoskeleton-equipment",
                // common fluids
                "crude-oil",
                "heavy-oil",
                "light-oil",
                "petroleum-gas",
                "lubricant",
                "water",
                "steam",
                // Vulcanus
                "tungsten-plate",
                "tungsten-carbide",
                "molten-iron",
                "molten-copper",
                "lava",
                "foundry",
                "turbo-transport-belt",
                // Fulgora
                "holmium-plate",
                "superconductor",
                "supercapacitor",
                "holmium-solution",
                "recycler",
                "lightning-collector",
                // Gleba
                "bioflux",
                "yumako-mash",
                "jelly",
                "nutrients",
                "spoilage",
                "carbon-fiber",
                "biochamber",
                "agricultural-tower",
                "biolab",
                // Aquilo
                "lithium",
                "lithium-plate",
                "lithium-brine",
                "ice",
                "cryogenic-plant",
                "fusion-power-cell",
                "fusion-reactor",
                "fusion-generator",
                "quantum-processor",
                "fluoroketone-cold",
                "fluoroketone-hot",
                "ammonia",
                // space
                "carbonic-asteroid-chunk",
                "metallic-asteroid-chunk",
                "oxide-asteroid-chunk",
                "promethium-asteroid-chunk",
                "railgun",
                "railgun-ammo",
                "tesla-ammo",
                "cargo-bay",
            )
    }
}
