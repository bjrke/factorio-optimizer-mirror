package de.bjrke.factorio.optimizer.jvm

import de.bjrke.factorio.optimizer.common.business.parseLua
import java.io.File

fun main() {
    println(
        parseLua(
            File(
                    "${System.getProperty("user.home")}/.steam/steam/steamapps/common/Factorio/data/base/prototypes/recipe.lua"
                )
                .readText()
        )
    )
}
