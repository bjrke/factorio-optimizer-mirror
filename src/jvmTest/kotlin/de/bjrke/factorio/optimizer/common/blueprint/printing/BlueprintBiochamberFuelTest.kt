package de.bjrke.factorio.optimizer.common.blueprint.printing

import de.bjrke.factorio.optimizer.common.blueprint.layout.validation.validateLayout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.Layout
import de.bjrke.factorio.optimizer.common.blueprint.printing.fixtures.decode
import de.bjrke.factorio.optimizer.common.blueprint.printing.json.toJson
import de.bjrke.factorio.optimizer.common.blueprint.printing.output.blueprintString
import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.bioflux
import de.bjrke.factorio.optimizer.common.fixtures.nutrients
import de.bjrke.factorio.optimizer.common.fixtures.recipes
import de.bjrke.factorio.optimizer.common.game.prototype.Surface
import de.bjrke.factorio.optimizer.common.game.prototype.biochamberFuel
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * The biochamber's nutrient fuel is a pseudo plan material. It must stay visible in the plan but
 * never become an item name in the blueprint the game reads.
 */
class BlueprintBiochamberFuelTest : BaseGameCtxTest() {

    @Test
    fun glebaNutrientPlansNameTheRealItemInTheBlueprint() {
        val biochamberFuel = materialRegistry.biochamberFuel()
        assertTableOf(nutrients, bioflux) { target ->
            val layout = Layout(target.name, surface = Surface.GLEBA)
            val json = decode(blueprintString(layout.blueprint.toJson()))

            assertAll(
                { assertEquals(emptyList(), layout.failures) },
                { assertEquals(emptyList(), validateLayout(layout.entities)) },
                {
                    assertContains(
                        layout.plan.recipes.flatMap { it.recipe.ingredients.keys },
                        biochamberFuel,
                    )
                },
                { assertContains(layout.plan.sources.map { it.material }, biochamberFuel) },
                { assertContainsNot(json, biochamberFuel.name) },
                { assertContains(json, "\"name\":\"nutrients\"") },
            )
        }
    }
}
