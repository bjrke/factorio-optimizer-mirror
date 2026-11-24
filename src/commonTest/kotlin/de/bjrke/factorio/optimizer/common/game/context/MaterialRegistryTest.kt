package de.bjrke.factorio.optimizer.common.game.context

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MaterialRegistryTest {

    @Test
    fun materialsAreInternedByName() {
        val registry = MaterialRegistry(emptySet())
        val first = registry.Material("iron-plate")
        val second = registry.Material("iron-plate")

        assertAll(
            { assertSame(first, second) },
            { assertEquals(1, registry.size) },
            { assertSame(first, registry.atIndex(first.index)) },
        )
    }

    @Test
    fun distinctNamesGetDenseIncreasingIndices() {
        val registry = MaterialRegistry(emptySet())
        val materials =
            listOf("iron-ore", "iron-plate", "copper-plate").map { registry.Material(it) }

        assertEquals(listOf(0, 1, 2), materials.map(Material::index))
        assertEquals(materials, List(registry.size) { registry.atIndex(it) })
    }

    @Test
    fun fluidNamesBecomeFluidMaterials() {
        val registry = MaterialRegistry(fluidNames = setOf("water", "steam"))
        val water = registry.Material("water")
        val ironPlate = registry.Material("iron-plate")

        assertAll(
            { assertTrue(water.isFluid) },
            { assertTrue(registry.Material("steam").isFluid) },
            { assertFalse(ironPlate.isFluid) },
        )
    }

    @Test
    fun aNameKeepsItsFirstPrototype() {
        val registry = MaterialRegistry(emptySet())
        val pseudo = registry.Material("nutrients-fuel", prototypeName = "nutrients")

        assertEquals("nutrients", pseudo.prototypeName)
        assertFailsWith<IllegalArgumentException> { registry.Material("nutrients-fuel", "other") }
    }

    @Test
    fun separateRegistriesAreSeparateUniverses() {
        val first = MaterialRegistry(emptySet())
        val second = MaterialRegistry(emptySet())
        val firstPlate = first.Material("iron-plate")
        val secondPlate = second.Material("iron-plate")

        assertAll(
            { assertNotSame(firstPlate, secondPlate) },
            { assertTrue(firstPlate != secondPlate) },
            { assertEquals(firstPlate.index, secondPlate.index) },
        )
    }

    @Test
    fun materialEqualityIsReferenceEqualityAndMapsByIndex() {
        val registry = MaterialRegistry(emptySet())
        val plate = registry.Material("iron-plate")
        val gear = registry.Material("iron-gear-wheel")
        val rates = mapOf(plate to 2.0)

        assertAll(
            { assertEquals(plate, registry.Material("iron-plate")) },
            { assertNotEquals(plate, gear) },
            { assertEquals(2.0, rates[registry.Material("iron-plate")]) },
            { assertEquals(plate.index, plate.hashCode()) },
        )
    }
}
