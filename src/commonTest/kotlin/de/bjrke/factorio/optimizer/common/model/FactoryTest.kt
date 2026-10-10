package de.bjrke.factorio.optimizer.common.model

import de.bjrke.factorio.optimizer.common.model.Factories.defaultFactories
import de.bjrke.factorio.optimizer.common.model.Factories.factoryGroups
import de.bjrke.factorio.optimizer.common.model.Factories.factorySetFromUrl
import de.bjrke.factorio.optimizer.common.model.Factories.removeFactory
import de.bjrke.factorio.optimizer.common.model.Factories.selectableFactories
import de.bjrke.factorio.optimizer.common.model.Factories.singleFactories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FactoryTest {

    @Test
    fun defaultSelectsElectricMiningDrillOnly() {
        assertTrue(Factory.ELECTRIC_MINING_DRILL in defaultFactories)
        assertFalse(Factory.BURNER_MINING_DRILL in defaultFactories)
        assertFalse(Factory.BIG_MINING_DRILL in defaultFactories)
    }

    @Test
    fun selectingAMinerDeselectsTheOthers() {
        val selected = defaultFactories.addFactory(Factory.BIG_MINING_DRILL)
        assertTrue(Factory.BIG_MINING_DRILL in selected)
        assertFalse(Factory.ELECTRIC_MINING_DRILL in selected)
        assertFalse(Factory.BURNER_MINING_DRILL in selected)
    }

    @Test
    fun miningDrillsAreSelectable() {
        assertTrue(Factory.BURNER_MINING_DRILL in selectableFactories)
        assertTrue(Factory.ELECTRIC_MINING_DRILL in selectableFactories)
        assertTrue(Factory.BIG_MINING_DRILL in selectableFactories)
    }

    @Test
    fun urlRoundTrip() {
        val encoded = defaultFactories.toUrlParam()
        assertEquals(defaultFactories, factorySetFromUrl(encoded))
    }

    @Test
    fun removingAMinerLeavesNoMiner() {
        val removed = defaultFactories.removeFactory(Factory.ELECTRIC_MINING_DRILL)
        assertTrue(
            removed.none {
                it == Factory.BURNER_MINING_DRILL ||
                    it == Factory.ELECTRIC_MINING_DRILL ||
                    it == Factory.BIG_MINING_DRILL
            }
        )
    }

    @Test
    fun pumpjackAndOffshorePumpAreAlwaysEnabled() {
        assertTrue(Factory.PUMPJACK in defaultFactories)
        assertTrue(Factory.OFFSHORE_PUMP in defaultFactories)
        assertFalse(Factory.PUMPJACK in selectableFactories)
        assertFalse(Factory.OFFSHORE_PUMP in selectableFactories)
        assertEquals(
            defaultFactories,
            defaultFactories.removeFactory(Factory.PUMPJACK).removeFactory(Factory.OFFSHORE_PUMP),
        )
    }

    @Test
    fun rottingIsAlwaysEnabled() {
        assertTrue(Factory.ROTTING in defaultFactories)
        assertFalse(Factory.ROTTING in selectableFactories)
        assertEquals(
            defaultFactories,
            defaultFactories.removeFactory(Factory.ROTTING),
        )
    }

    @Test
    fun singleMachineFactoriesAreAlwaysEnabled() {
        val alwaysOn =
            listOf(
                Factory.OIL_REFINERY,
                Factory.CHEMICAL_PLANT,
                Factory.CRUSHER,
                Factory.CENTRIFUGE,
                Factory.CAPTIVE_BITER_SPAWNER,
                Factory.AGRICULTURAL_TOWER,
            )
        for (factory in alwaysOn) {
            assertTrue(factory in defaultFactories, "$factory")
            assertFalse(factory in selectableFactories, "$factory")
            assertFalse(factory in singleFactories, "$factory")
            assertEquals(defaultFactories, defaultFactories.removeFactory(factory), "$factory")
        }
    }

    @Test
    fun groupsHaveExactlyOneSelected() {
        factoryGroups.forEach { group ->
            assertEquals(1, group.count { it in defaultFactories }, "group $group")
        }
    }

    @Test
    fun groupMembersAreNotSingles() {
        assertTrue(factoryGroups.flatten().none { it in singleFactories })
        assertTrue(singleFactories.none { factoryGroups.flatten().contains(it) })
    }

    @Test
    fun urlRoundTripKeepsAlwaysEnabledFactories() {
        val encoded = defaultFactories.toUrlParam()
        val decoded = factorySetFromUrl(encoded)
        assertTrue(Factory.PUMPJACK in decoded)
        assertTrue(Factory.OFFSHORE_PUMP in decoded)
    }
}
