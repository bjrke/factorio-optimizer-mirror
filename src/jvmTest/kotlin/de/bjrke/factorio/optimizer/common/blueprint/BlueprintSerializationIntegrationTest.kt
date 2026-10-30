package de.bjrke.factorio.optimizer.common.blueprint

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.decode
import de.bjrke.factorio.optimizer.common.blueprint.json.toJson
import de.bjrke.factorio.optimizer.common.blueprint.output.blueprintString
import kotlin.test.Test
import kotlin.test.assertContains

/** End-to-end check that a real plan becomes a string the game can read back. */
class BlueprintSerializationIntegrationTest {

    @Test
    fun aRealTargetProducesADecodableBlueprint() {
        val layout = Layout("electronic-circuit")
        val json = decode(blueprintString(layout.blueprint.toJson()))

        assertContains(json, "\"blueprint\"", message = json.take(120))
        assertContains(json, "\"entities\"", message = "no entities in the blueprint")
    }
}
