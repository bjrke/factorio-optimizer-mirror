package de.bjrke.factorio.optimizer.js

import de.bjrke.factorio.optimizer.js.component.urlIntermediateLimitInput
import de.bjrke.test.ComponentTest
import de.bjrke.test.TestingLibrary
import de.bjrke.test.changeTo
import de.bjrke.test.fireEvent
import de.bjrke.test.render
import de.bjrke.test.screen
import kotlin.test.Test
import kotlin.test.assertEquals
import react.FC
import react.Props
import react.create
import tanstack.history.CreateMemoryHistoryOpts
import tanstack.history.RouterHistory
import tanstack.history.createMemoryHistory
import tanstack.react.router.RouteOptions
import tanstack.react.router.RouterOptions
import tanstack.react.router.RouterProvider
import tanstack.react.router.createRootRoute
import tanstack.react.router.createRoute
import tanstack.react.router.createRouter
import tanstack.router.core.RoutePath

/** Edits that originate in a text field replace the current history entry, so Back works. */
class TextEditHistoryTest : ComponentTest() {
    private val editor =
        FC<Props>("textEditEditor") {
            val props = FactorioOptimizerProps()
            urlIntermediateLimitInput(props)
        }

    private fun router(history: RouterHistory) =
        createRootRoute().let { rootRoute ->
            val route =
                createRoute(
                    RouteOptions(
                        getParentRoute = { rootRoute },
                        path = RoutePath($$"/$target"),
                        component = editor,
                    ),
                )
            rootRoute.addChildren(arrayOf(route))
            createRouter(
                RouterOptions(
                    routeTree = rootRoute,
                    history = history,
                ),
            )
        }

    @Test
    fun typingIntoATextFieldReplacesOneHistoryEntry(): dynamic {
        val history =
            createMemoryHistory(
                CreateMemoryHistoryOpts(initialEntries = arrayOf("/iron-gear-wheel")),
            )
        val appRouter = router(history)
        // the React provider otherwise suspends until its layout effect loads the router
        return appRouter.asDynamic().load().then {
            val app = FC { RouterProvider { router = appRouter } }
            render(app.create())

            return@then TestingLibrary.waitFor(
                    {
                        screen.getByTitle("Maximum intermediate materials built")
                    },
                )
                .then { input: dynamic ->
                    val entriesBefore = history.length
                    fireEvent.change(input, changeTo("7"))
                    fireEvent.change(input, changeTo("8"))
                    assertEquals(entriesBefore, history.length)
                }
        }
    }
}
