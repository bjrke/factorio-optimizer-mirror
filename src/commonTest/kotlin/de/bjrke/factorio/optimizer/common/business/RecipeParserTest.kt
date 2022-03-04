package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Category
import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.Recipe
import de.bjrke.factorio.optimizer.common.model.Subgroup
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe

class RecipeParserTest :
    FreeSpec({
        fun parse(s: String, mode: Mode = Mode.NORMAL) =
            parseRecipes(s = s, mode = mode, defaultRecipesProvider = { listOf() }).recipes

        "empty table should return empty recipe list" { parse("{}").shouldBe(listOf()) }

        "parse copper-plate" {
            parse(
                    """
                    {{
                    type = "recipe",
                    name = "copper-plate",
                    category = "smelting",
                    energy_required = 3.2,
                    ingredients = {{ "copper-ore", 1}},
                    result = "copper-plate"
                    }}
                    """
                )
                .shouldBe(
                    listOf(
                        Recipe(
                            name = "copper-plate",
                            ingredients = mapOf(Material("copper-ore") to 1.0),
                            result = mapOf(Material("copper-plate") to 1.0),
                            time = 3.2,
                            category = Category.SMELTING,
                            subgroup = Subgroup()
                        )
                    )
                )
        }

        "parse copper-plate wrapped with method call" {
            parse(
                    """
                    data:extend({{
                    type = "recipe",
                    name = "copper-plate",
                    category = "smelting",
                    energy_required = 3.2,
                    ingredients = {{ "copper-ore", 1}},
                    result = "copper-plate"
                    }})
                    """
                )
                .shouldBe(
                    listOf(
                        Recipe(
                            name = "copper-plate",
                            ingredients = mapOf(Material("copper-ore") to 1.0),
                            result = mapOf(Material("copper-plate") to 1.0),
                            time = 3.2,
                            category = Category.SMELTING,
                            subgroup = Subgroup()
                        )
                    )
                )
        }

        "parse uranium-processing" {
            parse(
                    """
                    {{
                    type = "recipe",
                    name = "uranium-processing",
                    energy_required = 12,
                    enabled = false,
                    category = "centrifuging",
                    ingredients = {{"uranium-ore", 10}},
                    icon = "__base__/graphics/icons/uranium-processing.png",
                    icon_size = 64, icon_mipmaps = 4,
                    subgroup = "raw-material",
                    order = "k[uranium-processing]", -- k ordering so it shows up after explosives which is j ordering
                    results =
                    {
                      {
                        name = "uranium-235",
                        probability = 0.007,
                        amount = 1
                      },
                      {
                        name = "uranium-238",
                        probability = 0.993,
                        amount = 1
                      }
                    }
                  }}
                  """
                )
                .shouldBe(
                    listOf(
                        Recipe(
                            name = "uranium-processing",
                            ingredients = mapOf(Material("uranium-ore") to 10.0),
                            result =
                                mapOf(
                                    Material("uranium-235") to 0.007,
                                    Material("uranium-238") to 0.993
                                ),
                            time = 12.0,
                            category = Category.CENTRIFUGING,
                            subgroup = Subgroup("raw-material")
                        )
                    )
                )
        }

        "parse iron-gear-wheel normal" {
            parse(
                    """
                    {{
                    type = "recipe",
                    name = "iron-gear-wheel",
                    normal =
                    {
                      ingredients = {{"iron-plate", 2}},
                      result = "iron-gear-wheel"
                    },
                    expensive =
                    {
                      ingredients = {{"iron-plate", 4}},
                      result = "iron-gear-wheel"
                    }
                    }}
                    """
                )
                .shouldBe(
                    listOf(
                        Recipe(
                            name = "iron-gear-wheel",
                            ingredients = mapOf(Material("iron-plate") to 2.0),
                            result = mapOf(Material("iron-gear-wheel") to 1.0),
                            time = 0.5,
                            category = Category.CRAFTING,
                            subgroup = Subgroup()
                        )
                    )
                )
        }

        "parse iron-gear-wheel expensive" {
            parse(
                    """
                    {{
                    type = "recipe",
                    name = "iron-gear-wheel",
                    normal =
                    {
                      ingredients = {{"iron-plate", 2}},
                      result = "iron-gear-wheel"
                    },
                    expensive =
                    {
                      ingredients = {{"iron-plate", 4}},
                      result = "iron-gear-wheel"
                    }
                    }}
                    """,
                    mode = Mode.EXPENSINVE
                )
                .shouldBe(
                    listOf(
                        Recipe(
                            name = "iron-gear-wheel",
                            ingredients = mapOf(Material("iron-plate") to 4.0),
                            result = mapOf(Material("iron-gear-wheel") to 1.0),
                            time = 0.5,
                            category = Category.CRAFTING,
                            subgroup = Subgroup()
                        )
                    )
                )
        }

        "parse all recipes" { parse(loadRecipe()).shouldNotBeEmpty() }
    })
