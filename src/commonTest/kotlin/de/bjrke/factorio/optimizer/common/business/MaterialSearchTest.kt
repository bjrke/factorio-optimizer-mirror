package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

class MaterialSearchTest {

    private val materials =
        listOf(
                "iron-ore",
                "iron-plate",
                "copper-ore",
                "copper-plate",
                "advanced-circuit",
                "electronic-circuit",
                "science-pack-1",
                "x-circuit",
                "circuitry",
                "mycircuit",
            )
            .map { Material.of(it) }

    @Test
    fun blankQueryReturnsEverythingInOrder() =
        assertTableOf("" to materials, "   " to materials) { (query, expected) ->
            assertEquals(expected, searchMaterials(materials, query))
        }

    @Test
    fun matchingIsCaseInsensitive() {
        assertEquals(
            listOf(Material.of("iron-ore"), Material.of("iron-plate")),
            searchMaterials(materials, "IRON"),
        )
    }

    @Test
    fun matchesPrefixAcrossWords() {
        assertEquals(
            listOf(Material.of("copper-ore"), Material.of("copper-plate")),
            searchMaterials(materials, "copp"),
        )
    }

    @Test
    fun requiresEveryQueryTokenToMatch() =
        assertTableOf(
            "iron plate" to listOf(Material.of("iron-plate")),
            "iron zzz" to emptyList<Material>(),
        ) { (query, expected) ->
            assertEquals(expected, searchMaterials(materials, query))
        }

    @Test
    fun matchesQueryWithSeparators() =
        assertTableOf(
            "iron-plate" to listOf(Material.of("iron-plate")),
            "ironplate" to listOf(Material.of("iron-plate")),
        ) { (query, expected) ->
            assertEquals(expected, searchMaterials(materials, query))
        }

    @Test
    fun ranksExactAndPrefixBeforeSubstringAndFuzzy() {
        assertEquals(
            listOf(
                    "advanced-circuit",
                    "electronic-circuit",
                    "x-circuit",
                    "circuitry",
                    "mycircuit",
                )
                .map { Material.of(it) },
            searchMaterials(materials, "circuit"),
        )
    }

    @Test
    fun matchesFuzzySubsequence() {
        assertEquals(listOf(Material.of("iron-plate")), searchMaterials(materials, "irnplt"))
    }

    @Test
    fun respectsLimit() {
        assertEquals(2, searchMaterials(materials, "copper", limit = 2).size)
    }
}
