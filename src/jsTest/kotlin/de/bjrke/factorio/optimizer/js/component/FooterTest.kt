package de.bjrke.factorio.optimizer.js.component

import de.bjrke.test.ComponentTest
import de.bjrke.test.assertAll
import de.bjrke.test.fireEvent
import de.bjrke.test.render
import de.bjrke.test.screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import react.create

private const val BUILD_INFO = "build 2026-10-10T10:49:10Z · ace16532 · main"
private const val LICENSE = "Apache License\nVersion 2.0, January 2004"

class FooterTest : ComponentTest() {
    private fun renderFooter() =
        render(
            footer.create {
                buildInfo = BUILD_INFO
                license = LICENSE
            },
        )

    @Test
    fun showsTheBuildInfo() {
        renderFooter()

        assertEquals(BUILD_INFO, screen.getByText(BUILD_INFO).textContent)
    }

    @Test
    fun linksToTheSourceRepository() {
        val result = renderFooter()
        val link =
            result.container.querySelector("a[href='https://gitlab.com/bjrke/factorio-optimizer']")

        assertEquals("gitlab.com/bjrke/factorio-optimizer", link?.textContent)
    }

    @Test
    fun thePipelineBadgeLinksToTheLatestMainPipeline() {
        val result = renderFooter()
        val badge = result.container.querySelector("img")

        assertAll(
            {
                assertEquals(
                    "https://gitlab.com/bjrke/factorio-optimizer/badges/main/pipeline.svg",
                    badge?.getAttribute("src"),
                )
            },
            { assertEquals("pipeline status", badge?.getAttribute("alt")) },
            {
                assertEquals(
                    "https://gitlab.com/bjrke/factorio-optimizer/-/pipelines/latest?ref=main",
                    badge?.closest("a")?.getAttribute("href"),
                )
            },
        )
    }

    @Test
    fun opensTheLicenseInADialog() {
        renderFooter()
        assertNull(screen.queryByRole("dialog"))

        fireEvent.click(screen.getByText("Apache 2.0"))

        val dialog = screen.getByRole("dialog")
        assertAll(
            { assertTrue(dialog.textContent?.contains(LICENSE) == true) },
            { assertNotNull(screen.getByText("Close")) },
        )
    }

    @Test
    fun closesTheLicenseDialog() {
        renderFooter()
        fireEvent.click(screen.getByText("Apache 2.0"))

        fireEvent.click(screen.getByText("Close"))

        assertNull(screen.queryByRole("dialog"))
    }
}
