package de.bjrke.factorio.optimizer.common.business

import de.bjrke.parser.UnexpectedEndOfFile
import de.bjrke.parser.UnexpectedTokenException
import de.bjrke.test.assertTableOf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class LuaParserTest {

    @Test
    fun shouldParseToTableElement() =
        assertTableOf(
            "{}" to listOf(),
            "{ }" to listOf(),
            "{5}" to listOf(LuaNumber(5.0)),
            "{   0.1  }" to listOf(LuaNumber(0.1)),
            "{\"foo\"}" to listOf(LuaString("foo")),
            "{ \"bar\"   }" to listOf(LuaString("bar")),
            "{ \"\\\\\\\"\"   }" to listOf(LuaString("\\\"")),
            "{true}" to listOf(LuaBoolean(true)),
            "{   false   }" to listOf(LuaBoolean(false)),
            "{{}}" to listOf(LuaTable(listOf())),
            "{{   {}   }}" to listOf(LuaTable(listOf(LuaTable(listOf())))),
            "{foo=\"bar\"}" to listOf(LuaTableEntry("foo", LuaString("bar"))),
            "{ xxx  = 1.4 }" to listOf(LuaTableEntry("xxx", LuaNumber(1.4))),
            "{ 1, 2,}" to listOf(LuaNumber(1.0), LuaNumber(2.0)),
            """
            {
                foo = "bar" --with comment
            }
            """ to
                listOf(LuaTableEntry("foo", LuaString("bar"))),
            """
            {
                foo = "bar" --with comment
                -- more comments
            }
            """ to
                listOf(LuaTableEntry("foo", LuaString("bar"))),
            "stuff around { foo = \"bar\" } doesn't matter" to
                listOf(LuaTableEntry("foo", LuaString("bar"))),
            "{ \"xxx\", 1, true, foo = \"bar\" }" to
                listOf(
                    LuaString("xxx"),
                    LuaNumber(1.0),
                    LuaBoolean(true),
                    LuaTableEntry("foo", LuaString("bar"))
                ),
            "{ 1. }" to listOf(LuaNumber(1.0))
        ) { (s, expected) ->
            assertContentEquals(expected, parseLua(s).elements)
        }

    @Test
    fun shouldThrowUnexpectedTokenExceptions() =
        assertTableOf(
            "{,}",
            "{   falsex   }",
            "{   false false   }",
            "{   0.1.2  }",
            "{   fals  }",
            "{  foo foo = \"bar\"  }",
            "{   FALSE  }"
        ) {
            assertFailsWith<UnexpectedTokenException> { parseLua(it) }
        }

    @Test
    fun shouldThrowUnexpectedEndOfFileException() =
        assertTableOf("{,", "{", "{{}", "{\"}", "{foo =", "{1") {
            assertFailsWith<UnexpectedEndOfFile> { parseLua(it) }
        }
}
