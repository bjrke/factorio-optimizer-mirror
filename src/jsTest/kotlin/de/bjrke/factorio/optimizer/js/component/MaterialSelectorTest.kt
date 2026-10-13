package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.test.ComponentTest
import de.bjrke.test.assertAll
import de.bjrke.test.changeTo
import de.bjrke.test.fireEvent
import de.bjrke.test.key
import de.bjrke.test.render
import de.bjrke.test.screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import react.create
import web.html.HTMLInputElement

private val ironOre = Material.of("iron-ore")
private val copperOre = Material.of("copper-ore")
private val materials = listOf(ironOre, copperOre)

class MaterialSelectorTest : ComponentTest() {
    private val selections = mutableListOf<Material>()

    private fun renderSelector() =
        render(
            materialSelector.create {
                material = ironOre
                availableMaterials = materials
                changeMaterial = { selections += it }
            }
        )

    private fun searchField() = screen.getByPlaceholderText(ironOre.name) as HTMLInputElement

    @Test
    fun focusOpensDropdownListingAvailableMaterials() {
        renderSelector()

        assertNull(screen.queryByText(copperOre.name))

        fireEvent.focus(searchField())

        assertAll(
            { assertEquals(ironOre.name, screen.getByText(ironOre.name).textContent) },
            { assertEquals(copperOre.name, screen.getByText(copperOre.name).textContent) },
        )
    }

    @Test
    fun typingFiltersTheDropdown() {
        renderSelector()

        fireEvent.focus(searchField())
        fireEvent.change(searchField(), changeTo("copper"))

        assertAll(
            { assertEquals(copperOre.name, screen.getByText(copperOre.name).textContent) },
            { assertNull(screen.queryByText(ironOre.name)) },
        )
    }

    @Test
    fun clickingAnOptionSelectsItAndClosesTheDropdown() {
        renderSelector()
        fireEvent.focus(searchField())

        fireEvent.mouseDown(screen.getByText(copperOre.name))

        assertAll(
            { assertEquals(listOf(copperOre), selections) },
            { assertNull(screen.queryByText(copperOre.name)) },
        )
    }

    @Test
    fun escapeClearsTheQueryAndClosesTheDropdown() {
        renderSelector()
        fireEvent.focus(searchField())
        fireEvent.change(searchField(), changeTo("copper"))

        fireEvent.keyDown(searchField(), key("Escape"))

        assertAll(
            { assertEquals("", searchField().value) },
            { assertNull(screen.queryByText(copperOre.name)) },
        )
    }

    @Test
    fun arrowDownThenEnterSelectsTheHighlightedOption() {
        renderSelector()
        fireEvent.focus(searchField())

        fireEvent.keyDown(searchField(), key("ArrowDown"))
        fireEvent.keyDown(searchField(), key("Enter"))

        assertEquals(listOf(copperOre), selections)
    }
}
