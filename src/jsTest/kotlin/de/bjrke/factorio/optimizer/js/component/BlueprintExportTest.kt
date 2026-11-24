package de.bjrke.factorio.optimizer.js.component

import de.bjrke.factorio.optimizer.common.blueprint.RowLayout
import de.bjrke.factorio.optimizer.common.blueprint.layout.BeltEnd
import de.bjrke.factorio.optimizer.common.blueprint.layout.LayoutIssue
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.BeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.ChestEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityChestEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinityPipeEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InfinitySettings
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.InserterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LaneLoad
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LayoutEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.LoaderEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MachineEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.MarkerEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PipeToGroundEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.PoleEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.RequestFilter
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.Signal
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.SplitterEntity
import de.bjrke.factorio.optimizer.common.blueprint.layout.entity.UndergroundBeltEntity
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlan
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.BlueprintPlanResult
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedRow
import de.bjrke.factorio.optimizer.common.blueprint.planning.plan.PlannedSource
import de.bjrke.factorio.optimizer.common.blueprint.printing.model.Blueprint
import de.bjrke.factorio.optimizer.common.core.model.Area
import de.bjrke.factorio.optimizer.common.core.model.Direction
import de.bjrke.factorio.optimizer.common.core.model.Direction.EAST
import de.bjrke.factorio.optimizer.common.core.model.Io.INPUT
import de.bjrke.factorio.optimizer.common.core.model.Io.OUTPUT
import de.bjrke.factorio.optimizer.common.fixtures.BaseGameCtxTest
import de.bjrke.factorio.optimizer.common.fixtures.GameCtxTestMixin
import de.bjrke.factorio.optimizer.common.fixtures.calculator
import de.bjrke.factorio.optimizer.common.fixtures.copperPlate
import de.bjrke.factorio.optimizer.common.fixtures.gear
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironPlate
import de.bjrke.factorio.optimizer.common.fixtures.optimizationResult
import de.bjrke.factorio.optimizer.common.fixtures.production
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.test.ComponentTest
import de.bjrke.test.RenderResult
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import de.bjrke.test.named
import de.bjrke.test.render
import de.bjrke.test.screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import react.create
import web.dom.Element
import web.html.HTMLButtonElement

/** One tooltip expectation: the entity and the lines its hover text carries. */
private data class TitleCase(val entity: LayoutEntity, val expected: List<String>)

class BlueprintExportTest : ComponentTest(), GameCtxTestMixin by BaseGameCtxTest() {

    private val logistics = calculator()

    private val plan =
        BlueprintPlan(
            target = gear,
            rows = listOf(PlannedRow(listOf(production(ironGearWheelRecipe)))),
            sources = listOf(PlannedSource(ironPlate, fluid = false)),
            label = "iron-gear-wheel",
        )

    private fun blueprintOf(outcome: BlueprintPlanResult) =
        (outcome as? BlueprintPlanResult.Planned)?.let {
            RowLayout(it.plan, logistics).blueprint()
        }

    private fun renderPanel(
        outcome: BlueprintPlanResult,
        layoutIssues: List<LayoutIssue> = emptyList(),
    ) =
        render(
            blueprintPanel.create {
                blueprint = blueprintOf(outcome)
                issues = layoutIssues
            },
        )

    private fun renderCopyButton(outcome: BlueprintPlanResult) =
        render(
            blueprintCopyButton.create {
                this.outcome = outcome
                this.blueprint = blueprintOf(outcome)
            },
        )

    private fun renderView(
        vararg entities: LayoutEntity,
        layoutIssues: List<LayoutIssue> = emptyList(),
    ) =
        render(
            blueprintView.create {
                blueprint = Blueprint("test", entities = entities.toList())
                issues = layoutIssues
            },
        )

    /** The image of [name]; its title is the entity's hover text, so it is queried by `alt`. */
    private fun RenderResult.image(name: String): Element? =
        container.querySelector("img[alt=\"$name\"]")

    /** The red layout-issue boxes drawn over the tiles, in render order. */
    private fun RenderResult.issues(): List<Element> {
        val nodes = container.querySelectorAll("div[title]")
        return (0 until nodes.length).mapNotNull { nodes.item(it) }
    }

