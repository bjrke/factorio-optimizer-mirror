package de.bjrke.factorio.optimizer.common.special

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
            .map { Material(it) }

    @Test
    fun blankQueryReturnsEverythingInOrder() =
        assertTableOf("" to materials, "   " to materials) { (query, expected) ->
            assertEquals(expected, searchMaterials(materials, query))
        }

    @Test
    fun matchingIsCaseInsensitive() {
        assertEquals(
            listOf(Material("iron-ore"), Material("iron-plate")),
            searchMaterials(materials, "IRON"),
        )
    }

    @Test
    fun matchesPrefixAcrossWords() {
        assertEquals(
            listOf(Material("copper-ore"), Material("copper-plate")),
            searchMaterials(materials, "copp"),
        )
    }

    @Test
    fun requiresEveryQueryTokenToMatch() =
        assertTableOf(
            "iron plate" to listOf(Material("iron-plate")),
            "iron zzz" to emptyList(),
        ) { (query, expected) ->
            assertEquals(expected, searchMaterials(materials, query))
        }

    @Test
    fun matchesQueryWithSeparators() =
        assertTableOf(
            "iron-plate" to listOf(Material("iron-plate")),
            "ironplate" to listOf(Material("iron-plate")),
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
                .map { Material(it) },
            searchMaterials(materials, "circuit"),
        )
    }

    @Test
    fun matchesFuzzySubsequence() {
        assertEquals(listOf(Material("iron-plate")), searchMaterials(materials, "irnplt"))
    }

    @Test
    fun respectsLimit() {
        assertEquals(2, searchMaterials(materials, "copper", limit = 2).size)
    }
}
