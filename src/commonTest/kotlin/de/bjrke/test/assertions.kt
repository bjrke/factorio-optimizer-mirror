package de.bjrke.test

import kotlin.test.fail

/** Shared absolute delta for floating-point `assertEquals` in tests. */
internal const val TOLERANCE = 1e-9

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
                },
        )
    }
}

fun <T> assertContainsNot(iterable: Iterable<T>, element: T, message: String? = null) {
    if (iterable.contains(element)) {
        fail(containsNotMessage(message, "collection", element, iterable))
    }
}

fun <K, V> assertContainsNot(map: Map<K, V>, key: K, message: String? = null) {
    if (map.containsKey(key)) {
        fail(containsNotMessage(message, "map keys", key, map.keys))
    }
}

fun <T> assertContainsNot(sequence: Sequence<T>, element: T, message: String? = null) =
    assertContainsNot(sequence.toList(), element, message)

fun <T> assertContainsNot(array: Array<T>, element: T, message: String? = null) {
    if (array.contains(element)) {
        fail(containsNotMessage(message, "array", element, array.toList()))
    }
}

fun assertContainsNot(
    charSequence: CharSequence,
    other: CharSequence,
    ignoreCase: Boolean = false,
    message: String? = null,
) {
    if (charSequence.contains(other, ignoreCase)) {
        fail(containsNotMessage(message, "string", other, charSequence))
    }
}

private fun containsNotMessage(
    message: String?,
    what: String,
    element: Any?,
    contents: Any?,
): String = buildString {
    if (message != null) appendLine(message)
    append("Expected the $what to not contain $element, but it does: $contents")
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
        },
    )

internal expect fun shortMessage(failure: Throwable): String
