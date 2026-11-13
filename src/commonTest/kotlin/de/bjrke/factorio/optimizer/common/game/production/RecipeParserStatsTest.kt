package de.bjrke.factorio.optimizer.common.game.production

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.coal
import de.bjrke.factorio.optimizer.common.fixtures.crudeOil
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.factorio.optimizer.common.game.prototype.BeltStats
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.InserterStats
import de.bjrke.factorio.optimizer.common.game.prototype.MachineStats
import de.bjrke.factorio.optimizer.common.game.prototype.PoleStats
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.factorio.optimizer.common.game.prototype.SurfaceProperty
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

/** The machine and logistics stats the parser reads from the prototype dump. */
class RecipeParserStatsTest {

    @Test
    fun parseMachineStats() {
        val stats =
            parsePrototypes(
                    """
                    {
                      "assembling-machine": {
                        "assembling-machine-3": {"crafting_categories": ["crafting", "advanced-crafting", "crafting-with-fluid"], "crafting_speed": 1.25},
                        "foundry": {"crafting_categories": ["metallurgy"], "crafting_speed": 4, "effect_receiver": {"base_effect": {"productivity": 0.5}}}
                      },
                      "mining-drill": {
                        "big-mining-drill": {"resource_categories": ["basic-solid", "hard-solid"], "mining_speed": 2.5}
                      }
                    }
                    """,
                )
                .machineStats

        assertTableOf(
            "assembling-machine-3" to
                MachineStats(
                    setOf(
                        Category.CRAFTING,
                        Category.ADVANCED_CRAFTING,
                        Category.CRAFTING_WITH_FLUID,
                    ),
                    1.25,
                    0.0,
                ),
            "foundry" to MachineStats(setOf(Category.METALLURGY), 4.0, 0.5),
            "big-mining-drill" to
                MachineStats(
                    setOf(Category.MINING_BASIC_SOLID, Category.MINING_HARD_SOLID),
                    2.5,
                    0.0,
                ),
        ) { (name, expected) ->
            assertEquals(expected, stats.getValue(name))
        }
    }

    @Test
    fun parseMachineEnergyUsage() {
        val stats =
            parsePrototypes(
                    """
                    {
                      "furnace": {
                        "stone-furnace": {"crafting_categories": ["smelting"], "crafting_speed": 1, "energy_usage": "90kW"},
                        "electric-furnace": {"crafting_categories": ["smelting"], "crafting_speed": 2, "energy_usage": "180kW"}
                      }
                    }
                    """,
                )
                .machineStats

        assertTableOf("stone-furnace" to 90_000.0, "electric-furnace" to 180_000.0) {
            (name, expected) ->
            assertEquals(expected, stats.getValue(name).energyUsage)
        }
    }

    /** `fuel_value` is an energy and `energy_usage` a power, so one SI parser reads both. */
    @Test
    fun parseFuelValues() {
        val values =
            parsePrototypes(
                    """
                    {
                      "item": {
                        "coal": {"fuel_value": "4MJ"},
                        "uranium-fuel-cell": {"fuel_value": "8GJ"},
                        "iron-plate": {}
                      }
                    }
                    """,
                )
                .fuelValues

        assertAll(
            { assertEquals(4_000_000.0, values.getValue(coal)) },
            { assertEquals(8_000_000_000.0, values.getValue(Material("uranium-fuel-cell"))) },
            { assertContainsNot(values, ironPlate, "an item without a fuel value is no fuel") },
        )
    }

    @Test
    fun parseSurfaceProperties() {
        val properties =
            parsePrototypes(
                    """
                    {
                      "surface-property": {
                        "pressure": {"default_value": 1000},
                        "gravity": {"default_value": 10},
                        "magnetic-field": {"default_value": 90}
                      },
                      "planet": {
                        "nauvis": {"surface_properties": {"magnetic-field": 80}},
                        "fulgora": {"surface_properties": {"pressure": 800, "gravity": 8, "magnetic-field": 99}}
                      },
                      "surface": {
                        "space-platform": {"surface_properties": {"pressure": 0, "gravity": 0, "magnetic-field": 0}}
                      }
                    }
                    """,
                )
                .surfaceProperties

        assertAll(
            {
                // a planet only overrides what its prototype sets, the rest are the defaults
                assertEquals(
                    mapOf(
                        SurfaceProperty.PRESSURE to 1000,
                        SurfaceProperty.GRAVITY to 10,
                        SurfaceProperty.MAGNETIC_FIELD to 80,
                    ),
                    properties.getValue(Surface.NAUVIS),
                )
            },
            {
                assertEquals(
                    mapOf(
                        SurfaceProperty.PRESSURE to 800,
                        SurfaceProperty.GRAVITY to 8,
                        SurfaceProperty.MAGNETIC_FIELD to 99,
                    ),
                    properties.getValue(Surface.FULGORA),
                )
            },
            {
                // the space platform is a `surface` prototype, not a planet
                assertEquals(
                    0,
                    properties.getValue(Surface.SPACE).getValue(SurfaceProperty.GRAVITY),
                )
            },
            {
                // a planet the dump does not carry gets the `surface-property` defaults
                assertEquals(
                    mapOf(
                        SurfaceProperty.PRESSURE to 1000,
                        SurfaceProperty.GRAVITY to 10,
                        SurfaceProperty.MAGNETIC_FIELD to 90,
                    ),
                    properties.getValue(Surface.VULCANUS),
                )
            },
        )
    }

