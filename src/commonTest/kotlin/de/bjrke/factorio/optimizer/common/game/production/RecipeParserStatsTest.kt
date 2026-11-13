package de.bjrke.factorio.optimizer.common.game.production

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.coal
import de.bjrke.factorio.optimizer.common.fixtures.crudeOil
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.InserterStats
import de.bjrke.factorio.optimizer.common.game.prototype.MachineStats
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
    fun parseLogisticsStats() {
        val logistics =
            parsePrototypes(
                    """
                    {
                      "transport-belt": {
                        "transport-belt": {"speed": 0.03125},
                        "turbo-transport-belt": {"speed": 0.125}
                      },
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
            { assertEquals(0.03125, logistics.belts["transport-belt"]) },
            { assertEquals(0.125, logistics.belts["turbo-transport-belt"]) },
            {
                // a plain inserter starts at one item and gains the generic stack size bonus
                assertEquals(
                    InserterStats(
                        0.014,
                        minStack = 1,
                        maxStack = 2,
                        stacksOnBelt = false,
                    ),
                    logistics.inserters["inserter"],
                )
            },
            {
                // the stack inserter starts at 2 + stack_size_bonus and gains the bulk capacity
                // bonus; only it carries max_belt_stack_size
                assertEquals(
                    InserterStats(
                        0.04,
                        minStack = 6,
                        maxStack = 8,
                        stacksOnBelt = true,
                    ),
                    logistics.inserters["stack-inserter"],
                )
            },
            // pumping_speed is fluid per tick, so the rate is 60 times as much
            { assertEquals(1200.0, logistics.pumpRate) },
            {
                assertEquals(
                    setOf(water, crudeOil),
                    logistics.fluids,
                )
            },
        )
    }
}
