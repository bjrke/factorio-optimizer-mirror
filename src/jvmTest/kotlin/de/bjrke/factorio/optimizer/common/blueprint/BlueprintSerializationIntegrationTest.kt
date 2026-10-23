package de.bjrke.factorio.optimizer.common.blueprint

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.decode
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.optimize
import de.bjrke.factorio.optimizer.common.blueprint.json.toJson
import de.bjrke.factorio.optimizer.common.blueprint.output.blueprintString
import de.bjrke.factorio.optimizer.common.blueprint.planning.blueprintPlan
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlanResult
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.prototype.BeltTier
import de.bjrke.factorio.optimizer.common.prototype.InserterTier
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertIs

/** End-to-end check that a real plan becomes a string the game can read back. */
class BlueprintSerializationIntegrationTest {

    @Test
    fun aRealTargetProducesADecodableBlueprint() {
        val result = optimize("electronic-circuit")
        val logistics =
            LogisticsCalculator(
                result.logistics,
                BeltTier.FAST_TRANSPORT_BELT,
                InserterTier.FAST_INSERTER,
                lanes = 2,
                beltStack = 1,
                inserterStack = 1,
            )

        val planned = assertIs<BlueprintPlanResult.Planned>(blueprintPlan(result, logistics)).plan
        val json = decode(blueprintString(fromPlaced(planned, logistics).toJson()))

        assertContains(json, "\"blueprint\"", message = json.take(120))
        assertContains(json, "\"entities\"", message = "no entities in the blueprint")
    }
}
