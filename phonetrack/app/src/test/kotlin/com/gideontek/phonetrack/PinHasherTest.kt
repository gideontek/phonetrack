package com.gideontek.phonetrack

import org.junit.Assert.*
import org.junit.Test

class PinHasherTest {

    private val fast = 1_000   // tests don't need the production iteration count

    @Test fun `the right PIN verifies`() {
        val stored = PinHasher.hash("4821", fast)
        assertTrue(PinHasher.verify("4821", stored))
    }

    @Test fun `a wrong PIN does not verify`() {
        val stored = PinHasher.hash("4821", fast)
        assertFalse(PinHasher.verify("4822", stored))
        assertFalse(PinHasher.verify("482", stored))
        assertFalse(PinHasher.verify("48210", stored))
    }

    @Test fun `an empty PIN never verifies`() {
        assertFalse(PinHasher.verify("", PinHasher.hash("4821", fast)))
    }

    @Test fun `hashing an empty PIN is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { PinHasher.hash("", fast) }
    }

    @Test fun `the stored value is self-describing and never contains the PIN`() {
        val stored = PinHasher.hash("4821", fast)
        val parts = stored.split(":")
        assertEquals(4, parts.size)
        assertEquals("v1", parts[0])
        assertEquals("1000", parts[1])
        assertFalse(stored.contains("4821"))
    }

    @Test fun `the same PIN gets a different salt and hash each time`() {
        val a = PinHasher.hash("4821", fast)
        val b = PinHasher.hash("4821", fast)
        assertNotEquals(a, b)
        assertTrue(PinHasher.verify("4821", a))
        assertTrue(PinHasher.verify("4821", b))
    }

    @Test fun `a fixed salt gives a repeatable hash`() {
        val salt = ByteArray(16) { it.toByte() }
        assertEquals(PinHasher.hash("4821", salt, fast), PinHasher.hash("4821", salt, fast))
    }

    @Test fun `the iteration count in the stored value is honoured`() {
        val salt = ByteArray(16) { 7 }
        val low = PinHasher.hash("4821", salt, 1_000)
        val high = PinHasher.hash("4821", salt, 2_000)
        assertNotEquals(low.split(":")[3], high.split(":")[3])
        assertTrue(PinHasher.verify("4821", low))
        assertTrue(PinHasher.verify("4821", high))
    }

    @Test fun `the production default round trips`() {
        assertEquals(100_000, PinHasher.DEFAULT_ITERATIONS)
        val stored = PinHasher.hash("123456")
        assertTrue(stored.startsWith("v1:100000:"))
        assertTrue(PinHasher.verify("123456", stored))
        assertFalse(PinHasher.verify("123457", stored))
    }

    @Test fun `long and unicode-free digit PINs work`() {
        val stored = PinHasher.hash("1234567890123456", fast)
        assertTrue(PinHasher.verify("1234567890123456", stored))
    }

    @Test fun `malformed stored values fail to verify instead of throwing`() {
        listOf(
            "", "garbage", "v1", "v1:1000", "v1:1000:abc", "v2:1000:AAAA:AAAA",
            "v1:notanumber:AAAA:AAAA", "v1:0:AAAA:AAAA", "v1:-5:AAAA:AAAA", "v1:99999999999:AAAA:AAAA",
            "v1:1000:!!!:AAAA", "v1:1000:AAAA:!!!", "v1:1000::AAAA", "v1:1000:AAAA:", "v1:1000:AAAA:AAAA:extra"
        ).forEach { assertFalse("should not verify: '$it'", PinHasher.verify("4821", it)) }
    }

    @Test fun `a tampered hash does not verify`() {
        val stored = PinHasher.hash("4821", fast).split(":").toMutableList()
        stored[3] = "AAAA" + stored[3].drop(4)
        assertFalse(PinHasher.verify("4821", stored.joinToString(":")))
    }
}
