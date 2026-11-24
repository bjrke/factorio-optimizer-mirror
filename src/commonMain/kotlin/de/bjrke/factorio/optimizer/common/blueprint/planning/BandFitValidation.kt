package de.bjrke.factorio.optimizer.common.blueprint.planning

import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.fits
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.rowBeltPacking
import de.bjrke.factorio.optimizer.common.blueprint.planning.row.RowPlanner
import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.model.IndexedRecipe

/**
 * The recipe sets of the finished [rows] whose solid belts do not pack. `bandBeltFits` can only
 * count lanes while the band search runs, before the feed sides are known; this check runs the real
 * packer on the placed rows, so a band whose inputs end up on opposite sides is reported instead of
 * failing in the layout. A rejected group is rebuilt as its own rows.
 */
internal fun failingBandGroups(
    rows: List<PlannedRow>,
    target: Material,
    scale: Double,
    logistics: LogisticsCalculator,
): Set<Set<IndexedRecipe>> {
    if (rows.none { it.links.isNotEmpty() }) return emptySet()
    // `RowTopology`/`FeedSides` read only the rows and the target, so this plan's feed directions
    // match the real plan even though its external sources are not computed yet.
    val plan =
        BlueprintPlan(
            target = target,
            rows = rows,
            sources = emptyList(),
            scale = scale,
            label = "",
        )
    val planner = RowPlanner(plan, logistics)
    return buildSet {
        for (row in rows) {
            if (row.links.isEmpty()) continue
            val fits =
                rowBeltPacking(
                        row,
                        plan.beltMaterials,
                        logistics,
                        plan::machineRate,
                        planner::feedDirection,
                    )
                    .groups
                    .fits()
            if (!fits) add(row.productions.mapTo(mutableSetOf()) { it.recipe })
        }
    }
}
