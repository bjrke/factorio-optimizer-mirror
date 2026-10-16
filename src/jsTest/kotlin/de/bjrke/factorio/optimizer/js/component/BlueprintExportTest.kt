package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.business.*
import de.bjrke.factorio.optimizer.common.model.*
import de.bjrke.test.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import react.create
import web.html.HTMLButtonElement
import web.html.HTMLTextAreaElement

private val plate = Material("iron-plate")
private val gear = Material("iron-gear-wheel")

private val craft =
    Recipe(
        "iron-gear-wheel",
        mapOf(plate to 2.0),
        mapOf(gear to 1.0),
        1.0,
        setOf(Category.CRAFTING),
    )

private val plan =
    BlueprintPlan(
        target = gear,
        recipes =
            listOf(
                PlannedRecipe(
                    production =
                        FactoryProduction(gear, craft, Factory.ASSEMBLING_MACHINE_3, 1.0, 0),
                    machines = 1.0,
                    directInsert = emptySet(),
                )
            ),
        sources = listOf(PlannedSource(plate, 1.0, fluid = false)),
        scale = 1.0,
        label = "iron-gear-wheel",
    )

class BlueprintExportTest : ComponentTest() {

    private val logistics =
        LogisticsCalculator(
            testLogistics,
            Belt.EXPRESS_TRANSPORT_BELT,
            Inserter.FAST_INSERTER,
            lanes = 2,
            beltStack = 1,
            inserterStack = 1,
        )

    private fun render(outcome: BlueprintPlanResult) =
        render(
            blueprintButton.create {
                this.outcome = outcome
                this.logistics = this@BlueprintExportTest.logistics
            }
        )

    @Test
    fun anUnavailablePlanDisablesTheButton() {
        render(BlueprintPlanResult.Unavailable("too big"))

        val button = screen.getByText(BUTTON_TEXT) as HTMLButtonElement
        assertAll(
            { assertTrue(button.disabled) },
            { assertEquals("too big", button.title) },
        )
    }

    @Test
    fun openingTheDialogShowsACopyableBlueprintAndRendersIt() {
        render(BlueprintPlanResult.Planned(plan))

        fireEvent.click(screen.getByText(BUTTON_TEXT))

        val textarea = screen.getByRole("textbox") as HTMLTextAreaElement
        assertAll(
            { assertTrue(textarea.readOnly) },
            { assertTrue(textarea.value.startsWith("0"), textarea.value.take(8)) },
            { assertTrue(textarea.value.length > 20) },
            { assertNotNull(screen.queryByTitle(Factory.ASSEMBLING_MACHINE_3.identifier)) },
        )
    }

    @Test
    fun theDialogClosesAgain() {
        render(BlueprintPlanResult.Planned(plan))
        fireEvent.click(screen.getByText(BUTTON_TEXT))

        fireEvent.click(screen.getByText("Close"))

        assertEquals(null, screen.queryByRole("textbox"))
    }
}