    private fun computedTransform(element: Element?) =
        js("(function (el) { return getComputedStyle(el).transform; })")(element)

    private fun computedLeft(element: Element?) =
        js("(function (el) { return getComputedStyle(el).left; })")(element)

    private fun computedWidth(element: Element?) =
        js("(function (el) { return getComputedStyle(el).width; })")(element)

    private fun computedBorderColor(element: Element?) =
        js("(function (el) { return getComputedStyle(el).borderTopColor; })")(element)

    private fun computedBackground(element: Element?) =
        js("(function (el) { return getComputedStyle(el).backgroundColor; })")(element)

    @Test
    fun displayRotationTurnsEachDirectionalIconToItsBlueprintDirection() =
        assertTableOf(
            LoaderEntity(Area(0, 0, 2, 1), "fast-loader", ironPlate, OUTPUT, EAST) to 90,
            SplitterEntity(Area(0, 0, 1, 2), "fast-splitter", ironPlate, EAST) to 180,
            UndergroundBeltEntity(
                Area(0, 0, 1, 1),
                "fast-underground-belt",
                ironPlate,
                EAST,
                INPUT,
            ) to 0,
            UndergroundBeltEntity(
                Area(0, 0, 1, 1),
                "fast-underground-belt",
                ironPlate,
                EAST,
                OUTPUT,
            ) to 180,
            BeltEntity(Area(0, 0, 1, 1), "express-transport-belt", ironPlate, Direction.NORTH) to
                270,
            PipeToGroundEntity(Area(0, 0, 1, 1), "pipe-to-ground", water, EAST, INPUT) to 180,
            PipeToGroundEntity(Area(0, 0, 1, 1), "pipe-to-ground", water, EAST, OUTPUT) to 0,
        ) { (entity, expected) ->
            assertEquals(expected, blueprintDisplayRotation(entity))
        }

    @Test
    fun aNonDirectionalEntityKeepsItsIconUpright() =
        assertTableOf(
            ChestEntity(Area(0, 0, 1, 1), "steel-chest"),
            PoleEntity(Area(0, 0, 1, 1), "medium-electric-pole"),
            MarkerEntity(Area(0, 0, 1, 1), "constant-combinator", Signal(ironPlate)),
            InfinityChestEntity(
                Area(0, 0, 1, 1),
                "infinity-chest",
                InfinitySettings(item = ironPlate),
            ),
        ) { entity ->
            assertEquals("none", computedTransform(renderView(entity).image(entity.name)))
        }

    /** An infinity pipe has no icon asset, so pin its rotation without rendering it. */
    @Test
    fun anInfinityPipeKeepsItsIconUpright() =
        assertEquals(
            0,
            blueprintDisplayRotation(
                InfinityPipeEntity(Area(0, 0, 1, 1), "infinity-pipe", water),
            ),
        )

    @Test
    fun aLoaderIsRotatedClockwiseAndStretchedToItsFootprint() {
        val result =
            renderView(LoaderEntity(Area(0, 0, 2, 1), "fast-loader", ironPlate, OUTPUT, EAST))

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
        val result = renderView(SplitterEntity(Area(0, 0, 1, 2), "fast-splitter", ironPlate, EAST))
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
            INPUT to "none",
            OUTPUT to "matrix(-1, 0, 0, -1, 0, 0)",
        ) { (io, expected) ->
            val result =
                renderView(
                    UndergroundBeltEntity(
                        Area(0, 0, 1, 1),
                        "fast-underground-belt",
                        ironPlate,
                        EAST,
                        io,
                    ),
                )
            assertEquals(expected, computedTransform(result.image("fast-underground-belt")))
        }

