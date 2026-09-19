package de.bjrke.test

internal actual fun shortMessage(failure: Throwable): String = failure.message ?: failure.toString()
