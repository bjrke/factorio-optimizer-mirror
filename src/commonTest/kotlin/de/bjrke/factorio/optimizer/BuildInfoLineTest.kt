package de.bjrke.factorio.optimizer

import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

class BuildInfoLineTest {
    @Test
    fun formatsTheBuildLine() =
        assertTableOf(
            Triple("2026-10-10T10:49:10Z", "ace16532", "main") to
                "build 2026-10-10T10:49:10Z · ace16532 · main",
            Triple("unknown", "unknown", "unknown") to "build unknown · unknown · unknown",
        ) { (info, expected) ->
            val (date, commit, branch) = info
            assertEquals(expected, buildInfoLine(date, commit, branch))
        }
}
