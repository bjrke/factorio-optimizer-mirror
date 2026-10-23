package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.prototype.FactoryConfig
import de.bjrke.test.ComponentTest
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import de.bjrke.test.fireEvent
import de.bjrke.test.named
import de.bjrke.test.render
import de.bjrke.test.screen
import kotlin.test.Test
import kotlin.test.assertContains
import react.create
import web.dom.Element
import web.html.HTMLInputElement

class FactorySelectorTest : ComponentTest() {
    private val factoryConfig = FactoryConfig(spaceAge = true)
    private val selections = mutableListOf<Set<Factory>>()

    private fun renderSelector(selected: Set<Factory> = factoryConfig.defaults) =
        render(
            factorySelector.create {
                config = factoryConfig
                selectedFactories = selected
                selectFactory = { selections += it }
            },
        )

    private fun option(name: String): HTMLInputElement =
        screen.getByRole("radio", named(name)) as HTMLInputElement

    private fun labelClasses(element: Element): String =
        element.closest("label")?.getAttribute("class") ?: ""

    @Test
    fun theSelectedFactoryIsMarkedOnItsLabel() {
        renderSelector()

        assertAll(
            { assertContains(labelClasses(option("assembling-machine-3")), "selected") },
            { assertContainsNot(labelClasses(option("assembling-machine-1")), "selected") },
        )
    }

    @Test
    fun clickingADifferentAssemblerSelectsItInsteadOfTheCurrentOne() {
        renderSelector()

        fireEvent.click(option("assembling-machine-1"))

        val selected = selections.single()
        assertAll(
            { assertContains(selected, Factory.ASSEMBLING_MACHINE_1) },
            { assertContainsNot(selected, Factory.ASSEMBLING_MACHINE_3) },
        )
    }

    @Test
    fun togglingASingleMachineAddsIt() {
        val single = factoryConfig.singles.first()
        renderSelector(selected = factoryConfig.defaults - single)

        fireEvent.click(screen.getByRole("checkbox", named(single.identifier)))

        assertContains(selections.single(), single)
    }
}
