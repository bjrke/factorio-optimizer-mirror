package de.bjrke.factorio.optimizer.common.business

actual fun loadRecipe(): String {
    return recipeLua
}

@JsModule("./recipes/recipe.lua") @JsNonModule private external val recipeLua: dynamic
