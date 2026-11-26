package de.bjrke.factorio.optimizer.common.blueprint.planning.band

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.advancedCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.calculator
import de.bjrke.factorio.optimizer.common.fixtures.copperCableRecipe
import de.bjrke.factorio.optimizer.common.fixtures.electronicCircuitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.engineUnitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.findProductionByRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateRecipe
import de.bjrke.factorio.optimizer.common.fixtures.optimizationResult
import de.bjrke.factorio.optimizer.common.fixtures.production
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.test.TOLERANCE
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The arrangement search's pruning, the machine budget before it, and the cached ratio penalty. */
class BandSearchTest : BaseGameCtxTest() {

    private val logistics = calculator()

    @Test
    fun anArrangedPlanCarriesItsRatioPenalty() {
        val circuit = production(electronicCircuitRecipe, amount = 4.0)
        val cable = production(copperCableRecipe, amount = 6.0)
        val context = DirectFeedContext(optimizationResult(circuit, cable), logistics = logistics)
        val plan = assertNotNull(context.arrange(context.candidateLinks()))

        // the 6:4 need is the exact 3:2 cycle, so the stored penalty is zero
        assertEquals(0.0, plan.ratioPenalty, TOLERANCE)
    }

    @Test
    fun aWindowThatCannotHitTheRatioCarriesItsPenalty() {
        // the consumer wants 1.2 producer machines; only a 5:4 window fits the machine budget,
        // so it overfeeds the link by 1/24 of the wanted ratio and pays half of that
        val wire = Material("wire")
        val gadget = Material("gadget")
        val producer =
            production(
                Recipe(
                    name = "make-wire",
                    ingredients = emptyMap(),
                    result = mapOf(wire to 1.0),
                    time = 1.0,
                    categories = setOf(Category.CRAFTING),
                ),
            )
        val consumer =
            production(
                Recipe(
                    name = "make-gadget",
                    ingredients = mapOf(wire to 1.2),
                    result = mapOf(gadget to 1.0),
                    time = 1.0,
                    categories = setOf(Category.CRAFTING),
                ),
                amount = 4.0,
            )
        val context =
            DirectFeedContext(optimizationResult(consumer, producer), logistics = logistics)
        val plan = assertNotNull(context.arrange(context.candidateLinks()))

        assertEquals(9, plan.machines.size)
        assertEquals(1.0 / 48.0, plan.ratioPenalty, TOLERANCE)
    }

    @Test
    fun arrangePicksTheShortestWindow() {
        val circuit = production(electronicCircuitRecipe, amount = 4.0)
        val cable = production(copperCableRecipe, amount = 6.0)
        val context = DirectFeedContext(optimizationResult(circuit, cable), logistics = logistics)
        val plan = assertNotNull(context.arrange(context.candidateLinks()))

        // 6 cable + 4 circuit is the shortest window; a longer valid window must not win
        assertEquals(10, plan.machines.size)
    }

    @Test
    fun arrangeRealizesEveryLinkAroundTheCycle() {
        // three recipes in a triangle: one link is only adjacent across the cycle's wrap boundary,
        // so the search must close the cycle before it accepts the arrangement
        val advanced = production(advancedCircuitRecipe)
        val circuit = production(electronicCircuitRecipe, amount = 4.0)
        val cable = production(copperCableRecipe, amount = 6.0)
        val context =
            DirectFeedContext(optimizationResult(advanced, circuit, cable), logistics = logistics)
        val links = context.candidateLinks()
        val plan = assertNotNull(context.arrange(links))

        assertEquals(
            emptyList(),
            links.filterNot { link -> plan.feeds.any { it.link == link } },
            links.toString(),
        )
    }

