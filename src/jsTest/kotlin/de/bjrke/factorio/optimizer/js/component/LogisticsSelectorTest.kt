package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.business.testLogistics
import de.bjrke.factorio.optimizer.common.model.BeltEnd
import de.bjrke.factorio.optimizer.common.model.BeltTier
import de.bjrke.factorio.optimizer.common.model.InserterTier
import de.bjrke.factorio.optimizer.common.model.LogisticsConfig
import de.bjrke.factorio.optimizer.common.model.Surface
import de.bjrke.test.ComponentTest
import de.bjrke.test.assertAll
import de.bjrke.test.changeTo
import de.bjrke.test.fireEvent
import de.bjrke.test.named
import de.bjrke.test.render
import de.bjrke.test.screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import react.create
import web.html.HTMLInputElement

class LogisticsSelectorTest : ComponentTest() {
    private val logisticsConfig = LogisticsConfig(testLogistics, spaceAge = true)
    private val belts = mutableListOf<BeltTier>()
    private val inserters = mutableListOf<InserterTier>()
    private val lanes = mutableListOf<Int>()
    private val beltStacks = mutableListOf<Int>()
    private val inserterStacks = mutableListOf<Int>()
    private val beltEnds = mutableListOf<BeltEnd>()

    private fun renderSelector(
        usedConfig: LogisticsConfig = logisticsConfig,
        surface: Surface = Surface.NAUVIS,
        belt: BeltTier = usedConfig.defaultBelt,
        inserter: InserterTier = usedConfig.defaultInserter,
        beltLanes: Int = 1,
        beltStack: Int = usedConfig.beltStackMin(inserter),
        inserterStack: Int = 1,
        beltEnd: BeltEnd = BeltEnd.CONSTANT_COMBINATOR,
    ) =
        render(
            logisticsSelector.create {
                config = usedConfig
                this.surface = surface
                selectedBelt = belt
                selectedInserter = inserter
                this.beltLanes = beltLanes
                this.beltStack = beltStack
                this.inserterStack = inserterStack
                this.beltEnd = beltEnd
                selectBelt = { belts += it }
                selectInserter = { inserters += it }
                selectBeltLanes = { lanes += it }
                selectBeltStack = { beltStacks += it }
                selectInserterStack = { inserterStacks += it }
                selectBeltEnd = { beltEnds += it }
            },
        )

    @Test
    fun selectingABeltReportsIt() {
        renderSelector()

        fireEvent.click(screen.getByRole("radio", named(BeltTier.TRANSPORT_BELT.identifier)))

        assertEquals(listOf(BeltTier.TRANSPORT_BELT), belts)
    }

    @Test
    fun selectingAnInserterReportsIt() {
        renderSelector()

        fireEvent.click(screen.getByRole("radio", named(InserterTier.INSERTER.identifier)))

        assertEquals(listOf(InserterTier.INSERTER), inserters)
    }

    @Test
    fun changingTheBeltStackReportsIt() {
        renderSelector()

        fireEvent.change(screen.getByTitle("belt stack"), changeTo("3"))

        assertEquals(listOf(3), beltStacks)
    }

    @Test
    fun theBeltStackIsClampedToTheMaximum() {
        renderSelector()

        fireEvent.change(screen.getByTitle("belt stack"), changeTo("9"))

        assertEquals(listOf(BeltTier.MAX_STACK), beltStacks)
    }

    @Test
    fun theBeltStackControlStartsAtOneWithoutAStackingInserter() {
        renderSelector()

        assertEquals("1", (screen.getByTitle("belt stack") as HTMLInputElement).min)
    }

    @Test
    fun theBeltStackControlStartsAtTwoWithTheStackInserter() {
        renderSelector(inserter = InserterTier.STACK_INSERTER)

        assertEquals(
            BeltTier.MIN_STACK_INSERTER_STACK.toString(),
            (screen.getByTitle("belt stack") as HTMLInputElement).min,
        )
    }

    @Test
    fun theBeltStackIsRaisedToTheMinimumWithTheStackInserter() {
        renderSelector(inserter = InserterTier.STACK_INSERTER)

        fireEvent.change(screen.getByTitle("belt stack"), changeTo("1"))

        assertEquals(listOf(BeltTier.MIN_STACK_INSERTER_STACK), beltStacks)
    }

    @Test
    fun theInserterStackIsClampedToTheInserterMaximum() {
        renderSelector(inserter = InserterTier.INSERTER)

        // a plain inserter tops out at 3 hand slots
        fireEvent.change(screen.getByTitle("inserter stack"), changeTo("9"))

        assertEquals(listOf(logisticsConfig.maxStack(InserterTier.INSERTER)), inserterStacks)
    }

    @Test
    fun theInserterStackIsRaisedToTheInsertersHandSize() {
        renderSelector(inserter = InserterTier.STACK_INSERTER)

        // a stack inserter always carries at least six items
        fireEvent.change(screen.getByTitle("inserter stack"), changeTo("3"))

        assertEquals(listOf(6), inserterStacks)
    }

    @Test
    fun theInserterStackControlStartsAtTheInsertersHandSize() {
        renderSelector(inserter = InserterTier.BULK_INSERTER)

        assertEquals("2", (screen.getByTitle("inserter stack") as HTMLInputElement).min)
    }

    @Test
    fun togglingFullBeltReportsBothLanes() {
        renderSelector()

        fireEvent.click(screen.getByLabelText("full belt"))

        assertEquals(listOf(BeltTier.MAX_LANES), lanes)
    }

    @Test
    fun theBeltStackControlIsShownWithSpaceAge() {
        renderSelector()

        assertNotNull(screen.queryByTitle("belt stack"))
    }

    @Test
    fun theBeltStackControlIsHiddenWithoutSpaceAge() {
        // belt stacking needs the Space Age stack inserters, so there is nothing to choose
        renderSelector(usedConfig = LogisticsConfig(testLogistics, spaceAge = false))

        assertNull(screen.queryByTitle("belt stack"))
    }

    @Test
    fun selectingABeltEndReportsIt() {
        renderSelector()

        fireEvent.click(screen.getByRole("radio", named(BeltEnd.BUFFER_CHEST.identifier)))

        assertEquals(listOf(BeltEnd.BUFFER_CHEST), beltEnds)
    }

    @Test
    fun chestBeltEndsAreDisabledInSpace() {
        renderSelector(surface = Surface.SPACE)

        val buffer =
            screen.getByRole("radio", named(BeltEnd.BUFFER_CHEST.identifier)) as HTMLInputElement
        val label =
            screen.getByRole("radio", named(BeltEnd.CONSTANT_COMBINATOR.identifier))
                as HTMLInputElement

        assertAll(
            { assertTrue(buffer.disabled, "a chest marker is disabled on a space platform") },
            { assertFalse(label.disabled, "the constant-combinator label stays selectable") },
        )
    }
}
