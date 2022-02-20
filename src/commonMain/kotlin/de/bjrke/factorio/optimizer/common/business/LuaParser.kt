package de.bjrke.factorio.optimizer.common.business

import kotlin.math.min

private val TABLE_KEY_REGEX = "^(\\w+)\\s*=\\s*".toRegex()
private val NUMBER_REGEX = "^\\d+(\\.\\d+)?".toRegex()

interface LuaNode

interface LuaTableElement : LuaNode

interface LuaObject : LuaTableElement

data class LuaTableEntry(val key: String, val value: LuaObject) : LuaTableElement

data class LuaString(val s: String) : LuaObject

data class LuaNumber(val n: Double) : LuaObject

data class LuaBoolean(val b: Boolean) : LuaObject

data class LuaTable(val elements: List<LuaTableElement>) : LuaObject

data class UnexpectedTokenException(val context: String, val offset: Int) :
    Exception("Unexpected token at $offset $context}")

data class UnexpectedEndOfFile(val offset: Int) : Exception("Unexpected end of file at $offset")

private class LuaParser(val complete: String) {

    private val maxOffset = complete.length

    private inner class Position(val offset: Int) {

        init {
            checkOffset(offset)
        }

        val substr by lazy {
            checkOffset(offset + 1)
            complete.substring(offset)
        }

        private fun checkOffset(offsetToCheck: Int) {
            if (offsetToCheck > maxOffset) throw UnexpectedEndOfFile(offsetToCheck)
        }

        val next by lazy { Position(offset + 1) }

        val current by lazy { substr[0] }
    }

    private tailrec fun Position.advance(n: Int): Position =
        when (n) {
            0 -> this
            1 -> next
            else -> next.advance(n - 1)
        }

    private fun Position.startsWith(expected: String) = substr.startsWith(expected)

    private fun Position.startsWith(expected: Char) = current == expected

    private fun Position.find(regex: Regex) = regex.find(substr)

    private tailrec fun Position.skipWhitespace(): Position =
        when {
            current.isWhitespace() -> next.skipWhitespace()
            !startsWith("--") -> this
            else -> advanceUntil('\n').skipWhitespace()
        }

    private tailrec fun Position.advanceUntil(ch: Char): Position =
        when {
            startsWith(ch) -> this
            else -> next.advanceUntil(ch)
        }

    private fun Position.luaString(): Pair<Position, LuaString>? {

        tailrec fun Position.findStringEnd(sb: StringBuilder): Pair<Position, LuaString> =
            when {
                startsWith("\\a") -> advance(2).findStringEnd(sb.append('\u0007'))
                startsWith("\\b") -> advance(2).findStringEnd(sb.append('\b'))
                startsWith("\\f") -> advance(2).findStringEnd(sb.append('\u000C'))
                startsWith("\\n") -> advance(2).findStringEnd(sb.append('\n'))
                startsWith("\\r") -> advance(2).findStringEnd(sb.append('\r'))
                startsWith("\\t") -> advance(2).findStringEnd(sb.append('\t'))
                startsWith("\\v") -> advance(2).findStringEnd(sb.append('\u000B'))
                startsWith("\\\\") -> advance(2).findStringEnd(sb.append('\\'))
                startsWith("\\\"") -> advance(2).findStringEnd(sb.append('"'))
                startsWith("\\'") -> advance(2).findStringEnd(sb.append('\''))
                startsWith("\\[") -> advance(2).findStringEnd(sb.append('['))
                startsWith("\\]") -> advance(2).findStringEnd(sb.append(']'))
                startsWith('"') -> LuaString(sb.toString()).withPos(next)
                else -> next.findStringEnd(sb.append(current))
            }

        return when {
            startsWith('"') -> next.findStringEnd(StringBuilder())
            else -> null
        }
    }

    private fun Position.luaNumber() =
        find(NUMBER_REGEX)?.run { LuaNumber(value.toDouble()).withPos(advance(value.length)) }

    private fun <T : LuaNode> T.withPos(pos: Position) = pos to this

    private fun Position.luaTable(): Pair<Position, LuaTable>? {

        fun Position.tableEntry() =
            luaObject()
                ?: find(TABLE_KEY_REGEX)?.let { match ->
                    match.groups[1]?.value?.let { key ->
                        advance(match.value.length).luaObject()?.let { (p, n) ->
                            LuaTableEntry(key, n).withPos(p)
                        }
                    }
                }
                    ?: unexpectedToken()

        tailrec fun Position.tableElements(
            elements: List<LuaTableElement>
        ): Pair<Position, LuaTable> =
            next.skipWhitespace().run {
                if (startsWith('}')) return LuaTable(elements).withPos(next)
                val (pos, node) = tableEntry()
                pos.skipWhitespace().run {
                    when {
                        startsWith('}') -> LuaTable(elements + node).withPos(next)
                        startsWith(',') -> return tableElements(elements + node)
                        else -> unexpectedToken()
                    }
                }
            }

        return when {
            startsWith('{') -> this.tableElements(listOf())
            else -> null
        }
    }

    private fun Position.luaBoolean() =
        when {
            startsWith("true") -> LuaBoolean(true).withPos(advance(4))
            startsWith("false") -> LuaBoolean(false).withPos(advance(5))
            else -> null
        }

    private fun Position.luaObject() = luaTable() ?: luaString() ?: luaNumber() ?: luaBoolean()

    private fun <T> Position.unexpectedToken(): T =
        throw UnexpectedTokenException(substr.substring(0 until min(substr.length, 100)), offset)

    fun result() =
        Position(0).run {
            substr.indexOf('{').let {
                when {
                    it < 0 -> unexpectedToken()
                    else -> advance(it).luaTable()!!.second
                }
            }
        }
}

fun parseLua(s: String): LuaTable = LuaParser(s).result()