    @Test
    fun aSingleLinkGroupFillsItsTenMachineBudget() =
        assertTableOf(
            // 4 circuits need 6 cable machines, so the group's need is exactly the ten machines a
            // one-link band may build; a fraction more circuit no longer fits
            4.0 to true,
            4.4 to false,
        ) { (circuitAmount, expected) ->
            val result =
                optimizationResult(
                    production(electronicCircuitRecipe, amount = circuitAmount),
                    production(copperCableRecipe, amount = circuitAmount * 1.5),
                )
            val context = DirectFeedContext(result, logistics = logistics)
            val circuit = result.findProductionByRecipe(electronicCircuitRecipe)
            val cable = result.findProductionByRecipe(copperCableRecipe)

            assertEquals(expected, context.groupMayFit(listOf(context.feedLink(cable, circuit))))
        }

    @Test
    fun aTwoLinkGroupIsOneMachineShorter() =
        assertTableOf(
            // 6 engine units need one gear and two plate machines, so six, one and two machines
            // fill the nine a band of two links may build
            6.0 to true,
            // 7 engine units need ten machines: the same need a single link may build, which the
            // two-link budget rejects
            7.0 to false,
        ) { (engineAmount, expected) ->
            val result =
                optimizationResult(
                    production(engineUnitRecipe, amount = engineAmount),
                    production(ironGearWheelRecipe),
                    production(ironPlateRecipe),
                )
            val context = DirectFeedContext(result, logistics = logistics)
            val engine = result.findProductionByRecipe(engineUnitRecipe)
            val gear = result.findProductionByRecipe(ironGearWheelRecipe)
            val plate = result.findProductionByRecipe(ironPlateRecipe)
            val links = listOf(context.feedLink(plate, gear), context.feedLink(gear, engine))

            assertEquals(expected, context.groupMayFit(links))
        }

    @Test
    fun aLinkHoldingAnEqualCopyStillPairsByRecipeSlot() =
        assertTableOf(CopiedEndpoint.PRODUCER, CopiedEndpoint.CONSUMER, CopiedEndpoint.BOTH) {
            copied ->
            val cable = production(copperCableRecipe, amount = 6.0)
            val circuit = production(electronicCircuitRecipe, amount = 4.0)
            val result = optimizationResult(circuit, cable)
            val context = DirectFeedContext(result, logistics = logistics)
            val original =
                context.feedLink(
                    result.findProductionByRecipe(copperCableRecipe),
                    result.findProductionByRecipe(electronicCircuitRecipe),
                )
            // the link holds an equal copy of an endpoint, not the instance the context owns; an
            // identity match would never pair it with the canonical machine
            val copyLink =
                FeedLink(
                    if (copied == CopiedEndpoint.CONSUMER) original.producer
                    else FeedNode(context, original.producer.production.copy()),
                    if (copied == CopiedEndpoint.PRODUCER) original.consumer
                    else FeedNode(context, original.consumer.production.copy()),
                    original.material,
                )

            val plan = assertNotNull(context.arrange(listOf(copyLink)))

            assertContains(plan.feeds.map { it.link }, copyLink)
        }

    @Test
    fun aLinkHoldingAnUnequalCopyStillPairsByRecipeSlot() {
        val cable = production(copperCableRecipe, amount = 6.0)
        val circuit = production(electronicCircuitRecipe, amount = 4.0)
        val result = optimizationResult(circuit, cable)
        val context = DirectFeedContext(result, logistics = logistics)
        val original =
            context.feedLink(
                result.findProductionByRecipe(copperCableRecipe),
                result.findProductionByRecipe(electronicCircuitRecipe),
            )
        // the belted pass copies a production with its own amount, so a copy can share the slot
        // but not be value-equal; a value match would miss the slot's machine
        val copyLink =
            FeedLink(
                FeedNode(context, original.producer.production.copy(amount = 7.0)),
                original.consumer,
                original.material,
            )

        val plan = assertNotNull(context.arrange(listOf(copyLink)))

        assertContains(plan.feeds.map { it.link }, copyLink)
    }
}

/** Which endpoint of a link the copy test replaces with an equal copy. */
private enum class CopiedEndpoint {
    PRODUCER,
    CONSUMER,
    BOTH,
}
