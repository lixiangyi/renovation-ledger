package com.renovation.ledger.dsl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DslSmokeTest {

    @Test
    fun conditionalYesNo() {
        val label = true yes "超支" nonNull "正常"
        assertEquals("超支", label)
        val other = false yes "超支" nonNull "正常"
        assertEquals("正常", other)
    }

    @Test
    fun gsonBuilder() {
        val obj = gson {
            "name" with "厨房"
            "budgetFen" with 10000
            "tags" gsonArray {
                with("硬装")
                with("水电")
            }
        }
        assertEquals("厨房", obj.get("name").asString)
        assertEquals(10000, obj.get("budgetFen").asInt)
        assertEquals(2, obj.getAsJsonArray("tags").size())
    }

    @Test
    fun regexBuilder() {
        val r = regex {
            startOfLine()
            digit()
            literally(".")
            2 times { digit() }
            endOfLine()
        }
        assertTrue(r.matches("1.23"))
        assertTrue(!r.matches("12.3"))
    }

    @Test
    fun jsonToMap() {
        val map = """{"a":1,"b":"x"}""".jsonToMap<Any?>()
        assertEquals(1.0, map["a"])
        assertEquals("x", map["b"])
    }

    @Test
    fun catchExceptionFallback() {
        val value = catchException(onError = { -1 }) {
            error("boom")
        }
        assertEquals(-1, value)
    }
}
