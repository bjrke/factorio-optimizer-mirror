package de.bjrke.test

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class AssertionsTest {

    @Test
    fun absentElementsPassForEveryContainer() =
        assertAll(
            { assertContainsNot(listOf("a", "b"), "c") },
            { assertContainsNot(setOf("a", "b"), "c") },
            { assertContainsNot(mapOf("a" to 1), "b") },
            { assertContainsNot(arrayOf("a", "b"), "c") },
            { assertContainsNot(sequenceOf("a", "b"), "c") },
            { assertContainsNot("abc", "d") },
        )

    @Test
    fun presentElementsFailWithTheFullContents() =
        assertAll(
            { assertFailureContains({ assertContainsNot(listOf("a", "b"), "b") }, "[a, b]") },
            {
                assertFailureContains(
                    { assertContainsNot(mapOf("a" to 1, "b" to 2), "b") },
                    "[a, b]",
                )
            },
            { assertFailureContains({ assertContainsNot(arrayOf("a", "b"), "b") }, "[a, b]") },
            { assertFailureContains({ assertContainsNot("abc", "b") }, "abc") },
        )

    @Test
    fun theMessageIsKept() {
        val failure =
            assertFailsWith<AssertionError> {
                assertContainsNot(listOf("a"), "a", "expected no a")
            }
        assertContains(failure.message.orEmpty(), "expected no a")
    }

    @Test
    fun stringsCanIgnoreCase() {
        assertContainsNot("abc", "B")
        assertFailsWith<AssertionError> {
            assertContainsNot("abc", "B", ignoreCase = true)
        }
    }

    private fun assertFailureContains(assertion: () -> Unit, expected: String) {
        val failure = assertFailsWith<AssertionError> { assertion() }
        assertContains(failure.message.orEmpty(), expected)
    }
}
