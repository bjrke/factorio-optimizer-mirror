package de.bjrke.factorio.optimizer.common.business.fixtures

import de.bjrke.factorio.optimizer.common.business.BlueprintPlan
import de.bjrke.factorio.optimizer.common.business.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.business.Plumbing
import de.bjrke.factorio.optimizer.common.business.TransportEntity
import de.bjrke.factorio.optimizer.common.business.blueprintLayoutDump
import de.bjrke.factorio.optimizer.common.model.BeltEnd

// The layout facts the suites assert on, read back from the production blueprintLayoutDump. They
// live in the test source set: in commonMain only the CLI's text layout uses the dump.

/** Inputs whose belt could not be routed; empty for a valid blueprint. */
internal fun blueprintFeedFailures(
    plan: BlueprintPlan,
    logistics: LogisticsCalculator,
    beltEnd: BeltEnd = BeltEnd.CONSTANT_COMBINATOR,
) = blueprintLayoutDump(plan, logistics, beltEnd).failures

/** The material each transport entity carries, keyed by tile; tests check nothing merges. */
internal fun blueprintTransportMaterials(
    plan: BlueprintPlan,
    logistics: LogisticsCalculator,
    beltEnd: BeltEnd = BeltEnd.CONSTANT_COMBINATOR,
) =
    blueprintLayoutDump(plan, logistics, beltEnd)
        .entities
        .filterIsInstance<TransportEntity>()
        .associate { it.area.topLeft to it.material }

/** The fluid each pipe or pipe-to-ground tile carries; tests check no fluids merge. */
internal fun blueprintPipeFluids(
    plan: BlueprintPlan,
    logistics: LogisticsCalculator,
    beltEnd: BeltEnd = BeltEnd.CONSTANT_COMBINATOR,
) =
    blueprintLayoutDump(plan, logistics, beltEnd).entities.filterIsInstance<Plumbing>().associate {
        it.area.topLeft to it.material
    }
