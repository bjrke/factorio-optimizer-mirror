package de.bjrke.factorio.optimizer.common.fixtures

import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.service.FactoryProduction

/**
 * Every planned production in plan order; a production may appear once per row. A test-only view:
 * production code reads `BlueprintPlan.rows` instead, so the plan owns no derived recipe list.
 */
internal val BlueprintPlan.recipes: List<FactoryProduction>
    get() = rows.flatMap { it.productions }
