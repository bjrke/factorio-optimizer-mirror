package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.model.Surface
import de.bjrke.test.ComponentTest
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import de.bjrke.test.fireEvent
import de.bjrke.test.named
import de.bjrke.test.render
import de.bjrke.test.screen
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import react.create
import web.dom.Element
import web.html.HTMLInputElement

class PlanetSelectorTest : ComponentTest() {
    private val selections = mutableListOf<Surface>()

    private fun renderSelector(
        selected: Surface = Surface.NAUVIS,
        enabled: Boolean = true,
    ) =
        render(
            planetSelector.create {
                selectedSurface = selected
                availableSurfaces = Surface.entries
                this.enabled = enabled
                selectSurface = { selections += it }
            },
        )

    private fun option(surface: Surface): HTMLInputElement =
        screen.getByRole("radio", named(surface.identifier)) as HTMLInputElement

    private fun labelClasses(element: Element): String =
        element.closest("label")?.getAttribute("class") ?: ""

    @Test
    fun theSelectedSurfaceIsMarkedOnItsLabel() {
        renderSelector(selected = Surface.VULCANUS)

        assertAll(
            { assertContains(labelClasses(option(Surface.VULCANUS)), "selected") },
            { assertContainsNot(labelClasses(option(Surface.NAUVIS)), "selected") },
        )
    }

    @Test
    fun clickingASurfaceReportsIt() {
        renderSelector()

        fireEvent.click(option(Surface.GLEBA))

        assertEquals(listOf(Surface.GLEBA), selections)
    }

    @Test
    fun everySurfaceIsDisabledWhenSpaceAgeIsOff() {
        renderSelector(enabled = false)

        assertTrue(option(Surface.NAUVIS).disabled)
    }
}
