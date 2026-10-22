package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.blueprint.output.FactorioUrl
import de.bjrke.factorio.optimizer.common.blueprint.output.parseFactorioUrl
import de.bjrke.factorio.optimizer.common.data.DEFAULT_NORMALIZE_TARGET
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FactorioUrlTest {

    @Test
    fun parsesEveryFormAUserMightPaste() =
        assertTableOf(
            "http://localhost:8080/#/4.5/advanced-circuit/CFSXbmr/1/1",
            "localhost:8080/#/4.5/advanced-circuit/CFSXbmr/1/1",
            "#/4.5/advanced-circuit/CFSXbmr/1/1",
            "http://example.com/some/page#/4.5/advanced-circuit/CFSXbmr/1/1",
        ) { text ->
            assertEquals(
                FactorioUrl(4.5, "advanced-circuit", "CFSXbmr", "1", "1"),
                parseFactorioUrl(text),
                text,
            )
        }

    @Test
    fun missingTrailingSegmentsDecodeToNullsSoTheDefaultsApply() =
        assertTableOf(
            "#/4.5/advanced-circuit",
            "#/4.5/advanced-circuit/",
            "http://localhost:8080/#/4.5/advanced-circuit?foo=bar",
        ) { text ->
            assertEquals(
                FactorioUrl(4.5, "advanced-circuit", null, null, null),
                parseFactorioUrl(text),
                text,
            )
        }

    @Test
    fun anInvalidNormalizeTargetFallsBackToTheDefault() =
        assertEquals(
            FactorioUrl(DEFAULT_NORMALIZE_TARGET, "advanced-circuit", "S", "1", "1"),
            parseFactorioUrl("#/not-a-number/advanced-circuit/S/1/1"),
        )

    @Test
    fun nonUrlsAreRejected() =
        assertTableOf<String?>(
            null,
            "",
            "advanced-circuit",
            "iron-plate",
            "#/only-one-segment",
            "#/",
            "https://example.com/page",
        ) { text ->
            assertNull(parseFactorioUrl(text.orEmpty()), text)
        }

    @Test
    fun aMalformedUrlStillNeedsAHash() =
        assertAll(
            { assertNull(parseFactorioUrl("4.5/advanced-circuit/S/1/1")) },
            { assertNull(parseFactorioUrl("#x")) },
        )
}
