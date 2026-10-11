package de.bjrke.factorio.optimizer.common.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FactoryTest {

    private val base = FactoryConfig(spaceAge = false, recycler = false)
    private val spaceAge = FactoryConfig(spaceAge = true, recycler = true)

    @Test
    fun defaultSelectsElectricMiningDrillOnly() {
        assertTrue(Factory.ELECTRIC_MINING_DRILL in base.defaults)
        assertFalse(Factory.BURNER_MINING_DRILL in base.defaults)
        assertFalse(Factory.BIG_MINING_DRILL in base.defaults)
    }

    @Test
    fun selectingAMinerDeselectsTheOthers() {
        val selected = base.add(base.defaults, Factory.BURNER_MINING_DRILL)
        assertTrue(Factory.BURNER_MINING_DRILL in selected)
        assertFalse(Factory.ELECTRIC_MINING_DRILL in selected)
        assertFalse(Factory.BIG_MINING_DRILL in selected)
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
    fun urlRoundTrip() {
        assertEquals(base.defaults, base.fromUrl(base.defaults.toUrlParam()))
        assertEquals(spaceAge.defaults, spaceAge.fromUrl(spaceAge.defaults.toUrlParam()))
    }

    @Test
    fun urlRoundTripKeepsAlwaysEnabledFactories() {
        val decoded = base.fromUrl(base.defaults.toUrlParam())
        assertTrue(Factory.PUMPJACK in decoded)
        assertTrue(Factory.OFFSHORE_PUMP in decoded)
    }

    @Test
    fun rocketSiloIsBaseOnlyAndAlwaysOn() {
        assertTrue(Factory.ROCKET_SILO in base.defaults)
        assertTrue(Factory.ROCKET_SILO in base.alwaysEnabled)
        assertFalse(Factory.ROCKET_SILO in base.singles)
        assertEquals(base.defaults, base.remove(base.defaults, Factory.ROCKET_SILO))
        assertFalse(Factory.ROCKET_SILO in spaceAge.available)
        assertFalse(Factory.ROCKET_SILO in spaceAge.defaults)
    }

    @Test
    fun scienceLabIsAlwaysOn() {
        assertTrue(Factory.SCIENCE_LAB in base.available)
        assertTrue(Factory.SCIENCE_LAB in spaceAge.available)
        assertTrue(Factory.SCIENCE_LAB in base.defaults)
        assertTrue(Factory.SCIENCE_LAB in spaceAge.defaults)
        assertFalse(Factory.SCIENCE_LAB in base.singles)
        assertFalse(Factory.SCIENCE_LAB in spaceAge.singles)
    }

    @Test
    fun spaceAgeMachinesAreGatedByMod() {
        val spaceAgeOnly =
            listOf(
                Factory.FOUNDRY,
                Factory.CRYOGENIC_PLANT,
                Factory.ELECTROMAGNETIC_PLANT,
                Factory.BIOCHAMBER,
                Factory.CRUSHER,
                Factory.CAPTIVE_BITER_SPAWNER,
                Factory.BIG_MINING_DRILL,
                Factory.AGRICULTURAL_TOWER,
                Factory.ROTTING,
            )
        for (factory in spaceAgeOnly) {
            assertFalse(factory in base.available, "$factory should be hidden without Space Age")
            assertTrue(factory in spaceAge.available, "$factory should be available with Space Age")
        }
    }

    @Test
    fun recyclerRequiresRecyclerMod() {
        assertFalse(Factory.RECYCLER in base.available)
        assertTrue(Factory.RECYCLER in FactoryConfig(spaceAge = true, recycler = true).available)
        assertTrue(Factory.RECYCLER in FactoryConfig(spaceAge = false, recycler = true).available)
    }

    @Test
    fun alwaysEnabledMachinesStaySelected() {
        val alwaysOn =
            listOf(
                Factory.NO_FACTORY,
                Factory.PUMPJACK,
                Factory.OFFSHORE_PUMP,
                Factory.OIL_REFINERY,
                Factory.CHEMICAL_PLANT,
                Factory.CENTRIFUGE,
                Factory.ROCKET_SILO,
            )
        for (factory in alwaysOn) {
            assertTrue(factory in base.defaults, "$factory")
            assertFalse(factory in base.singles, "$factory")
            assertEquals(base.defaults, base.remove(base.defaults, factory), "$factory")
        }
    }

    @Test
    fun spaceAgeAlwaysEnabledMachines() {
        val alwaysOn =
            listOf(
                Factory.CRUSHER,
                Factory.CENTRIFUGE,
                Factory.CAPTIVE_BITER_SPAWNER,
                Factory.AGRICULTURAL_TOWER,
                Factory.ROTTING,
            )
        for (factory in alwaysOn) {
            assertTrue(factory in spaceAge.defaults, "$factory")
            assertFalse(factory in spaceAge.singles, "$factory")
            assertEquals(spaceAge.defaults, spaceAge.remove(spaceAge.defaults, factory), "$factory")
        }
    }

    @Test
    fun groupsHaveExactlyOneSelected() {
        for (config in listOf(base, spaceAge)) {
            for (group in config.groups) {
                assertEquals(1, group.count { it in config.defaults }, "group $group")
            }
        }
    }

    @Test
    fun groupMembersAreNotSingles() {
        for (config in listOf(base, spaceAge)) {
            assertTrue(config.groups.flatten().none { it in config.singles })
            assertTrue(config.singles.none { config.groups.flatten().contains(it) })
        }
    }
}
