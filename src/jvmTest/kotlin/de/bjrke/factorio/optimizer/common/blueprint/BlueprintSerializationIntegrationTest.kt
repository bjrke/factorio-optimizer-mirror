package de.bjrke.factorio.optimizer.common.blueprint

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.decode
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.optimize
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.testBlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.json.toJson
import de.bjrke.factorio.optimizer.common.blueprint.output.blueprintString
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import kotlin.test.Test
import kotlin.test.assertContains

/** End-to-end check that a real plan becomes a string the game can read back. */
class BlueprintSerializationIntegrationTest {

    @Test
    fun aRealTargetProducesADecodableBlueprint() {
        val result = optimize("electronic-circuit")
        val logistics = LogisticsCalculator(result.logistics)
        val planned = testBlueprintPlan(result, logistics)
        val json = decode(blueprintString(fromPlaced(planned, logistics).toJson()))

        assertContains(json, "\"blueprint\"", message = json.take(120))
        assertContains(json, "\"entities\"", message = "no entities in the blueprint")
    }
}
