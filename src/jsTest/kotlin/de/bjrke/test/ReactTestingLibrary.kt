package de.bjrke.test

import kotlin.js.JsNonModule
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import react.ReactNode
import web.dom.Element

/**
 * Thin Kotlin facade over
 * [@testing-library/react](https://testing-library.com/docs/react-testing-library/intro/), which
 * runs inside the real browser used by `jsBrowserTest` (karma + headless Chrome).
 *
 * Typed as `dynamic` for matchers/options on purpose: RTL accepts plain strings, regexes, functions
 * and rich option objects, and modelling all of that in Kotlin would add noise without catching
 * mistakes.
 */
@JsModule("@testing-library/react")
@JsNonModule
external object TestingLibrary {
    fun render(ui: ReactNode, options: dynamic = definedExternally): RenderResult

    fun cleanup()

    val screen: Screen

    val fireEvent: FireEvent

    fun within(element: Element): Screen

    fun act(callback: () -> dynamic): dynamic

    fun waitFor(callback: () -> dynamic, options: dynamic = definedExternally): dynamic
}

external interface RenderResult {
    val container: Element
    val baseElement: Element

    fun unmount()

    fun rerender(ui: ReactNode)
}

/** The subset of RTL queries the component tests use. */
external interface Screen {
    fun getByText(text: dynamic, options: dynamic = definedExternally): Element

    fun getAllByText(text: dynamic, options: dynamic = definedExternally): Array<Element>

    fun queryByText(text: dynamic, options: dynamic = definedExternally): Element?

    fun getByPlaceholderText(text: dynamic, options: dynamic = definedExternally): Element

    fun queryByPlaceholderText(text: dynamic, options: dynamic = definedExternally): Element?

    fun getByRole(role: dynamic, options: dynamic = definedExternally): Element

    fun getAllByRole(role: dynamic, options: dynamic = definedExternally): Array<Element>

    fun queryByRole(role: dynamic, options: dynamic = definedExternally): Element?

    fun getByTestId(id: dynamic, options: dynamic = definedExternally): Element

    fun queryByTestId(id: dynamic, options: dynamic = definedExternally): Element?

    fun getByDisplayValue(value: dynamic, options: dynamic = definedExternally): Element

    fun getByLabelText(text: dynamic, options: dynamic = definedExternally): Element

    fun getByTitle(title: dynamic, options: dynamic = definedExternally): Element

    fun getAllByTitle(title: dynamic, options: dynamic = definedExternally): Array<Element>

    fun queryByTitle(title: dynamic, options: dynamic = definedExternally): Element?

    fun debug(element: dynamic = definedExternally, maxLength: Int = definedExternally)
}

external interface FireEvent {
    fun click(element: Element, options: dynamic = definedExternally): Boolean

    fun change(element: Element, options: dynamic = definedExternally): Boolean

    fun input(element: Element, options: dynamic = definedExternally): Boolean

    fun keyDown(element: Element, options: dynamic = definedExternally): Boolean

    fun keyUp(element: Element, options: dynamic = definedExternally): Boolean

    fun focus(element: Element, options: dynamic = definedExternally): Boolean

    fun blur(element: Element, options: dynamic = definedExternally): Boolean

    fun mouseDown(element: Element, options: dynamic = definedExternally): Boolean

    fun mouseEnter(element: Element, options: dynamic = definedExternally): Boolean
}

/** Convenience wrapper so tests can just write `render(component.create { ... })`. */
fun render(ui: ReactNode): RenderResult = TestingLibrary.render(ui)

val screen: Screen
    get() = TestingLibrary.screen

val fireEvent: FireEvent
    get() = TestingLibrary.fireEvent

fun within(element: Element): Screen = TestingLibrary.within(element)

/** `{ target: { value } }` as expected by `fireEvent.change`/`fireEvent.input`. */
fun changeTo(text: String): dynamic {
    val target = js("({})")
    target.value = text
    val options = js("({})")
    options.target = target
    return options
}

/** `{ key }` as expected by `fireEvent.keyDown`/`fireEvent.keyUp`. */
fun key(keyName: String): dynamic {
    val options = js("({})")
    options.key = keyName
    return options
}

/**
 * Base class for component tests: enables React's act environment (so RTL can flush updates) and
 * unmounts everything rendered by a test afterwards, since RTL only auto-cleans under a Jest-like
 * runner.
 */
abstract class ComponentTest {
    @BeforeTest
    fun enableReactActEnvironment() {
        js("globalThis.IS_REACT_ACT_ENVIRONMENT = true")
    }

    @AfterTest
    fun cleanupRenderedComponents() {
        TestingLibrary.cleanup()
    }
}
