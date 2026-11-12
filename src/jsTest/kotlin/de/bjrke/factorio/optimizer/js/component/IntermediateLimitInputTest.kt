package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.game.prototype.MAX_INTERMEDIATE_LIMIT
import de.bjrke.factorio.optimizer.common.game.prototype.MIN_INTERMEDIATE_LIMIT
import de.bjrke.test.ComponentTest
import de.bjrke.test.assertAll
import de.bjrke.test.changeTo
import de.bjrke.test.fireEvent
import de.bjrke.test.render
import de.bjrke.test.screen
import kotlin.test.Test
import kotlin.test.assertEquals
import react.create

class IntermediateLimitInputTest : ComponentTest() {
    private val changes = mutableListOf<Int>()

    private fun renderInput(value: Int = 10) =
        render(
            intermediateLimitInput.create {
                this.value = value
                change = { changes += it }
            },
        )

    @Test
    fun changingTheLimitReportsTheClampedValue() =
        assertAll(
            {
                renderInput()
                fireEvent.change(
                    screen.getByTitle("Maximum intermediate materials built"),
                    changeTo("7"),
                )
                assertEquals(listOf(7), changes)
            },
            {
                changes.clear()
                fireEvent.change(
                    screen.getByTitle("Maximum intermediate materials built"),
                    changeTo("99"),
                )
                assertEquals(listOf(MAX_INTERMEDIATE_LIMIT), changes)
            },
            {
                changes.clear()
                fireEvent.change(
                    screen.getByTitle("Maximum intermediate materials built"),
                    changeTo("0"),
                )
                assertEquals(listOf(MIN_INTERMEDIATE_LIMIT), changes)
            },
        )
}
