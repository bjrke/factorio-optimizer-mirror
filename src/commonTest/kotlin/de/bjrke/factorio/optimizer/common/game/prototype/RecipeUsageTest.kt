package de.bjrke.factorio.optimizer.common.game.prototype

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.test.assertAll
import kotlin.test.Test
import kotlin.test.assertEquals

class RecipeUsageTest {

    private val ore = Material("ore")
    private val plate = Material("plate")
    private val gear = Material("gear")
    private val pipe = Material("pipe")
    private val target = Material("target")

    private fun recipe(
        name: String,
        ingredients: Map<Material, Double>,
        result: Map<Material, Double>,
    ) = Recipe(name, ingredients, result, time = 1.0, categories = setOf(Category.CRAFTING))

    @Test
    fun countsEveryRecipeThatTransitivelyUsesAMaterial() {
        val usage =
            RecipeUsage(
                listOf(
                    recipe("smelt", mapOf(ore to 1.0), mapOf(plate to 1.0)),
                    recipe("gear", mapOf(plate to 1.0), mapOf(gear to 1.0)),
                    recipe("pipe", mapOf(plate to 1.0), mapOf(pipe to 1.0)),
                    recipe(
                        "target",
                        mapOf(gear to 1.0, pipe to 1.0),
                        mapOf(target to 1.0),
                    ),
                ),
            )

        assertAll(
            // ore is used by smelt, gear, pipe and target; plate by gear, pipe and target
            { assertEquals(4, usage.counts[ore]) },
            { assertEquals(3, usage.counts[plate]) },
            { assertEquals(1, usage.counts[gear]) },
            { assertEquals(1, usage.counts[pipe]) },
            // the target is produced, never used
            { assertEquals(null, usage.counts[target]) },
        )
    }

    @Test
    fun ordersTheLeastUsedMaterialsFirstAndBreaksTiesByName() {
        val usage =
            RecipeUsage(
                listOf(
                    recipe("smelt", mapOf(ore to 1.0), mapOf(plate to 1.0)),
                    recipe("gear", mapOf(plate to 1.0), mapOf(gear to 1.0)),
                    recipe("pipe", mapOf(plate to 1.0), mapOf(pipe to 1.0)),
                    recipe(
                        "target",
                        mapOf(gear to 1.0, pipe to 1.0),
                        mapOf(target to 1.0),
                    ),
                ),
            )

        assertEquals(listOf(gear, pipe, plate, ore), usage.order)
    }

    @Test
    fun aCycleDoesNotRepeatAMaterial() {
        val byproduct = Material("byproduct")
        val usage =
            RecipeUsage(
                listOf(
                    recipe("forward", mapOf(target to 1.0), mapOf(byproduct to 1.0)),
                    recipe("back", mapOf(byproduct to 1.0), mapOf(target to 1.0)),
                ),
            )

        assertAll(
            // both recipes reach both materials through the cycle, but each material is counted
            // once per recipe
            { assertEquals(2, usage.counts[target]) },
            { assertEquals(2, usage.counts[byproduct]) },
        )
    }

    @Test
    fun theLimitClampsToTheSelectableRange() =
        assertAll(
            { assertEquals(DEFAULT_INTERMEDIATE_LIMIT, validIntermediateLimit(null)) },
            { assertEquals(DEFAULT_INTERMEDIATE_LIMIT, validIntermediateLimit("not-a-number")) },
            { assertEquals(MIN_INTERMEDIATE_LIMIT, validIntermediateLimit("0")) },
            { assertEquals(MAX_INTERMEDIATE_LIMIT, validIntermediateLimit("99")) },
            { assertEquals(7, validIntermediateLimit("7")) },
        )
}
