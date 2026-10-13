package de.bjrke.test

import kotlin.test.Test
import kotlin.test.assertEquals
import react.FC
import react.Props
import react.create
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.span
import react.useState
import web.html.HTMLInputElement

private external interface CounterProps : Props {
    var start: Int
}

private val counter =
    FC<CounterProps>("counter") { props ->
        val (count, setCount) = useState(props.start)
        val (text, setText) = useState("")
        div {
            span { +"count=$count" }
            button {
                +"increment"
                onClick = { setCount(count + 1) }
            }
            input {
                value = text
                onChange = { setText(it.target.value) }
            }
            span { +"text=$text" }
        }
    }

/** Verifies the RTL facade itself: rendering, state updates, event helpers and cleanup. */
class ComponentTestHarnessTest : ComponentTest() {
    @Test
    fun rendersInitialState() {
        render(counter.create { start = 3 })

        assertEquals("count=3", screen.getByText("count=3").textContent)
    }

    @Test
    fun clickUpdatesState() {
        render(counter.create { start = 0 })

        fireEvent.click(screen.getByText("increment"))

        assertEquals("count=1", screen.getByText("count=1").textContent)
    }

    @Test
    fun typingUpdatesControlledInput() {
        render(counter.create { start = 0 })
        val field = screen.getByRole("textbox") as HTMLInputElement

        fireEvent.change(field, changeTo("hello"))

        assertAll(
            { assertEquals("hello", field.value) },
            { assertEquals("text=hello", screen.getByText("text=hello").textContent) },
        )
    }

    @Test
    fun cleanupRemovesRenderedComponent() {
        val result = render(counter.create { start = 0 })

        result.unmount()

        assertAll(
            { assertEquals(0, result.container.childElementCount) },
            { assertEquals(null, screen.queryByText("count=0")) },
        )
    }
}
