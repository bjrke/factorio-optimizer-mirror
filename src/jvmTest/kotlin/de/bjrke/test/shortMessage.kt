package de.bjrke.test

internal actual fun shortMessage(failure: Throwable): String {
    val message = failure.message ?: failure.toString()
    val frame =
        failure.stackTrace.firstOrNull {
            !it.className.startsWith("kotlin.") &&
                !it.className.startsWith("AssertionsKt") &&
                !it.className.startsWith("de.bjrke.test.")
        }
    return message + (frame?.let { "\n   at $it" } ?: "")
}
