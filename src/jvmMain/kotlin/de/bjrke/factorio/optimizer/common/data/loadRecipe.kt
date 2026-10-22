package de.bjrke.factorio.optimizer.common.data

internal actual fun loadPrototypeDump(spaceAge: Boolean): String =
    loadResource("/prototypes/${if (spaceAge) "space-age" else "base"}.json").readText()

private fun loadResource(s: String) = object {}.javaClass.getResource(s)!!
