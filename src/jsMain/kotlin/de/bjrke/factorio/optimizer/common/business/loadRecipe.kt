package de.bjrke.factorio.optimizer.common.business

actual fun loadMods() = parseModsTxt(modsTxt)

actual fun loadRecipeFiles() =
    recipeFiles.unsafeCast<Array<Array<String>>>().associate { it[0] to it[1] }

@JsModule("./recipes/mods.txt") @JsNonModule private external val modsTxt: String

@JsModule("./recipes/loader.js") @JsNonModule private external val recipeFiles: dynamic
