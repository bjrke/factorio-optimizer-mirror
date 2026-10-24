package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.blueprint.fixtures.gear
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.logistics
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.plate
import de.bjrke.factorio.optimizer.common.blueprint.fixtures.recipe
import de.bjrke.factorio.optimizer.common.blueprint.model.Blueprint
import de.bjrke.factorio.optimizer.common.blueprint.model.BlueprintEntity
import de.bjrke.factorio.optimizer.common.layout.entity.InfinitySettings
import de.bjrke.factorio.optimizer.common.model.Coordinate
import de.bjrke.factorio.optimizer.common.model.Direction
import de.bjrke.factorio.optimizer.common.model.Io
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.area
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.plan.BlueprintPlanResult
import de.bjrke.factorio.optimizer.common.plan.LogisticsCalculator
import de.bjrke.factorio.optimizer.common.plan.PlannedRecipe
import de.bjrke.factorio.optimizer.common.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.prototype.BeltTier
import de.bjrke.factorio.optimizer.common.prototype.Category
import de.bjrke.factorio.optimizer.common.prototype.Factory
import de.bjrke.factorio.optimizer.common.prototype.InserterTier
import de.bjrke.factorio.optimizer.common.prototype.Recipe
import de.bjrke.factorio.optimizer.common.service.FactoryProduction
import de.bjrke.factorio.optimizer.common.service.fixtures.testLogistics
import de.bjrke.test.ComponentTest
import de.bjrke.test.RenderResult
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import de.bjrke.test.fireEvent
import de.bjrke.test.render
import de.bjrke.test.screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import react.create
import web.dom.Element
import web.html.HTMLButtonElement
import web.html.HTMLTextAreaElement

private val plate = Material("iron-plate")
private val gear = Material("iron-gear-wheel")

private val craft =
    Recipe(
        "iron-gear-wheel",
        mapOf(plate to 2.0),
        mapOf(gear to 1.0),
        1.0,
        setOf(Category.CRAFTING),
    )

private val plan =
    BlueprintPlan(
        target = gear,
        rows =
            listOf(
                PlannedRow(
                    listOf(
                        PlannedRecipe(
                            FactoryProduction(
                                gear,
                                craft,
                                Factory.ASSEMBLING_MACHINE_3,
                                1.0,
                                0,
                            ),
                        ),
                    ),
                ),
            ),
        sources = listOf(PlannedSource(plate, 1.0, fluid = false)),
        scale = 1.0,
        label = "iron-gear-wheel",
    )

class BlueprintExportTest : ComponentTest() {

    private val logistics =
        LogisticsCalculator(
            testLogistics,
            BeltTier.EXPRESS_TRANSPORT_BELT,
            InserterTier.FAST_INSERTER,
            lanes = 2,
            beltStack = 1,
            inserterStack = 1,
        )

    private fun render(outcome: BlueprintPlanResult) =
        render(
            blueprintButton.create {
                this.outcome = outcome
                this.logistics = this@BlueprintExportTest.logistics
            },
        )

    private fun renderView(vararg entities: BlueprintEntity) =
        render(blueprintView.create { blueprint = Blueprint("test", entities = entities.toList()) })

    private fun RenderResult.image(title: String): Element? =
        container.querySelector("img[title=\"$title\"]")

    private fun computedTransform(element: Element?) =
        js("(function (el) { return getComputedStyle(el).transform; })")(element)

    private fun computedLeft(element: Element?) =
        js("(function (el) { return getComputedStyle(el).left; })")(element)

    @Test
    fun displayRotationTurnsEachDirectionalIconToItsBlueprintDirection() =
        assertTableOf(
            BlueprintEntity(
                "fast-loader",
                area = Coordinate(0, 0).area(2, 1),
                direction = Direction.EAST,
                type = Io.OUTPUT,
            ) to 90,
            BlueprintEntity(
                "fast-splitter",
                area = Coordinate(0, 0).area(1, 2),
                direction = Direction.EAST,
            ) to 180,
            BlueprintEntity(
                "fast-underground-belt",
                area = Coordinate(0, 0).area(1, 1),
                direction = Direction.EAST,
                type = Io.INPUT,
            ) to null,
            BlueprintEntity(
                "fast-underground-belt",
                area = Coordinate(0, 0).area(1, 1),
                direction = Direction.EAST,
                type = Io.OUTPUT,
            ) to 180,
            BlueprintEntity(
                "express-transport-belt",
                area = Coordinate(0, 0).area(1, 1),
                direction = Direction.NORTH,
            ) to 270,
        ) { (entity, expected) ->
            assertEquals(expected, blueprintDisplayRotation(entity))
        }

    @Test
    fun aLoaderIsRotatedClockwiseAndStretchedToItsFootprint() {
        val result =
            renderView(
                BlueprintEntity(
                    "fast-loader",
                    area = Coordinate(0, 0).area(2, 1),
                    direction = Direction.EAST,
                    type = Io.OUTPUT,
                ),
            )

        val loader = assertNotNull(result.image("fast-loader"))
        assertAll(
            { assertEquals("matrix(0, 1, -1, 0, 0, 0)", computedTransform(loader)) },
            // the quarter turn swaps the box, so the rotated icon still fills the 2x1 area
            { assertEquals("24", loader.getAttribute("width")) },
            { assertEquals("48", loader.getAttribute("height")) },
        )
    }

