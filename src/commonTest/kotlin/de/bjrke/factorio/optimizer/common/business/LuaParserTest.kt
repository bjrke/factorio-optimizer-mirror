package de.bjrke.factorio.optimizer.common.business

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class LuaParserTest :
    DescribeSpec({
        describe("should parse to table element") {
            withData(
                "{}" to listOf(),
                "{ }" to listOf(),
                "{5}" to listOf(LuaNumber(5.0)),
                "{   0.1  }" to listOf(LuaNumber(0.1)),
                "{\"foo\"}" to listOf(LuaString("foo")),
                "{ \"bar\"   }" to listOf(LuaString("bar")),
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
                "stuff around { foo = \"bar\" } doesn't matter" to
                    listOf(LuaTableEntry("foo", LuaString("bar"))),
                "{ \"xxx\", 1, true, foo = \"bar\" }" to
                    listOf(
                        LuaString("xxx"),
                        LuaNumber(1.0),
                        LuaBoolean(true),
                        LuaTableEntry("foo", LuaString("bar"))
                    )
            ) { (s, expected) -> parseLua(s).elements.shouldBe(expected) }
        }

        describe("should throw unexpected token exceptions") {
            withData(
                "{,",
                "{,}",
                "{   falsex   }",
                "{   false false   }",
                "{   fals  }",
                "{   FALSE  }",
                "{ 1. }"
            ) { s -> shouldThrow<UnexpectedTokenException> { parseLua(s) } }
        }

        describe("should throw unexpected end of file exception") {
            withData("{", "{{}", "{\"}", "{foo =", "{1") { s: String ->
                shouldThrow<UnexpectedEndOfFile> { parseLua(s) }
            }
        }
    })
