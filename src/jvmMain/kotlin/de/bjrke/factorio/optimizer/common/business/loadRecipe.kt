package de.bjrke.factorio.optimizer.common.business

actual fun loadMods() = parseModsTxt(loadResource("/recipes/mods.txt").readText())

actual fun loadRecipeFiles() =
    loadMods()
        .flatMap { mod ->
            mod.files.map { file -> file to loadResource("/recipes/$file").readText() }
        }
        .toMap()

private fun loadResource(s: String) = object {}.javaClass.getResource(s)!!
