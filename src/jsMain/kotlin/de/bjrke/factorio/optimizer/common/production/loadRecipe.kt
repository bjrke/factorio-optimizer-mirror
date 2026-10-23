package de.bjrke.factorio.optimizer.common.production

internal actual fun loadPrototypeDump(spaceAge: Boolean): String =
    prototypeDumps[if (spaceAge) "space-age" else "base"] as String

@JsModule("./prototypes/loader.js") @JsNonModule private external val prototypeDumps: dynamic
