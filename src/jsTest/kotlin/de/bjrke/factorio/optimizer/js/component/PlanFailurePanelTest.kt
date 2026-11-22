package de.bjrke.factorio.optimizer.js.component

import de.bjrke.test.ComponentTest
import de.bjrke.test.assertAll
import de.bjrke.test.render
import de.bjrke.test.screen
import kotlin.test.Test
import kotlin.test.assertNotNull
import react.create

/**
 * A render that throws must not leave the user on the router's bare error page or a blank app; the
 * panel keeps the layout and names the reason.
 */
class PlanFailurePanelTest : ComponentTest() {

    @Test
    fun reportsTheFailureReason() {
        render(
            planFailurePanel.create {
                error = IllegalStateException("no feasible production plan for atomic-bomb")
                reset = {}
            },
        )

        assertAll(
            {
                assertNotNull(
                    screen.queryByText("Could not plan this factory."),
                    "the panel does not explain the failure",
                )
            },
            {
                assertNotNull(
                    screen.queryByText("no feasible production plan for atomic-bomb"),
                    "the panel does not report the error message",
                )
            },
        )
    }
}
