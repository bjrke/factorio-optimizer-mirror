package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.test.assertAll
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/** The material list the parser offers: no phantom item from a hidden or cheat recipe. */
class RecipeParserMaterialsTest {

    /**
     * Items that only exist inside hidden or cheat recipes and must never appear in the material
     * list. Base game items first, then the Space Age cheats; the absent ones are simply a no-op
     * for the other dump.
     */
    private val phantomMaterials =
        listOf(
            // the loaders and the starting pistol only have hidden recipes
            "loader",
            "fast-loader",
            "express-loader",
            "turbo-loader",
            "pistol",
            // creative/debug entities, tools and placeholders from hidden cheat recipes
            "blueprint",
            "blueprint-book",
            "bottomless-chest",
            "burner-generator",
            "coin",
            "deconstruction-planner",
            "electric-energy-interface",
            "electric-energy-interface-equipment",
            "empty-module-slot",
            "heat-interface",
            "infinity-cargo-wagon",
            "infinity-chest",
            "infinity-pipe",
            "item-unknown",
            "lane-splitter",
            "linked-belt",
            "linked-chest",
            "one-way-valve",
            "overflow-valve",
            "proxy-container",
            "science",
            "selection-tool",
            "simple-entity-with-force",
            "simple-entity-with-owner",
            "space-platform-hub",
            "top-up-valve",
            "upgrade-planner",
        )

    @Test
    fun materialsExcludeItemsFromDroppedHiddenRecipes() {
        val parsed =
            parsePrototypes(
                """
                {"recipe":{
                  "loader":{
                    "type":"recipe","name":"loader","hidden":true,
                    "ingredients":[{"type":"item","name":"iron-plate","amount":5}],
                    "results":[{"type":"item","name":"loader","amount":1}]},
                  "iron-gear-wheel":{
                    "type":"recipe","name":"iron-gear-wheel",
                    "ingredients":[{"type":"item","name":"iron-plate","amount":2}],
                    "results":[{"type":"item","name":"iron-gear-wheel","amount":1}]}
                }}
                """,
            )

        val names = parsed.materials.map(Material::name)
        assertAll(
            // the hidden loader recipe is dropped, so no loader-ish item may be offered
            {
                assertEquals(
                    emptyList(),
                    names.filter { it.contains("loader") },
                    "hidden loader recipe leaked a material",
                )
            },
            // only produced materials are offered, and the gear wheel is produced
            { assertContains(names, "iron-gear-wheel", "produced material is missing") },
            // iron-plate is only an ingredient; no recipe yields it, so it has no factory to run
            {
                assertEquals(
                    emptyList(),
                    names.filter { it == "iron-plate" },
                    "ingredient-only material leaked",
                )
            },
        )
    }

    /**
     * Items that exist only inside hidden or cheat recipes the parser drops (loaders, planners,
     * creative entities, ...) must never be offered: they have no surface or factory to produce
     * them. This is a curated list, so a wrong pseudo recipe adds a failure instead of hiding.
     */
    @Test
    fun realPrototypeDumpsDoNotListPhantomMaterials() =
        assertTableOf(
            "base" to RecipeProvider.forActiveMods(false),
            "space-age" to RecipeProvider.forActiveMods(),
        ) { (label, parsed) ->
            assertEquals(
                emptyList(),
                parsed.materials.map(Material::name).filter { it in phantomMaterials },
                "$label lists phantom materials",
            )
        }

    /** The agricultural tower recipe puts wood back as a producible Space Age material. */
    @Test
    fun spaceAgePrototypeDumpKeepsHarvestableWood() =
        assertContains(
            RecipeProvider.forActiveMods().materials.map(Material::name),
            "wood",
            "wood must be producible by the agricultural tower",
        )
}
