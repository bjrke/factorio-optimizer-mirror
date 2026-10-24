package de.bjrke.factorio.optimizer.common.prototype

import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import de.bjrke.test.assertTable
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertContains
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
            Factory.SPOILING,
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
            Factory.SPOILING,
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
        assertTable(listOf(base, spaceAge)) { config ->
            assertEquals(config.defaults, config.fromUrl(config.toUrlParam(config.defaults)))
        }

    @Test
    fun urlDecodingIgnoresTheSurfaceBeltAndFlagCodes() =
        assertTable(listOf(base, spaceAge)) { config ->
            assertEquals(
                config.defaults,
                config.fromUrl(config.toUrlParam(config.defaults) + "opteySL"),
            )
        }

    @Test
    fun urlRoundTripKeepsAlwaysEnabledFactories() {
        val decoded = base.fromUrl(base.toUrlParam(base.defaults))
        assertTableOf(Factory.PUMPJACK, Factory.OFFSHORE_PUMP) { factory ->
            assertContains(decoded, factory)
        }
    }

    @Test
    fun groupDefaultsAreNotEncoded() {
        assertAll(
            // the assembler-3, electric furnace and electric mining drill hold no codes, so the
            // base-game defaults encode to the empty string
            { assertEquals("", base.toUrlParam(base.defaults)) },
            // with Space Age only the independently toggled machines remain in the parameter
            {
                assertEquals(
                    spaceAge.singles.mapNotNull { it.code }.sorted().joinToString(""),
                    spaceAge.toUrlParam(spaceAge.defaults),
                )
            },
        )
    }

    @Test
    fun absentSelectionTurnsOnTheDefaultMachines() =
        assertTableOf<Pair<FactoryConfig, String?>>(
            base to null,
            base to "",
            spaceAge to null,
            spaceAge to "",
        ) { (config, selection) ->
            assertEquals(config.defaults, config.fromUrl(selection))
        }

    @Test
    fun aSelectionWithoutAMachinesCodeTurnsItOff() =
        // Space Age on but no factory code: the independently toggled machines are all off
        assertEquals(spaceAge.defaults - spaceAge.singles, spaceAge.fromUrl("S"))

    @Test
    fun rocketSiloIsBaseOnlyAndAlwaysOn() =
        assertAll(
            { assertContains(base.defaults, Factory.ROCKET_SILO) },
            { assertContains(base.alwaysEnabled, Factory.ROCKET_SILO) },
            { assertContainsNot(base.singles, Factory.ROCKET_SILO) },
            { assertEquals(base.defaults, base.remove(base.defaults, Factory.ROCKET_SILO)) },
            { assertContainsNot(spaceAge.available, Factory.ROCKET_SILO) },
            { assertContainsNot(spaceAge.defaults, Factory.ROCKET_SILO) },
        )

    @Test
    fun scienceLabIsAlwaysOn() =
        assertAll(
            { assertContains(base.available, Factory.SCIENCE_LAB) },
            { assertContains(spaceAge.available, Factory.SCIENCE_LAB) },
            { assertContains(base.defaults, Factory.SCIENCE_LAB) },
            { assertContains(spaceAge.defaults, Factory.SCIENCE_LAB) },
            { assertContainsNot(base.singles, Factory.SCIENCE_LAB) },
            { assertContainsNot(spaceAge.singles, Factory.SCIENCE_LAB) },
        )

    @Test
    fun spaceAgeMachinesAreHiddenWithoutSpaceAge() =
        assertTable(spaceAgeOnlyFactories) { factory ->
            assertContainsNot(base.available, factory)
        }

    @Test
    fun spaceAgeMachinesAreAvailableWithSpaceAge() =
        assertTable(spaceAgeOnlyFactories) { factory ->
            assertContains(spaceAge.available, factory)
        }

    @Test
    fun recyclerIsSpaceAgeOnly() =
        assertAll(
            { assertContainsNot(base.available, Factory.RECYCLER) },
            { assertContains(spaceAge.available, Factory.RECYCLER) },
        )

    @Test
    fun alwaysEnabledMachinesAreSelectedByDefault() =
        assertTable(baseAlwaysOnFactories) { factory ->
            assertContains(base.defaults, factory)
        }

    @Test
    fun alwaysEnabledMachinesAreNotSingles() =
        assertTable(baseAlwaysOnFactories) { factory ->
            assertContainsNot(base.singles, factory)
        }

    @Test
    fun alwaysEnabledMachinesCannotBeRemoved() =
        assertTable(baseAlwaysOnFactories) { factory ->
            assertEquals(base.defaults, base.remove(base.defaults, factory), "$factory")
        }

    @Test
    fun spaceAgeAlwaysEnabledMachinesAreSelectedByDefault() =
        assertTable(spaceAgeAlwaysOnFactories) { factory ->
            assertContains(spaceAge.defaults, factory)
        }

    @Test
    fun spaceAgeAlwaysEnabledMachinesAreNotSingles() =
        assertTable(spaceAgeAlwaysOnFactories) { factory ->
            assertContainsNot(spaceAge.singles, factory)
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

    @Test
    fun drillsAndRecyclerOutputDirectly() =
        assertTable(
            listOf(
                Factory.BURNER_MINING_DRILL,
                Factory.ELECTRIC_MINING_DRILL,
                Factory.BIG_MINING_DRILL,
                Factory.RECYCLER,
            ),
        ) { factory ->
            assertTrue(factory.outputsDirectly, "$factory")
        }

    @Test
    fun onlyTheBigMiningDrillAndTheRecyclerStackTheirOutput() =
        assertTable(Factory.entries) { factory ->
            assertEquals(
                factory in setOf(Factory.BIG_MINING_DRILL, Factory.RECYCLER),
                factory.stacksOnBelt,
                "$factory",
            )
        }
}