    @Test
    fun aSplitterPointsOppositeAndFillsItsFootprint() {
        val result =
            renderView(
                BlueprintEntity(
                    "fast-splitter",
                    area = Coordinate(0, 0).area(1, 2),
                    direction = Direction.EAST,
                ),
            )

        val splitter = assertNotNull(result.image("fast-splitter"))
        assertAll(
            { assertEquals("matrix(-1, 0, 0, -1, 0, 0)", computedTransform(splitter)) },
            { assertEquals("24", splitter.getAttribute("width")) },
            { assertEquals("48", splitter.getAttribute("height")) },
        )
    }

    @Test
    fun undergroundBeltExitsPointOppositeWhereInputsDoNot() =
        assertTableOf(
            Io.INPUT to "none",
            Io.OUTPUT to "matrix(-1, 0, 0, -1, 0, 0)",
        ) { (io, expected) ->
            val result =
                renderView(
                    BlueprintEntity(
                        "fast-underground-belt",
                        area = Coordinate(0, 0).area(1, 1),
                        direction = Direction.EAST,
                        type = io,
                    ),
                )
            assertEquals(expected, computedTransform(result.image("fast-underground-belt")))
        }

    @Test
    fun aNonSquareMachineStretchesToItsFootprint() {
        val result =
            renderView(
                BlueprintEntity(
                    "recycler",
                    area = Coordinate(0, 0).area(4, 2),
                    recipe = "iron-gear-wheel",
                ),
            )

        val recycler = assertNotNull(result.image("recycler"))
        assertAll(
            { assertEquals("96", recycler.getAttribute("width")) },
            { assertEquals("48", recycler.getAttribute("height")) },
        )
    }

    @Test
    fun recipesAndMaterialsAreShownCentredOnTheirEntity() {
        data class OverlayCase(
            val entity: BlueprintEntity,
            val overlay: String,
            val size: Int,
            val left: String,
        )

        assertTableOf(
            OverlayCase(
                BlueprintEntity(
                    "assembling-machine-3",
                    area = Coordinate(0, 0).area(3, 3),
                    recipe = "iron-gear-wheel",
                ),
                "iron-gear-wheel",
                36,
                // half of the 72px entity, centred
                "18px",
            ),
            OverlayCase(
                BlueprintEntity(
                    "constant-combinator",
                    area = Coordinate(0, 0).area(1, 1),
                    marker = Material("iron-plate"),
                ),
                "iron-plate",
                12,
                "6px",
            ),
            OverlayCase(
                BlueprintEntity(
                    "infinity-chest",
                    area = Coordinate(0, 0).area(1, 1),
                    infinity = InfinitySettings(item = Material("iron-plate")),
                ),
                "iron-plate",
                12,
                "6px",
            ),
        ) { (entity, overlay, size, left) ->
            val result = renderView(entity)
            val image = assertNotNull(result.image(overlay))
            assertAll(
                { assertEquals(size.toString(), image.getAttribute("width")) },
                { assertEquals(left, computedLeft(image.parentElement)) },
            )
        }
    }

    @Test
    fun theBlueprintScalesDownToFitThePreview() {
        val result =
            renderView(
                BlueprintEntity(
                    "transport-belt",
                    Coordinate(0, 0).area(1, 1),
                    direction = Direction.EAST,
                ),
            )

        val svg = assertNotNull(result.container.querySelector("svg"))
        assertAll(
            { assertEquals("xMidYMid meet", svg.getAttribute("preserveAspectRatio")) },
            { assertEquals("0 0 24 24", svg.getAttribute("viewBox")) },
        )
    }

    @Test
    fun theDialogCoversTheAppAreaAboveTheFooter() {
        render(BlueprintPlanResult.Planned(plan))
        fireEvent.click(screen.getByText(BUTTON_TEXT))

        val overlay = screen.getByRole("dialog")
        assertEquals("blueprint-overlay", overlay.getAttribute("class"))
    }

    @Test
    fun anUnavailablePlanDisablesTheButton() {
        render(BlueprintPlanResult.Unavailable("too big"))

        val button = screen.getByText(BUTTON_TEXT) as HTMLButtonElement
        assertAll(
            { assertTrue(button.disabled) },
            { assertEquals("too big", button.title) },
        )
    }

    @Test
    fun openingTheDialogShowsACopyableBlueprintAndRendersIt() {
        render(BlueprintPlanResult.Planned(plan))

        fireEvent.click(screen.getByText(BUTTON_TEXT))

        val textarea = screen.getByRole("textbox") as HTMLTextAreaElement
        assertAll(
            { assertTrue(textarea.readOnly) },
            { assertTrue(textarea.value.startsWith("0"), textarea.value.take(8)) },
            { assertTrue(textarea.value.length > 20) },
            { assertNotNull(screen.queryByTitle(Factory.ASSEMBLING_MACHINE_3.identifier)) },
        )
    }

    @Test
    fun theDialogClosesAgain() {
        render(BlueprintPlanResult.Planned(plan))
        fireEvent.click(screen.getByText(BUTTON_TEXT))

        fireEvent.click(screen.getByText("Close"))

        assertEquals(null, screen.queryByRole("textbox"))
    }
}
