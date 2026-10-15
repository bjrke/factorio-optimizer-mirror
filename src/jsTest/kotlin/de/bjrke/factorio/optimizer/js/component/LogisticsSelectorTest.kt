package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.business.testLogistics
import de.bjrke.factorio.optimizer.common.model.Belt
import de.bjrke.factorio.optimizer.common.model.Inserter
import de.bjrke.factorio.optimizer.common.model.LogisticsConfig
import de.bjrke.test.ComponentTest
import de.bjrke.test.changeTo
import de.bjrke.test.fireEvent
import de.bjrke.test.named
import de.bjrke.test.render
import de.bjrke.test.screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import react.create
import web.html.HTMLInputElement

class LogisticsSelectorTest : ComponentTest() {
    private val logisticsConfig = LogisticsConfig(testLogistics, spaceAge = true)
    private val belts = mutableListOf<Belt>()
    private val inserters = mutableListOf<Inserter>()
    private val lanes = mutableListOf<Int>()
    private val beltStacks = mutableListOf<Int>()
    private val inserterStacks = mutableListOf<Int>()

    private fun renderSelector(
        usedConfig: LogisticsConfig = logisticsConfig,
        belt: Belt = usedConfig.defaultBelt,
        inserter: Inserter = usedConfig.defaultInserter,
        beltLanes: Int = 1,
        beltStack: Int = usedConfig.beltStackMin(inserter),
        inserterStack: Int = 1,
    ) =
        render(
            logisticsSelector.create {
                config = usedConfig
                selectedBelt = belt
                selectedInserter = inserter
                this.beltLanes = beltLanes
                this.beltStack = beltStack
                this.inserterStack = inserterStack
                selectBelt = { belts += it }
                selectInserter = { inserters += it }
                selectBeltLanes = { lanes += it }
                selectBeltStack = { beltStacks += it }
                selectInserterStack = { inserterStacks += it }
            }
        )

    @Test
    fun selectingABeltReportsIt() {
        renderSelector()

        fireEvent.click(screen.getByRole("radio", named(Belt.TRANSPORT_BELT.identifier)))

        assertEquals(listOf(Belt.TRANSPORT_BELT), belts)
    }

    @Test
    fun selectingAnInserterReportsIt() {
        renderSelector()

        fireEvent.click(screen.getByRole("radio", named(Inserter.INSERTER.identifier)))

        assertEquals(listOf(Inserter.INSERTER), inserters)
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

        assertEquals(listOf(Belt.MAX_STACK), beltStacks)
    }

    @Test
    fun theBeltStackControlStartsAtOneWithoutAStackingInserter() {
        renderSelector()

        assertEquals("1", (screen.getByTitle("belt stack") as HTMLInputElement).min)
    }

    @Test
    fun theBeltStackControlStartsAtTwoWithTheStackInserter() {
        renderSelector(inserter = Inserter.STACK_INSERTER)

        assertEquals(
            Belt.MIN_STACK_INSERTER_STACK.toString(),
            (screen.getByTitle("belt stack") as HTMLInputElement).min,
        )
    }

    @Test
    fun theBeltStackIsRaisedToTheMinimumWithTheStackInserter() {
        renderSelector(inserter = Inserter.STACK_INSERTER)

        fireEvent.change(screen.getByTitle("belt stack"), changeTo("1"))

        assertEquals(listOf(Belt.MIN_STACK_INSERTER_STACK), beltStacks)
    }

    @Test
    fun theInserterStackIsClampedToTheInserterMaximum() {
        renderSelector(inserter = Inserter.INSERTER)

        // a plain inserter tops out at 3 hand slots
        fireEvent.change(screen.getByTitle("inserter stack"), changeTo("9"))

        assertEquals(listOf(logisticsConfig.maxStack(Inserter.INSERTER)), inserterStacks)
    }

    @Test
    fun theInserterStackIsRaisedToTheInsertersHandSize() {
        renderSelector(inserter = Inserter.STACK_INSERTER)

        // a stack inserter always carries at least six items
        fireEvent.change(screen.getByTitle("inserter stack"), changeTo("3"))

        assertEquals(listOf(6), inserterStacks)
    }

    @Test
    fun theInserterStackControlStartsAtTheInsertersHandSize() {
        renderSelector(inserter = Inserter.BULK_INSERTER)

        assertEquals("2", (screen.getByTitle("inserter stack") as HTMLInputElement).min)
    }

    @Test
    fun togglingFullBeltReportsBothLanes() {
        renderSelector()

        fireEvent.click(screen.getByLabelText("full belt"))

        assertEquals(listOf(Belt.MAX_LANES), lanes)
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
}
