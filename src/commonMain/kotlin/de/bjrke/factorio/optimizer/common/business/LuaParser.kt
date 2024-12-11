package de.bjrke.factorio.optimizer.common.business

import de.bjrke.parser.*

interface LuaNode

interface LuaTableElement : LuaNode

interface LuaObject : LuaTableElement

data class LuaTableEntry(val key: String, val value: LuaObject) : LuaTableElement

data class LuaString(val s: String) : LuaObject

data class LuaNumber(val n: Double) : LuaObject

data class LuaBoolean(val b: Boolean) : LuaObject

data class LuaTable(val elements: List<LuaTableElement>) : LuaObject

private tailrec fun Position.skipWhitespace(): Position =
    when {
        isWhitespace() -> next.skipWhitespace()
        !isString("--") -> this
        else -> advanceUntil('\n').skipWhitespace()
    }

private fun Position.luaString(): Pair<Position, LuaString>? {
    if (!isChar('"')) return null

    val sb = StringBuilder()

    fun Position.found(ch: Char): Position {
        sb.append(ch)
        return advance(2)
    }

    tailrec fun Position.findStringEnd(): Pair<Position, LuaString> =
        when {
            isString("\\a") -> found('\u0007').findStringEnd()
            isString("\\b") -> found('\b').findStringEnd()
            isString("\\f") -> found('\u000C').findStringEnd()
            isString("\\n") -> found('\n').findStringEnd()
            isString("\\r") -> found('\r').findStringEnd()
            isString("\\t") -> found('\t').findStringEnd()
            isString("\\v") -> found('\u000B').findStringEnd()
            isString("\\\\") -> found('\\').findStringEnd()
            isString("\\\"") -> found('"').findStringEnd()
            isString("\\'") -> found('\'').findStringEnd()
            isString("\\[") -> found('[').findStringEnd()
            isString("\\]") -> found(']').findStringEnd()
            isChar('"') -> next to LuaString(sb.toString())
            else -> appendTo(sb).findStringEnd()
        }

    return next.findStringEnd()
}

private fun Position.luaNumber(): Pair<Position, LuaNumber>? {
    val sb = StringBuilder()

    tailrec fun Position.dotFound(): Position =
        when {
            isDigit() -> appendTo(sb).dotFound()
            else -> this
        }

    tailrec fun Position.digitFound(): Position =
        when {
            isDigit() -> appendTo(sb).digitFound()
            isChar('.') -> appendTo(sb).dotFound()
            else -> this
        }

    val pos = digitFound()
    return when {
        sb.isEmpty() -> null
        else -> pos to LuaNumber(sb.toString().toDouble())
    }
}

private fun Position.luaTable(): Pair<Position, LuaTable>? {

    fun Position.keyValue(): Pair<Position, LuaTableEntry>? {
        if (!isLetter()) return null

        val sb = StringBuilder()

        tailrec fun Position.appendMoreLettersAndDigits(): Position =
            when {
                isLetter() || isDigit() || isChar('_') -> appendTo(sb).appendMoreLettersAndDigits()
                else -> skipWhitespace().expect('=')
            }

        val (pos, luaObject) =
            appendTo(sb).appendMoreLettersAndDigits().skipWhitespace().luaObject()
                ?: unexpectedToken()
        return pos to LuaTableEntry(sb.toString(), luaObject)
    }

    tailrec fun Position.tableElements(elements: List<LuaTableElement>): Pair<Position, LuaTable> {
        if (isChar('}')) return next to LuaTable(elements)
        val (p, node) = (luaObject() ?: keyValue() ?: unexpectedToken())
        val pos = p.skipWhitespace()
        return when {
            pos.isChar('}') -> pos.next to LuaTable(elements + node)
            pos.isChar(',') -> pos.next.skipWhitespace().tableElements(elements + node)
            else -> pos.unexpectedToken()
        }
    }

    return when {
        isChar('{') -> next.skipWhitespace().tableElements(listOf())
        else -> null
    }
}

private fun Position.luaBoolean() =
    when {
        isString("true") -> advance(4) to LuaBoolean(true)
        isString("false") -> advance(5) to LuaBoolean(false)
        else -> null
    }

private fun Position.luaObject() = luaTable() ?: luaString() ?: luaNumber() ?: luaBoolean()

fun findLastCurlyBlock(s: String): Int? {
    var result: Int? = null
    var start = 0
    var count = 0
    for (i in s.indices) {
        when (s[i]) {
            '{' -> {
                if (count == 0) {
                    start = i
                }
                count++
            }
            '}' -> {
                count--
                if (count == 0) {
                    result = start
                }
            }
        }
    }
    return result
}

fun parseLua(s: String): LuaTable {
    val lastCurlyBlock = findLastCurlyBlock(s) ?: throw UnexpectedEndOfFile(s.length)
    return parse(s, lastCurlyBlock).run { luaTable()?.second ?: unexpectedToken() }
}