    @Test
    fun surfacePropertiesFallBackWithoutTheCategories() {
        assertEquals(
            Surface.entries.associateWith { it.properties },
            parsePrototypes("{}").surfaceProperties,
        )
    }

    @Test
    fun parseBeltAndPoleStats() {
        val logistics =
            parsePrototypes(
                    """
                    {
                      "transport-belt": {
                        "transport-belt": {"speed": 0.03125, "related_underground_belt": "underground-belt"},
                        "fast-transport-belt": {"speed": 0.0625, "related_underground_belt": "fast-underground-belt"},
                        "turbo-transport-belt": {"speed": 0.125, "related_underground_belt": "turbo-underground-belt"}
                      },
                      "underground-belt": {
                        "underground-belt": {"max_distance": 5},
                        "fast-underground-belt": {"max_distance": 7},
                        "turbo-underground-belt": {"max_distance": 11}
                      },
                      "electric-pole": {
                        "small-electric-pole": {"supply_area_distance": 2.5, "maximum_wire_distance": 7.5},
                        "medium-electric-pole": {"supply_area_distance": 3.5, "maximum_wire_distance": 9}
                      }
                    }
                    """,
                )
                .logistics

        assertAll(
            {
                assertEquals(
                    BeltStats(speed = 0.03125, undergroundRange = 5),
                    logistics.belts["transport-belt"],
                )
            },
            {
                assertEquals(
                    BeltStats(speed = 0.0625, undergroundRange = 7),
                    logistics.belts["fast-transport-belt"],
                )
            },
            {
                assertEquals(
                    BeltStats(speed = 0.125, undergroundRange = 11),
                    logistics.belts["turbo-transport-belt"],
                )
            },
            {
                // supply_area_distance is the half-width of the square, so 2.5 covers two tiles
                assertEquals(
                    PoleStats(supplyRadius = 2, wireReach = 7.5),
                    logistics.poles["small-electric-pole"],
                )
            },
            {
                assertEquals(
                    PoleStats(supplyRadius = 3, wireReach = 9.0),
                    logistics.poles["medium-electric-pole"],
                )
            },
        )
    }

    @Test
    fun parseInserterAndPumpStats() {
        val logistics =
            parsePrototypes(
                    """
                    {
                      "inserter": {
                        "inserter": {"rotation_speed": 0.014, "extension_speed": 0.035},
                        "stack-inserter": {"rotation_speed": 0.04, "bulk": true, "max_belt_stack_size": 4, "stack_size_bonus": 4}
                      },
                      "technology": {
                        "inserter-capacity-bonus-1": {"effects": [{"type": "bulk-inserter-capacity-bonus", "modifier": 1}]},
                        "inserter-capacity-bonus-2": {"effects": [{"type": "inserter-stack-size-bonus", "modifier": 1}, {"type": "bulk-inserter-capacity-bonus", "modifier": 1}]},
                        "transport-belt-capacity-2": {"effects": [{"type": "belt-stack-size-bonus", "modifier": 1}, {"type": "inserter-stack-size-bonus", "modifier": 1}]}
                      },
                      "pump": {"pump": {"pumping_speed": 20}},
                      "offshore-pump": {"offshore-pump": {"pumping_speed": 20}},
                      "fluid": {"water": {}, "crude-oil": {}}
                    }
                    """,
                )
                .logistics

        assertAll(
            {
                // a plain inserter starts at one item and gains the generic stack size bonus
                assertEquals(
                    InserterStats(0.014, minStack = 1, maxStack = 2, stacksOnBelt = false),
                    logistics.inserters["inserter"],
                )
            },
            {
                // the stack inserter starts at 2 + stack_size_bonus and gains the bulk capacity
                // bonus; only it carries max_belt_stack_size
                assertEquals(
                    InserterStats(0.04, minStack = 6, maxStack = 8, stacksOnBelt = true),
                    logistics.inserters["stack-inserter"],
                )
            },
            // pumping_speed is fluid per tick, so the rate is 60 times as much
            { assertEquals(1200.0, logistics.pumpRate) },
            { assertEquals(setOf(water, crudeOil), logistics.fluids) },
        )
    }
}
