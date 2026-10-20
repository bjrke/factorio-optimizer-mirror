package de.bjrke.factorio.optimizer.common.business

/**
 * The board and plan-level services every layout stage reads. A stage owns its own state and takes
 * this shared context instead of repeating the same six collaborators in its constructor, so a new
 * stage cannot forget one and an existing one does not grow another parameter.
 */
internal class LayoutStage(
    val board: LayoutBoard,
    val plan: BlueprintPlan,
    val logistics: LogisticsCalculator,
    val planner: RowPlanner,
    val rowPlacer: RowPlacer,
    val router: LayoutRouter,
)
