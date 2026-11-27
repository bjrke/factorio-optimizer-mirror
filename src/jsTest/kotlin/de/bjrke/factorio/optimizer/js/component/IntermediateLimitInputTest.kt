package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.game.prototype.MAX_INTERMEDIATE_LIMIT
import de.bjrke.factorio.optimizer.common.game.prototype.MIN_INTERMEDIATE_LIMIT
import de.bjrke.test.ComponentTest
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import de.bjrke.test.changeTo
import de.bjrke.test.fireEvent
import de.bjrke.test.key
import de.bjrke.test.render
import de.bjrke.test.screen
import kotlin.test.Test
import kotlin.test.assertEquals
import react.create
import web.html.HTMLInputElement

class IntermediateLimitInputTest : ComponentTest() {

    private val changes = mutableListOf<Int>()

    private fun renderInput(value: Int = 10) =
        render(
            intermediateLimitInput.create {
                this.value = value
                change = { changes += it }
            },
        )

    private fun input(): HTMLInputElement =
        screen.getByTitle("Maximum intermediate materials built") as HTMLInputElement

    @Test
    fun typingTheLimitDoesNotReportUntilTheEditIsCommitted() {
        renderInput()

        fireEvent.change(input(), changeTo("2"))

        assertAll(
            // a keystroke must not replan the factory
            { assertEquals(emptyList(), changes) },
            { assertEquals("2", input().value) },
        )

        fireEvent.blur(input())

        assertEquals(listOf(2), changes)
    }

    @Test
    fun anEmptyFieldStaysEditableAndFallsBackOnBlur() {
        renderInput()

        fireEvent.change(input(), changeTo(""))

        assertAll(
            { assertEquals(emptyList(), changes) },
            { assertEquals("", input().value) },
        )

        fireEvent.blur(input())

        assertAll(
            { assertEquals(emptyList(), changes) },
            { assertEquals("10", input().value) },
        )
    }

    @Test
    fun aCommittedValueIsClamped() {
        renderInput()

        assertTableOf(
            7 to 7,
            99 to MAX_INTERMEDIATE_LIMIT,
            0 to MIN_INTERMEDIATE_LIMIT,
        ) { (typed, expected) ->
            changes.clear()
            fireEvent.change(input(), changeTo(typed.toString()))
            fireEvent.keyDown(input(), key("Enter"))

            assertEquals(listOf(expected), changes, typed.toString())
        }
    }
}
