package de.bjrke.factorio.optimizer.js.component

import de.bjrke.test.ComponentTest
import de.bjrke.test.assertAll
import de.bjrke.test.fireEvent
import de.bjrke.test.render
import de.bjrke.test.screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import react.create
import web.html.HTMLInputElement

class SpaceAgeToggleTest : ComponentTest() {
    private val toggles = mutableListOf<Boolean>()

    private fun renderToggle(enabled: Boolean = false) =
        render(
            spaceAgeToggle.create {
                this.enabled = enabled
                toggle = { toggles += it }
            }
        )

    @Test
    fun theLogoCarriesTheLabelInItsAltText() {
        val result = renderToggle()
        val logo = result.container.querySelector("img")

        assertAll(
            { assertEquals("Space Age", logo?.getAttribute("alt")) },
            {
                assertEquals(
                    "icons/factorio-space-age-logo-11tiles.png",
                    logo?.getAttribute("src"),
                )
            },
        )
    }

    @Test
    fun clickingTheLogoTogglesSpaceAge() {
        renderToggle(enabled = false)

        fireEvent.click(screen.getByRole("checkbox"))

        assertEquals(listOf(true), toggles)
    }

    @Test
    fun anEnabledSpaceAgeIsMarkedSelected() {
        renderToggle(enabled = true)
        val checkbox = screen.getByRole("checkbox") as HTMLInputElement

        assertAll(
            { assertTrue(checkbox.checked) },
            { assertTrue("selected" in (checkbox.closest("label")?.getAttribute("class") ?: "")) },
        )
    }
}
