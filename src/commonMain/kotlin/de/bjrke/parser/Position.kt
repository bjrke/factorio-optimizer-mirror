package de.bjrke.parser

import kotlin.math.min

data class UnexpectedTokenException(val context: String, val offset: Int) :
    Exception("Unexpected token at $offset $context}")

data class UnexpectedEndOfFile(val offset: Int) : Exception("Unexpected end of file at $offset")

interface Position {

    val next: Position

    val current: Char

    fun <T> Position.unexpectedToken(): T
}

fun Position.isWhitespace() = current.isWhitespace()

fun Position.isLetter() = current.isLetter()

fun Position.isDigit() = current.isDigit()

fun Position.expect(ch: Char) = expect { isChar(ch) }

fun Position.expect(p: Position.() -> Boolean): Position =
    when {
        p(this) -> next
        else -> unexpectedToken()
    }

fun Position.appendTo(sb: StringBuilder): Position {
    sb.append(current)
    return next
}

tailrec fun Position.advance(n: Int): Position =
    when (n) {
        0 -> this
        1 -> next
        else -> next.advance(n - 1)
    }

fun Position.isString(expected: String) = expected.isNotEmpty() && isString(expected, 0)

private tailrec fun Position.isString(expected: String, offset: Int): Boolean =
    when {
        offset == expected.length -> true
        !isChar(expected[offset]) -> false
        else -> next.isString(expected, offset + 1)
    }

fun Position.isChar(expected: Char) = current == expected

tailrec fun Position.advanceUntil(ch: Char): Position =
    when {
        isChar(ch) -> this
        else -> next.advanceUntil(ch)
    }

private data class Wrapper(val complete: String) {

    private val maxOffset = complete.length

    inner class PositionImpl(val offset: Int) : Position {

        init {
            checkOffset(offset)
        }

        private fun checkOffset(offsetToCheck: Int) {
            if (offsetToCheck > maxOffset) throw UnexpectedEndOfFile(offsetToCheck)
        }

        override val next by lazy { PositionImpl(offset + 1) }

        override val current by lazy {
            checkOffset(offset + 1)
            complete[offset]
        }

        private fun errorContextStr() =
            complete.substring(offset until min(complete.length, offset + 100))

        override fun <T> Position.unexpectedToken() =
            throw UnexpectedTokenException(errorContextStr(), offset)
    }
}

fun parse(s: String, offset: Int = 0): Position {
    return Wrapper(s).PositionImpl(offset)
}