    @Test
    fun aNonSquareMachineStretchesToItsFootprint() {
        val result =
            renderView(
                MachineEntity(Factory.RECYCLER, Area(0, 0, 4, 2), "iron-gear-wheel"),
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
            val entity: LayoutEntity,
            val overlay: String,
            val size: Int,
            val left: String,
        )

        assertTableOf(
            OverlayCase(
                MachineEntity(Factory.ASSEMBLING_MACHINE_3, Area(0, 0, 3, 3), "iron-gear-wheel"),
                "iron-gear-wheel",
                36,
                // half of the 72px entity, centred
                "18px",
            ),
            OverlayCase(
                MarkerEntity(Area(0, 0, 1, 1), "constant-combinator", Signal(ironPlate)),
                "iron-plate",
                12,
                "6px",
            ),
            OverlayCase(
                InfinityChestEntity(
                    Area(0, 0, 1, 1),
                    "infinity-chest",
                    InfinitySettings(item = ironPlate),
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
                BeltEntity(Area(0, 0, 1, 1), "transport-belt", ironPlate, EAST),
            )

        val svg = assertNotNull(result.container.querySelector("svg"))
        assertAll(
            { assertEquals("xMidYMid meet", svg.getAttribute("preserveAspectRatio")) },
            { assertEquals("0 0 24 24", svg.getAttribute("viewBox")) },
        )
    }

    @Test
    fun titlesShowTheFieldsTheBlueprintCarries() =
        assertTableOf(
            TitleCase(
                MachineEntity(
                    Factory.ASSEMBLING_MACHINE_3,
                    Area(0, 0, 3, 3),
                    "iron-gear-wheel",
                    EAST,
                ),
                listOf(
                    "#1 assembling-machine-3",
                    "at (0, 0) 3x3",
                    "direction: east",
                    "recipe: iron-gear-wheel",
                ),
            ),
            TitleCase(
                PoleEntity(
                    Area(6, 0, 1, 1),
                    "medium-electric-pole",
                    listOf(2, 5),
                ),
                listOf("#1 medium-electric-pole", "at (6, 0) 1x1", "wired to: #2, #5"),
            ),
            TitleCase(
                InfinityChestEntity(
                    Area(0, 4, 1, 1),
                    "infinity-chest",
                    InfinitySettings(item = ironPlate),
                ),
                listOf("#1 infinity-chest", "at (0, 4) 1x1", "provides: iron-plate"),
            ),
            TitleCase(
                ChestEntity(
                    Area(8, 4, 1, 1),
                    "steel-chest",
                    requestFilter = RequestFilter("iron-plate", 50),
                    bar = 4,
                ),
                listOf("#1 steel-chest", "at (8, 4) 1x1", "request: iron-plate x50", "bar: 4"),
            ),
        ) { (entity, expected) ->
            assertEquals(
                expected.joinToString("\n"),
                renderView(entity).image(entity.name)?.getAttribute("title"),
            )
        }

    @Test
    fun titlesAddTheFactsOnlyTheLayoutKnows() =
        assertTableOf(
            TitleCase(
                BeltEntity(
                    Area(3, 1, 1, 1),
                    "express-transport-belt",
                    Direction.SOUTH,
                    LaneLoad.mixed(ironPlate, copperPlate),
                ),
                listOf(
                    "#1 express-transport-belt",
                    "at (3, 1) 1x1",
                    "direction: south",
                    "lanes left=iron-plate right=copper-plate",
                ),
            ),
            TitleCase(
                InserterEntity(
                    Area(4, 2, 1, 1),
                    "long-handed-inserter",
                    copperPlate,
                    Direction.WEST,
                    long = true,
                    mirror = true,
                ),
                listOf(
                    "#1 long-handed-inserter",
                    "at (4, 2) 1x1",
                    "direction: west",
                    "mirrored",
                    "material: copper-plate",
                    "long-handed",
                ),
            ),
        ) { (entity, expected) ->
            assertEquals(
                expected.joinToString("\n"),
                renderView(entity).image(entity.name)?.getAttribute("title"),
            )
        }

    @Test
    fun thePanelShowsTheBlueprintWithoutOpeningAnything() {
        val result = renderPanel(BlueprintPlanResult.Planned(plan))

        val image = assertNotNull(result.image(Factory.ASSEMBLING_MACHINE_3.identifier))
        val title = assertNotNull(image.getAttribute("title"))
        assertAll(
            {
                assertEquals(
                    "#1 ${Factory.ASSEMBLING_MACHINE_3.identifier}",
                    title.lineSequence().first(),
                )
            },
            { assertEquals(null, screen.queryByText(plan.label)) },
        )
    }

    @Test
    fun anUnavailablePlanRendersNoPanel() {
        val result = renderPanel(BlueprintPlanResult.Unavailable("too big"))

        assertAll(
            { assertNull(result.container.querySelector(".blueprint-panel")) },
            { assertNull(screen.queryByText("too big")) },
        )
    }

    @Test
    fun theCopyButtonIsEnabledForAPlan() {
        renderCopyButton(BlueprintPlanResult.Planned(plan))

        val button = screen.getByRole("button", named("Copy blueprint")) as HTMLButtonElement
        assertAll(
            { assertTrue(!button.disabled) },
            { assertEquals("Copy blueprint string", button.title) },
            { assertNotNull(button.querySelector("img[src=\"icons/copy-paste-tool.png\"]")) },
        )
    }

    @Test
    fun anUnavailablePlanDisablesTheCopyButton() {
        renderCopyButton(BlueprintPlanResult.Unavailable("too big"))

        val button = screen.getByRole("button", named("Copy blueprint")) as HTMLButtonElement
        assertAll(
            { assertTrue(button.disabled) },
            { assertEquals("too big", button.title) },
        )
    }

    @Test
    fun anIssueDrawsARedBoxOverItsAreaWithTheMessageOnHover() {
        val result =
            renderView(
                BeltEntity(Area(0, 0, 1, 1), "transport-belt", ironPlate, EAST),
                layoutIssues =
                    listOf(
                        LayoutIssue("belt has no input", Area(0, 0, 1, 1)),
                        LayoutIssue("no route to (2, 0)", Area(2, 0, 2, 1)),
                    ),
            )

        val issues = result.issues()
        assertAll(
            { assertEquals(2, issues.size) },
            { assertEquals("belt has no input", issues[0].getAttribute("title")) },
            { assertEquals("no route to (2, 0)", issues[1].getAttribute("title")) },
            { assertEquals("0px", computedLeft(issues[0])) },
            { assertEquals("48px", computedLeft(issues[1])) },
            { assertEquals("48px", computedWidth(issues[1])) },
            { assertEquals("rgb(215, 38, 38)", computedBorderColor(issues[0])) },
            { assertEquals("rgba(215, 38, 38, 0.08)", computedBackground(issues[0])) },
        )
    }

    @Test
    fun issuesOnTheSameAreaShareOneBox() {
        val result =
            renderView(
                BeltEntity(Area(0, 0, 1, 1), "transport-belt", ironPlate, EAST),
                layoutIssues =
                    listOf(
                        LayoutIssue("first", Area(0, 0, 1, 1)),
                        LayoutIssue("second", Area(0, 0, 1, 1)),
                    ),
            )

        val issues = result.issues()
        assertAll(
            { assertEquals(1, issues.size) },
            { assertEquals("first\nsecond", issues[0].getAttribute("title")) },
        )
    }

    @Test
    fun anIssueOutsideTheEntitiesStillFitsInThePreview() {
        val result =
            renderView(
                BeltEntity(Area(0, 0, 1, 1), "transport-belt", ironPlate, EAST),
                layoutIssues = listOf(LayoutIssue("missing", Area(2, 0, 1, 1))),
            )

        val issue = assertNotNull(result.issues().singleOrNull())
        val svg = assertNotNull(result.container.querySelector("svg"))
        assertAll(
            { assertEquals("0 0 72 24", svg.getAttribute("viewBox")) },
            { assertEquals("48px", computedLeft(issue)) },
        )
    }

    @Test
    fun thePanelMarksTheIssuesItIsGiven() {
        val result =
            renderPanel(
                BlueprintPlanResult.Planned(plan),
                listOf(LayoutIssue("missing", Area(0, 0, 1, 1))),
            )

        assertEquals(1, result.issues().size)
    }

    @Test
    fun thePreviewIsBuiltFromThePlan() {
        val result = optimizationResult(ironGearWheelRecipe, Factory.ASSEMBLING_MACHINE_3)
        val preview = blueprintPreview(result, logistics, 1.0, BeltEnd.CONSTANT_COMBINATOR)
        val blueprint = assertNotNull(preview.blueprint)
        val planned = assertNotNull(preview.outcome as? BlueprintPlanResult.Planned)
        val placed =
            RowLayout(planned.plan, logistics, BeltEnd.CONSTANT_COMBINATOR).layoutDump().entities

        assertAll(
            { assertEquals(placed, blueprint.entities) },
            { assertEquals(emptyList(), preview.issues) },
        )
    }
}
