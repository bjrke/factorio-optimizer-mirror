package de.bjrke.factorio.optimizer.common.model

import de.bjrke.test.assertAll
import de.bjrke.test.assertTable
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FactoryTest {

    private val base = FactoryConfig(spaceAge = false)
    private val spaceAge = FactoryConfig(spaceAge = true)

    private val spaceAgeOnlyFactories =
        listOf(
            Factory.FOUNDRY,
            Factory.CRYOGENIC_PLANT,
            Factory.ELECTROMAGNETIC_PLANT,
            Factory.BIOCHAMBER,
            Factory.CRUSHER,
            Factory.ASTEROID_COLLECTOR,
            Factory.CAPTIVE_BITER_SPAWNER,
            Factory.BIG_MINING_DRILL,
            Factory.AGRICULTURAL_TOWER,
            Factory.ROTTING,
        )

    private val baseAlwaysOnFactories =
        listOf(
            Factory.NO_FACTORY,
            Factory.PUMPJACK,
            Factory.OFFSHORE_PUMP,
            Factory.OIL_REFINERY,
            Factory.CHEMICAL_PLANT,
            Factory.CENTRIFUGE,
            Factory.ROCKET_SILO,
        )

    private val spaceAgeAlwaysOnFactories =
        listOf(
            Factory.CRUSHER,
            Factory.ASTEROID_COLLECTOR,
            Factory.CENTRIFUGE,
            Factory.CAPTIVE_BITER_SPAWNER,
            Factory.AGRICULTURAL_TOWER,
            Factory.ROTTING,
        )

    private val specialtyBonuses =
        mapOf(
            Factory.FOUNDRY to 0.5,
            Factory.ELECTROMAGNETIC_PLANT to 0.5,
            Factory.BIOCHAMBER to 0.5,
            // the cryogenic plant has no bonus but should still be preferred
            Factory.CRYOGENIC_PLANT to 0.0,
        )

    @Test
    fun defaultSelectsElectricMiningDrillOnly() =
        assertTableOf(
            Factory.ELECTRIC_MINING_DRILL to true,
            Factory.BURNER_MINING_DRILL to false,
            Factory.BIG_MINING_DRILL to false,
        ) { (factory, included) ->
            assertEquals(included, factory in base.defaults, "$factory")
        }

    @Test
    fun selectingAMinerDeselectsTheOthers() {
        val selected = base.add(base.defaults, Factory.BURNER_MINING_DRILL)
        assertTableOf(
            Factory.BURNER_MINING_DRILL to true,
            Factory.ELECTRIC_MINING_DRILL to false,
            Factory.BIG_MINING_DRILL to false,
        ) { (factory, included) ->
            assertEquals(included, factory in selected, "$factory")
        }
    }

    @Test
    fun removingAMinerLeavesNoMiner() {
        val removed = base.remove(base.defaults, Factory.ELECTRIC_MINING_DRILL)
        val miners =
            listOf(
                Factory.BURNER_MINING_DRILL,
                Factory.ELECTRIC_MINING_DRILL,
                Factory.BIG_MINING_DRILL,
            )
        assertTrue(removed.none { it in miners })
    }

    @Test
    fun urlRoundTrip() =
        assertAll(
            { assertEquals(base.defaults, base.fromUrl(base.defaults.toUrlParam())) },
            { assertEquals(spaceAge.defaults, spaceAge.fromUrl(spaceAge.defaults.toUrlParam())) },
        )

    @Test
    fun urlRoundTripKeepsAlwaysEnabledFactories() {
        val decoded = base.fromUrl(base.defaults.toUrlParam())
        assertTableOf(Factory.PUMPJACK, Factory.OFFSHORE_PUMP) { factory ->
            assertTrue(factory in decoded, "$factory")
        }
    }

    @Test
    fun rocketSiloIsBaseOnlyAndAlwaysOn() =
        assertAll(
            { assertTrue(Factory.ROCKET_SILO in base.defaults) },
            { assertTrue(Factory.ROCKET_SILO in base.alwaysEnabled) },
            { assertFalse(Factory.ROCKET_SILO in base.singles) },
            { assertEquals(base.defaults, base.remove(base.defaults, Factory.ROCKET_SILO)) },
            { assertFalse(Factory.ROCKET_SILO in spaceAge.available) },
            { assertFalse(Factory.ROCKET_SILO in spaceAge.defaults) },
        )

    @Test
    fun scienceLabIsAlwaysOn() =
        assertAll(
            { assertTrue(Factory.SCIENCE_LAB in base.available) },
            { assertTrue(Factory.SCIENCE_LAB in spaceAge.available) },
            { assertTrue(Factory.SCIENCE_LAB in base.defaults) },
            { assertTrue(Factory.SCIENCE_LAB in spaceAge.defaults) },
            { assertFalse(Factory.SCIENCE_LAB in base.singles) },
            { assertFalse(Factory.SCIENCE_LAB in spaceAge.singles) },
        )

    @Test
    fun spaceAgeMachinesAreHiddenWithoutSpaceAge() =
        assertTable(spaceAgeOnlyFactories) { factory ->
            assertFalse(factory in base.available, "$factory")
        }

    @Test
    fun spaceAgeMachinesAreAvailableWithSpaceAge() =
        assertTable(spaceAgeOnlyFactories) { factory ->
            assertTrue(factory in spaceAge.available, "$factory")
        }

    @Test
    fun recyclerIsSpaceAgeOnly() =
        assertAll(
            { assertFalse(Factory.RECYCLER in base.available) },
            { assertTrue(Factory.RECYCLER in spaceAge.available) },
        )

    @Test
    fun alwaysEnabledMachinesAreSelectedByDefault() =
        assertTable(baseAlwaysOnFactories) { factory ->
            assertTrue(factory in base.defaults, "$factory")
        }

    @Test
    fun alwaysEnabledMachinesAreNotSingles() =
        assertTable(baseAlwaysOnFactories) { factory ->
            assertFalse(factory in base.singles, "$factory")
        }

    @Test
    fun alwaysEnabledMachinesCannotBeRemoved() =
        assertTable(baseAlwaysOnFactories) { factory ->
            assertEquals(base.defaults, base.remove(base.defaults, factory), "$factory")
        }

    @Test
    fun spaceAgeAlwaysEnabledMachinesAreSelectedByDefault() =
        assertTable(spaceAgeAlwaysOnFactories) { factory ->
            assertTrue(factory in spaceAge.defaults, "$factory")
        }

    @Test
    fun spaceAgeAlwaysEnabledMachinesAreNotSingles() =
        assertTable(spaceAgeAlwaysOnFactories) { factory ->
            assertFalse(factory in spaceAge.singles, "$factory")
        }

    @Test
    fun spaceAgeAlwaysEnabledMachinesCannotBeRemoved() =
        assertTable(spaceAgeAlwaysOnFactories) { factory ->
            assertEquals(spaceAge.defaults, spaceAge.remove(spaceAge.defaults, factory), "$factory")
        }

    @Test
    fun groupsHaveExactlyOneSelected() =
        assertTable(listOf(base, spaceAge)) { config ->
            assertTable(config.groups) { group ->
                assertEquals(1, group.count { it in config.defaults }, "group $group")
            }
        }

    @Test
    fun groupMembersAreNotSingles() =
        assertTable(listOf(base, spaceAge)) { config ->
            assertTrue(config.groups.flatten().none { it in config.singles }, "$config")
        }

    @Test
    fun singlesAreNotGroupMembers() =
        assertTable(listOf(base, spaceAge)) { config ->
            assertTrue(config.singles.none { config.groups.flatten().contains(it) }, "$config")
        }

    @Test
    fun specialtyMachinesHaveProductivity() =
        assertTable(specialtyBonuses.entries) { (factory, productivity) ->
            assertEquals(productivity, factory.productivity, "$factory")
        }

    @Test
    fun specialtyMachinesArePreferred() =
        assertTable(specialtyBonuses.keys) { factory ->
            assertTrue(factory.preferred, "$factory")
        }

    @Test
    fun regularMachinesHaveNoProductivityAndAreNotPreferred() =
        assertAll(
            { assertFalse(Factory.ELECTRIC_FURNACE.preferred) },
            { assertFalse(Factory.ASSEMBLING_MACHINE_3.preferred) },
            { assertEquals(0.0, Factory.ELECTRIC_FURNACE.productivity) },
        )

    @Test
    fun specialtyMachinesMatchGameCategories() =
        assertTableOf(
            Factory.FOUNDRY to setOf(Category.METALLURGY),
            Factory.ELECTROMAGNETIC_PLANT to setOf(Category.ELECTROMAGNETICS),
            Factory.CRYOGENIC_PLANT to setOf(Category.CRYOGENICS),
            Factory.BIOCHAMBER to setOf(Category.ORGANIC),
        ) { (factory, categories) ->
            assertEquals(categories, factory.categories, "$factory")
        }

    @Test
    fun specialtyMachinesMatchGameSpeeds() =
        assertTableOf(
            Factory.FOUNDRY to 4.0,
            Factory.ELECTROMAGNETIC_PLANT to 2.0,
            Factory.CRYOGENIC_PLANT to 2.0,
            Factory.BIOCHAMBER to 2.0,
        ) { (factory, speed) ->
            assertEquals(speed, factory.speed, "$factory")
        }
}
