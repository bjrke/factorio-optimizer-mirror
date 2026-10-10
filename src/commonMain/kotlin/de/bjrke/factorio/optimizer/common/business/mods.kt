package de.bjrke.factorio.optimizer.common.business

data class ModInfo(val name: String, val dependencies: List<String>, val files: List<String>)

fun parseModsTxt(s: String) =
    s.lines()
        .filter { it.isNotBlank() }
        .map { line ->
            val parts = line.split("\t")
            ModInfo(
                name = parts[0],
                dependencies = parts.getOrElse(1) { "" }.split(",").filter { it.isNotBlank() },
                files = parts.getOrElse(2) { "" }.split(";").filter { it.isNotBlank() },
            )
        }

expect fun loadMods(): List<ModInfo>

expect fun loadRecipeFiles(): Map<String, String>
