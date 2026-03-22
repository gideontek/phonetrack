package com.gideontek.phonetrack

import org.junit.Assert.*
import org.junit.Test

class SmsCommandParserTest {

    @Test
    fun `no flags returns defaults`() {
        val result = SmsCommandParser.parseSubscribe(emptyList())
        assertNotNull(result)
        assertEquals(200, result!!.dist)
        assertEquals(15, result.freq)
        assertEquals(4, result.hours)
    }

    @Test
    fun `all flags explicit`() {
        val result = SmsCommandParser.parseSubscribe(
            listOf("--dist", "500", "--freq", "10", "--hours", "8")
        )
        assertNotNull(result)
        assertEquals(500, result!!.dist)
        assertEquals(10, result.freq)
        assertEquals(8, result.hours)
    }

    @Test
    fun `any order`() {
        val result = SmsCommandParser.parseSubscribe(
            listOf("--hours", "2", "--dist", "300", "--freq", "5")
        )
        assertNotNull(result)
        assertEquals(300, result!!.dist)
        assertEquals(5, result.freq)
        assertEquals(2, result.hours)
    }

    @Test
    fun `freq zero returns null`() {
        assertNull(SmsCommandParser.parseSubscribe(listOf("--freq", "0")))
    }

    @Test
    fun `freq negative returns null`() {
        assertNull(SmsCommandParser.parseSubscribe(listOf("--freq", "-1")))
    }

    @Test
    fun `missing value after dist flag returns null`() {
        assertNull(SmsCommandParser.parseSubscribe(listOf("--dist")))
    }

    @Test
    fun `missing value after freq flag returns null`() {
        assertNull(SmsCommandParser.parseSubscribe(listOf("--freq")))
    }

    @Test
    fun `missing value after hours flag returns null`() {
        assertNull(SmsCommandParser.parseSubscribe(listOf("--hours")))
    }

    @Test
    fun `non-integer dist value returns null`() {
        assertNull(SmsCommandParser.parseSubscribe(listOf("--dist", "abc")))
    }

    @Test
    fun `non-integer freq value returns null`() {
        assertNull(SmsCommandParser.parseSubscribe(listOf("--freq", "xyz")))
    }

    @Test
    fun `unknown flag returns null`() {
        assertNull(SmsCommandParser.parseSubscribe(listOf("--unknown", "5")))
    }

    @Test
    fun `only dist flag uses other defaults`() {
        val result = SmsCommandParser.parseSubscribe(listOf("--dist", "100"))
        assertNotNull(result)
        assertEquals(100, result!!.dist)
        assertEquals(15, result.freq)
        assertEquals(4, result.hours)
    }

    @Test
    fun `only freq flag uses other defaults`() {
        val result = SmsCommandParser.parseSubscribe(listOf("--freq", "30"))
        assertNotNull(result)
        assertEquals(200, result!!.dist)
        assertEquals(30, result.freq)
        assertEquals(4, result.hours)
    }

    @Test
    fun `freq equals 1 is valid`() {
        val result = SmsCommandParser.parseSubscribe(listOf("--freq", "1"))
        assertNotNull(result)
        assertEquals(1, result!!.freq)
    }
}
