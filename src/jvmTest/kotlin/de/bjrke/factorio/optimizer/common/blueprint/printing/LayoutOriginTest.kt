package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.assertNonNegativeOrigin
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.basePlan
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.fluidRowPlan
import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The layout origin on the small fixture plans: every placed tile keeps a non-negative column and
 * row, so the blueprint never carries a negative coordinate. The real plans and every chest marker
 * design are covered by the integration tables, which build them once for all their invariants.
 */
class LayoutOriginTest : BaseGameCtxTest() {

    @Test
    fun theFixtureLayoutsKeepEveryTileNonNegative() =
        assertTableOf(
            OriginCase("base plan", Layout(basePlan)),
            OriginCase("fluid row", Layout(fluidRowPlan(Factory.CHEMICAL_PLANT))),
        ) { origin ->
            assertAll(
                {
                    assertEquals(
                        emptyList(),
                        origin.layout.failures,
                        "${origin.name} left a feed unrouted",
                    )
                },
                { assertNonNegativeOrigin(origin.layout.entities) },
            )
        }
}

/** One fixture layout under test, labelled by name so the table prints which case failed. */
private class OriginCase(val name: String, val layout: Layout) {
    override fun toString() = name
}
