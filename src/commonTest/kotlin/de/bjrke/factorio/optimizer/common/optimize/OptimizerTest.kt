package de.bjrke.factorio.optimizer.common.optimize

import de.bjrke.factorio.optimizer.common.core.model.Material
import de.bjrke.factorio.optimizer.common.fixtures.calciteMiningRecipe
import de.bjrke.factorio.optimizer.common.fixtures.castingSteelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.engineUnit
import de.bjrke.factorio.optimizer.common.fixtures.engineUnitRecipe
import de.bjrke.factorio.optimizer.common.fixtures.factoryOf
import de.bjrke.factorio.optimizer.common.fixtures.iceMeltingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironGearWheelRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironOre
import de.bjrke.factorio.optimizer.common.fixtures.ironOreMeltingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironOreMiningRecipe
import de.bjrke.factorio.optimizer.common.fixtures.ironPlateSmeltingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.lavaPumpingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.moltenIron
import de.bjrke.factorio.optimizer.common.fixtures.moltenIronFromLavaRecipe
import de.bjrke.factorio.optimizer.common.fixtures.pipeRecipe
import de.bjrke.factorio.optimizer.common.fixtures.steelPlate
import de.bjrke.factorio.optimizer.common.fixtures.uraniumFuelCell
import de.bjrke.factorio.optimizer.common.fixtures.uraniumFuelCellRecipe
import de.bjrke.factorio.optimizer.common.fixtures.uraniumProcessingRecipe
import de.bjrke.factorio.optimizer.common.fixtures.water
import de.bjrke.factorio.optimizer.common.fixtures.waterPumpingRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Category
import de.bjrke.factorio.optimizer.common.game.prototype.Factory
import de.bjrke.factorio.optimizer.common.game.prototype.ParsedRecipe
import de.bjrke.factorio.optimizer.common.game.prototype.Recipe
import de.bjrke.test.TOLERANCE
import de.bjrke.test.assertAll
import de.bjrke.test.assertContainsNot
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OptimizerTest {

    private val miningIron = ironOreMiningRecipe
    private val pumpWater = waterPumpingRecipe
    private val meltIce = iceMeltingRecipe

    @Test
    fun miningDrillIsUsedForResource() =
        assertEquals(
            Factory.ELECTRIC_MINING_DRILL,
            factoryOf(
                ironOre,
                listOf(miningIron),
                setOf(
                    Factory.ELECTRIC_MINING_DRILL,
                    Factory.NO_FACTORY,
                    Factory.SCIENCE_LAB,
                ),
            ),
        )

    @Test
    fun freeSourceWhenNoDrillSelected() =
        assertEquals(
            Factory.NO_FACTORY,
            factoryOf(
                ironOre,
                listOf(miningIron),
                setOf(Factory.NO_FACTORY, Factory.SCIENCE_LAB),
            ),
        )

    @Test
    fun extractionIsPreferredOverProcessing() =
        assertEquals(
            Factory.OFFSHORE_PUMP,
            factoryOf(
                water,
                // sorted by name like RecipeLoader does: ice-melting before water
                listOf(meltIce, pumpWater),
                setOf(
                    Factory.OFFSHORE_PUMP,
                    Factory.CHEMICAL_PLANT,
                    Factory.NO_FACTORY,
                    Factory.SCIENCE_LAB,
                ),
            ),
        )

    @Test
    fun processingIsUsedWhenNoExtractionMachine() =
        assertEquals(
            Factory.CHEMICAL_PLANT,
            factoryOf(
                water,
                listOf(meltIce, pumpWater),
                setOf(Factory.CHEMICAL_PLANT, Factory.NO_FACTORY, Factory.SCIENCE_LAB),
            ),
        )

    private val pumpLava = lavaPumpingRecipe
    private val mineCalcite = calciteMiningRecipe

    private val vulcanusFactories =
        setOf(
            Factory.FOUNDRY,
            Factory.OFFSHORE_PUMP,
            Factory.ELECTRIC_MINING_DRILL,
            Factory.NO_FACTORY,
            Factory.SCIENCE_LAB,
        )

    @Test
    fun avoidsIngredientsFromFreeSource() {
        // iron-ore has no producer here (not on Vulcanus) so it would come from a chest;
        // lava & calcite are extractable, so the lava recipe should win
        val result =
            Optimizer(
                ParsedRecipe(
                    emptyList(),
                    listOf(
                        ironOreMeltingRecipe,
                        moltenIronFromLavaRecipe,
                        pumpLava,
                        mineCalcite,
                    ),
                ),
                vulcanusFactories,
            )[moltenIron]

        assertEquals("molten-iron-from-lava", result.recipe.name)
    }

    @Test
    fun vulcanusSteelUsesLavaNotChestOre() {
        val result =
            Optimizer(
                ParsedRecipe(
                    emptyList(),
                    listOf(
                        castingSteelRecipe,
                        ironOreMeltingRecipe,
                        moltenIronFromLavaRecipe,
                        pumpLava,
                        mineCalcite,
                    ),
                ),
                vulcanusFactories,
            )[steelPlate]

        assertAll(
            { assertEquals("casting-steel", result.recipe.name) },
            {
                assertEquals(
                    emptyList(),
                    result.factories.values.filter { it.factory == Factory.NO_FACTORY },
                )
            },
            {
                assertNotNull(
                    result.factories.values.firstOrNull {
                        it.recipe.name == "molten-iron-from-lava"
                    },
                )
            },
        )
    }

    @Test
    fun oreBasedRecipeWinsWhenOreIsProducible() {
        // on Nauvis-like surface: iron-ore is mined, lava is not available (free source)
        val result =
            Optimizer(
                ParsedRecipe(
                    emptyList(),
                    listOf(ironOreMeltingRecipe, moltenIronFromLavaRecipe, mineCalcite, miningIron),
                ),
                vulcanusFactories,
            )[moltenIron]

        assertEquals("iron-ore-melting", result.recipe.name)
    }

    @Test
    fun byproductRecipeRunsOnceForAllItsOutputs() {
        val optimizer =
            Optimizer(
                ParsedRecipe(emptyList(), listOf(uraniumProcessingRecipe, uraniumFuelCellRecipe)),
                setOf(
                    Factory.CENTRIFUGE,
                    Factory.ASSEMBLING_MACHINE_3,
                    Factory.NO_FACTORY,
                    Factory.SCIENCE_LAB,
                ),
            )

        val result = optimizer[uraniumFuelCell]

        val processing = result.factories.values.single { it.recipe.name == "uranium-processing" }
        assertAll(
            // 1 fuel cell/s needs 0.1 U-235/s, and one centrifuge yields 0.007/12 U-235/s, so the
            // U-235 demand alone sets the count. The same run also yields the U-238, so the
            // machines are not summed per output.
            { assertEquals(0.1 * 12.0 / 0.007, processing.amount, TOLERANCE) },
        )
    }

    @Test
    fun sharedIntermediateMachinesAreSummed() {
        val mine = ironOreMiningRecipe
        val optimizer =
            Optimizer(
                ParsedRecipe(
                    emptyList(),
                    listOf(
                        mine,
                        ironPlateSmeltingRecipe,
                        ironGearWheelRecipe,
                        pipeRecipe,
                        engineUnitRecipe,
                    ),
                ),
                setOf(
                    Factory.ASSEMBLING_MACHINE_3,
                    Factory.ELECTRIC_FURNACE,
                    Factory.ELECTRIC_MINING_DRILL,
                    Factory.NO_FACTORY,
                    Factory.SCIENCE_LAB,
                ),
            )

        val result = optimizer[engineUnit]

        val smelting = result.factories.values.single { it.recipe.name == "iron-plate" }
        // gear (2 plates) and pipe (1 plate each, 2 per engine) consume 4 plates/s in total
        assertEquals(6.4, smelting.amount, TOLERANCE)
    }

    @Test
    fun theLimitKeepsTheLeastUsedIntermediates() {
        val ore = Material("ore")
        val plate = Material("plate")
        val gear = Material("gear")
        val pipe = Material("pipe")
        val target = Material("target")
        fun recipe(
            name: String,
            ingredients: Map<Material, Double>,
            result: Map<Material, Double>,
        ) = Recipe(name, ingredients, result, time = 1.0, categories = setOf(Category.CRAFTING))

        val parsed =
            ParsedRecipe(
                emptyList(),
                listOf(
                    recipe("smelt", mapOf(ore to 1.0), mapOf(plate to 1.0)),
                    recipe("gear", mapOf(plate to 1.0), mapOf(gear to 1.0)),
                    recipe("pipe", mapOf(plate to 1.0), mapOf(pipe to 1.0)),
                    recipe("target", mapOf(gear to 1.0, pipe to 1.0), mapOf(target to 1.0)),
                ),
            )
        val result =
            Optimizer(
                parsed,
                setOf(Factory.ASSEMBLING_MACHINE_3, Factory.NO_FACTORY),
                maxIntermediates = 1,
            )[target]
        val built =
            result.factories.values
                .filter { it.factory != Factory.NO_FACTORY }
                .map { it.recipe.name }
                .toSet()
        val imported = result.factories.values.map { it.material }

        assertAll(
            // gear is the least-used produced material (tied with ore and pipe, name order wins)
            { assertContains(built, "gear") },
            { assertContainsNot(built, "smelt") },
            { assertContainsNot(built, "pipe") },
            // the dropped materials become imports
            { assertContains(imported, plate) },
            { assertContains(imported, pipe) },
        )
    }

    @Test
    fun anExcludedMaterialIsImportedInsteadOfBuilt() {
        val ore = Material("ore")
        val plate = Material("plate")
        val gear = Material("gear")
        val target = Material("target")
        fun recipe(
            name: String,
            ingredients: Map<Material, Double>,
            result: Map<Material, Double>,
        ) = Recipe(name, ingredients, result, time = 1.0, categories = setOf(Category.CRAFTING))

        val parsed =
            ParsedRecipe(
                emptyList(),
                listOf(
                    recipe("smelt", mapOf(ore to 1.0), mapOf(plate to 1.0)),
                    recipe("gear", mapOf(plate to 1.0), mapOf(gear to 1.0)),
                    recipe("target", mapOf(gear to 1.0), mapOf(target to 1.0)),
                ),
            )
        val result =
            Optimizer(
                parsed,
                setOf(Factory.ASSEMBLING_MACHINE_3, Factory.NO_FACTORY),
                excludedMaterials = setOf(plate),
            )[target]
        val built =
            result.factories.values
                .filter { it.factory != Factory.NO_FACTORY }
                .map { it.recipe.name }
                .toSet()
        val imported =
            result.factories.values
                .filter { Category.IMPORT in it.recipe.categories }
                .map { it.material }
                .toSet()

        assertAll(
            { assertContains(built, "gear") },
            { assertContainsNot(built, "smelt") },
            { assertContains(imported, plate) },
        )
    }

    @Test
    fun excludingAMaterialDropsEveryProducer() {
        val ore = Material("ore")
        val plate = Material("plate")
        val target = Material("target")
        fun recipe(
            name: String,
            ingredients: Map<Material, Double>,
            result: Map<Material, Double>,
        ) = Recipe(name, ingredients, result, time = 1.0, categories = setOf(Category.CRAFTING))

        val parsed =
            ParsedRecipe(
                emptyList(),
                listOf(
                    recipe("smelt-a", mapOf(ore to 1.0), mapOf(plate to 1.0)),
                    recipe("smelt-b", mapOf(ore to 2.0), mapOf(plate to 1.0)),
                    recipe("target", mapOf(plate to 1.0), mapOf(target to 1.0)),
                ),
            )
        val result =
            Optimizer(
                parsed,
                setOf(Factory.ASSEMBLING_MACHINE_3, Factory.NO_FACTORY),
                excludedMaterials = setOf(plate),
            )[target]
        val built = result.factories.values.map { it.recipe.name }.toSet()

        assertAll(
            { assertContainsNot(built, "smelt-a") },
            { assertContainsNot(built, "smelt-b") },
            { assertContains(built, "target") },
        )
    }

    @Test
    fun aMultiOutputRecipeProducingAnExcludedMaterialIsDropped() {
        val ore = Material("ore")
        val plate = Material("plate")
        val gear = Material("gear")
        val target = Material("target")
        fun recipe(
            name: String,
            ingredients: Map<Material, Double>,
            result: Map<Material, Double>,
        ) = Recipe(name, ingredients, result, time = 1.0, categories = setOf(Category.CRAFTING))

        val parsed =
            ParsedRecipe(
                emptyList(),
                listOf(
                    recipe("combo", mapOf(ore to 1.0), mapOf(plate to 1.0, gear to 1.0)),
                    recipe("target", mapOf(gear to 1.0), mapOf(target to 1.0)),
                ),
            )
        val result =
            Optimizer(
                parsed,
                setOf(Factory.ASSEMBLING_MACHINE_3, Factory.NO_FACTORY),
                excludedMaterials = setOf(plate),
            )[target]
        val built = result.factories.values.map { it.recipe.name }.toSet()
        val imported =
            result.factories.values
                .filter { Category.IMPORT in it.recipe.categories }
                .map { it.material }
                .toSet()

        assertAll(
            { assertContainsNot(built, "combo") },
            { assertContains(imported, gear) },
        )
    }

    @Test
    fun theTargetRecipeSurvivesAnExcludedByproduct() {
        val ore = Material("ore")
        val plate = Material("plate")
        val target = Material("target")
        fun recipe(
            name: String,
            ingredients: Map<Material, Double>,
            result: Map<Material, Double>,
        ) = Recipe(name, ingredients, result, time = 1.0, categories = setOf(Category.CRAFTING))

        val parsed =
            ParsedRecipe(
                emptyList(),
                listOf(recipe("target", mapOf(ore to 1.0), mapOf(target to 1.0, plate to 1.0))),
            )
        val result =
            Optimizer(
                parsed,
                setOf(Factory.ASSEMBLING_MACHINE_3, Factory.NO_FACTORY),
                excludedMaterials = setOf(plate),
            )[target]

        assertEquals("target", result.recipe.name)
        assertContains(result.factories.values.map { it.recipe.name }, "target")
    }

    @Test
    fun theTargetCannotBeExcluded() {
        val ore = Material("ore")
        val target = Material("target")
        fun recipe(
            name: String,
            ingredients: Map<Material, Double>,
            result: Map<Material, Double>,
        ) = Recipe(name, ingredients, result, time = 1.0, categories = setOf(Category.CRAFTING))

        val parsed =
            ParsedRecipe(
                emptyList(),
                listOf(recipe("target", mapOf(ore to 1.0), mapOf(target to 1.0))),
            )
        val result =
            Optimizer(
                parsed,
                setOf(Factory.ASSEMBLING_MACHINE_3, Factory.NO_FACTORY),
                excludedMaterials = setOf(target),
            )[target]

        assertEquals("target", result.recipe.name)
        assertContains(result.factories.values.map { it.recipe.name }, "target")
    }

    @Test
    fun anExcludedMaterialDoesNotCountTowardTheIntermediateLimit() {
        val ore = Material("ore")
        val plate = Material("plate")
        val gear = Material("gear")
        val target = Material("target")
        fun recipe(
            name: String,
            ingredients: Map<Material, Double>,
            result: Map<Material, Double>,
        ) = Recipe(name, ingredients, result, time = 1.0, categories = setOf(Category.CRAFTING))

        val parsed =
            ParsedRecipe(
                emptyList(),
                listOf(
                    recipe("smelt", mapOf(ore to 1.0), mapOf(plate to 1.0)),
                    recipe("gear", mapOf(plate to 1.0), mapOf(gear to 1.0)),
                    recipe("target", mapOf(gear to 1.0, plate to 1.0), mapOf(target to 1.0)),
                ),
            )
        val result =
            Optimizer(
                parsed,
                setOf(Factory.ASSEMBLING_MACHINE_3, Factory.NO_FACTORY),
                maxIntermediates = 1,
                excludedMaterials = setOf(gear),
            )[target]
        val built =
            result.factories.values
                .filter { it.factory != Factory.NO_FACTORY }
                .map { it.recipe.name }
                .toSet()

        assertAll(
            // gear is excluded, so the one remaining intermediate slot keeps the plate smelter
            { assertContains(built, "smelt") },
            { assertContainsNot(built, "gear") },
        )
    }

    @Test
    fun aTargetInACycleIsBuiltNotImported() {
        val target = Material("target")
        val byproduct = Material("byproduct")
        fun recipe(
            name: String,
            ingredients: Map<Material, Double>,
            result: Map<Material, Double>,
        ) = Recipe(name, ingredients, result, time = 1.0, categories = setOf(Category.CRAFTING))

        val parsed =
            ParsedRecipe(
                emptyList(),
                listOf(
                    recipe("forward", mapOf(target to 1.0), mapOf(byproduct to 1.0)),
                    recipe("back", mapOf(byproduct to 1.0), mapOf(target to 1.0)),
                ),
            )
        val result =
            Optimizer(parsed, setOf(Factory.ASSEMBLING_MACHINE_3, Factory.NO_FACTORY))[target]

        assertAll(
            // the cycle member may be imported, but the target itself must be produced
            { assertEquals("back", result.recipe.name) },
            { assertContains(result.factories.values.map { it.recipe.name }, "back") },
        )
    }
}
