package de.bjrke.factorio.optimizer.common.business

const val BASE_MOD = "base"
const val SPACE_AGE_MOD = "space-age"

val defaultMods = setOf(BASE_MOD, SPACE_AGE_MOD)

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
