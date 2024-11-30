package de.bjrke.factorio.optimizer.common.business

import de.bjrke.factorio.optimizer.common.model.Material
import de.bjrke.factorio.optimizer.common.model.ParsedRecipe
import de.bjrke.factorio.optimizer.common.model.Recipe
import de.bjrke.factorio.optimizer.common.model.Subgroup
import de.bjrke.factorio.optimizer.common.model.category

private open class WrappedTable(val table: LuaTable) {

    val map by lazy {
        table.elements.filterIsInstance<LuaTableEntry>().associate { it.key to it.value }
    }

    operator fun get(field: String) = map[field]

    operator fun get(index: Int) = if (index < table.elements.size) table.elements[index] else null

    fun getElementsAsTables(): List<WrappedTable> =
        table.elements.filterIsInstance<LuaTable>().map(::WrappedTable)

    fun listToPair(): Pair<String, Double>? {
        val name = getString(0) ?: return null
        val amount = getNumber(1) ?: return null
        return name to amount
    }

    private inline fun <reified L, R> getAs(field: String, f: (L) -> R) =
        this[field]?.let { it as? L }?.let(f)

    fun getString(field: String) = getAs(field, LuaString::s)

    fun getBoolean(field: String) = getAs(field, LuaBoolean::b)

    fun getNumber(field: String) = getAs(field, LuaNumber::n)

    fun getTable(field: String) = getAs(field, ::WrappedTable)

    fun getTableList(field: String) =
        getAs(field) { table: LuaTable -> WrappedTable(table).getElementsAsTables() } ?: listOf()

    private inline fun <reified L, R> getAs(index: Int, f: (L) -> R) =
        this[index]?.let { it as? L }?.let(f)

    fun getString(index: Int) = getAs(index, LuaString::s)

    fun getBoolean(index: Int) = getAs(index, LuaBoolean::b)

    fun getNumber(index: Int) = getAs(index, LuaNumber::n)

    fun getTable(index: Int) = getAs(index, ::WrappedTable)
}

enum class Mode(val fieldName: String) {
    NORMAL("normal"),
    EXPENSINVE("expensive")
}

private const val DEFAULT_PROBABILITY = 1.0
private const val DEFAULT_AMOUNT = 1.0
private const val DEFAULT_TYPE = "item"

private data class RecipeResult(
    val name: String,
    val amount: Double = DEFAULT_AMOUNT,
    val probability: Double = DEFAULT_PROBABILITY
)

private data class RecipeIngredient(
    val name: String,
    val amount: Double,
    val type: String = DEFAULT_TYPE
)

private class NestedRecipe(val table: WrappedTable) {

    fun getResults() = sequence {
        table.getString("result")?.also {
            yield(
                RecipeResult(name = it, amount = table.getNumber("result_count") ?: DEFAULT_AMOUNT)
            )
        }
        for (result in table.getTableList("results")) {
            result.listToPair()?.also { (name, amount) -> yield(RecipeResult(name, amount)) }
            yield(
                RecipeResult(
                    name = result.getString("name") ?: continue,
                    amount = result.getNumber("amount") ?: DEFAULT_AMOUNT,
                    probability = result.getNumber("probability") ?: DEFAULT_PROBABILITY
                )
            )
        }
    }

    fun getIngredients() = sequence {
        for (result in table.getTableList("ingredients")) {
            result.listToPair()?.also { (name, amount) -> yield(RecipeIngredient(name, amount)) }
            yield(
                RecipeIngredient(
                    name = result.getString("name") ?: continue,
                    amount = result.getNumber("amount") ?: DEFAULT_AMOUNT,
                    type = result.getString("type") ?: DEFAULT_TYPE
                )
            )
        }
    }

    fun getEnergyRequired() = table.getNumber("energy_required")
}

expect fun loadRecipe(): String

fun parseRecipes(
    s: String = loadRecipe(),
    mode: Mode = Mode.NORMAL,
    defaultRecipesProvider: ((String) -> Material) -> List<Recipe> = ::defaultRecipes
): ParsedRecipe {

    class LuaRecipe(val table: WrappedTable) {

        fun getSubgroup() = table.getString("subgroup")

        fun getType() = table.getString("type")

        fun getResults() = getNested(NestedRecipe::getResults)

        fun getIngredients() = getNested(NestedRecipe::getIngredients)

        private val thisRecipe = NestedRecipe(table)

        private val nestedRecipe = table.getTable(mode.fieldName)?.let(::NestedRecipe)

        private fun <T> getNested(f: (NestedRecipe) -> Sequence<T>) = sequence {
            yieldAll(f(thisRecipe))
            nestedRecipe?.also { yieldAll(f(it)) }
        }

        fun getName() = table.getString("name")

        fun getEnergyRequired() =
            nestedRecipe?.getEnergyRequired() ?: thisRecipe.getEnergyRequired()

        fun getCategory() = table.getString("category")
    }

    val materialsBuilder = mutableMapOf<String, Material>()
    fun material(name: String) = materialsBuilder.getOrPut(name) { Material.of(name) }

    val subgroups = mutableMapOf<String, Subgroup>()
    val noSubGroup = Subgroup()
    fun subGroup(name: String?) =
        when (name) {
            null -> noSubGroup
            else -> subgroups.getOrPut(name) { Subgroup(name) }
        }

    val recipes =
        sequence {
                for (luaRecipe in
                    WrappedTable(parseLua(s)).getElementsAsTables().map(::LuaRecipe)) {
                    if (luaRecipe.getType() != "recipe") continue

                    val subgroupName = luaRecipe.getSubgroup()
                    when (subgroupName) {
                        "fill-barrel",
                        "empty-barrel" -> continue
                    }

                    val result =
                        luaRecipe
                            .getResults()
                            .map { material(it.name) to it.amount * it.probability }
                            .toMap()

                    val ingredients =
                        luaRecipe.getIngredients().map { material(it.name) to it.amount }.toMap()

                    yield(
                        Recipe(
                            name = luaRecipe.getName()!!,
                            ingredients = ingredients,
                            result = result,
                            time = luaRecipe.getEnergyRequired() ?: 0.5,
                            subgroup = subGroup(subgroupName),
                            category = category(luaRecipe.getCategory() ?: "crafting")!!
                        )
                    )
                }

                yieldAll(defaultRecipesProvider(::material))
            }
            .sortedBy(Recipe::name)
            .toList()

    return ParsedRecipe(
        recipes = recipes,
        materials = materialsBuilder.values.sortedBy(Material::name).toList(),
    )
}
