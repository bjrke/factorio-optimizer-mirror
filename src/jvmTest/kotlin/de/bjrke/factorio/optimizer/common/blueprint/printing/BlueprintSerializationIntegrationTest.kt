package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.decode
import de.bjrke.factorio.optimizer.common.blueprint.printing.json.toJson
import de.bjrke.factorio.optimizer.common.blueprint.printing.output.blueprintString
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/** End-to-end check that a real plan becomes a string the game can read back. */
class BlueprintSerializationIntegrationTest {

    @Test
    fun aRealTargetProducesADecodableBlueprint() {
        val layout = Layout("electronic-circuit")
        val json = decode(blueprintString(layout.blueprint.toJson()))

        assertAll(
            { assertEquals(emptyList(), layout.failures) },
            { assertContains(json, "\"blueprint\"", message = json.take(120)) },
            { assertContains(json, "\"entities\"", message = "no entities in the blueprint") },
        )
    }
}
