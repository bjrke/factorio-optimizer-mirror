package de.bjrke.test

import kotlin.test.fail

fun assertAll(vararg assertions: () -> Unit) = assertAll(assertions.toList())

fun assertAll(assertions: Iterable<() -> Unit>) {
    assertAll(assertions.toList())
}

fun assertAll(assertionList: List<() -> Unit>) {
    val failures = assertionList.mapNotNull { runCatching(it).exceptionOrNull() }
    if (failures.isNotEmpty()) {
        throw AssertionError(
            "${failures.size} of ${assertionList.size} assertions failed:\n\n" +
                failures.withIndex().joinToString("\n\n") { (i, failure) ->
                    "${i + 1}) ${shortMessage(failure)}"
                }
        )
    }
}

fun <T> assertTableOf(vararg cases: T, assertion: (T) -> Unit) =
    assertTable(cases.asIterable(), assertion)

fun <T> assertTable(cases: Iterable<T>, assertion: (T) -> Unit) =
    assertAll(
        cases.map {
            {
                val failure = runCatching { assertion(it) }.exceptionOrNull()
                if (failure != null) {
                    fail("case: $it\n${failure.message ?: failure.toString()}", failure)
                }
            }
        }
    )

internal expect fun shortMessage(failure: Throwable): String
