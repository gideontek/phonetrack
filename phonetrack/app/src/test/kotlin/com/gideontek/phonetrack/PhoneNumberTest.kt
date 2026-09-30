package com.gideontek.phonetrack

import org.junit.Assert.*
import org.junit.Test

class PhoneNumberTest {

    // -------------------------------------------------------------------------
    // normalize
    // -------------------------------------------------------------------------

    @Test fun `normalize leaves a clean E164 number alone`() {
        assertEquals("+15551234567", PhoneNumber.normalize("+15551234567"))
    }

    @Test fun `normalize strips spaces dashes dots and parentheses`() {
        assertEquals("5551234567", PhoneNumber.normalize("(555) 123-4567"))
        assertEquals("5551234567", PhoneNumber.normalize("555.123.4567"))
        assertEquals("+15551234567", PhoneNumber.normalize("+1 555 123 4567"))
    }

    @Test fun `normalize turns a leading 00 into plus`() {
        assertEquals("+447911123456", PhoneNumber.normalize("00447911123456"))
    }

    @Test fun `normalize trims surrounding whitespace`() {
        assertEquals("+15551234567", PhoneNumber.normalize("  +15551234567 \n"))
    }

    @Test fun `normalize keeps an alphanumeric sender ID as is`() {
        assertEquals("MyBank", PhoneNumber.normalize(" MyBank "))
        assertEquals("AB-1234", PhoneNumber.normalize("AB-1234"))
    }

    @Test fun `normalize is idempotent`() {
        listOf("+1 (555) 123-4567", "0044 7911 123456", "555-123-4567", "MyBank").forEach {
            val once = PhoneNumber.normalize(it)
            assertEquals(once, PhoneNumber.normalize(once))
        }
    }

    @Test fun `normalize of an empty string is empty`() {
        assertEquals("", PhoneNumber.normalize("  "))
    }

    // -------------------------------------------------------------------------
    // isReplyable
    // -------------------------------------------------------------------------

    @Test fun `regular numbers are replyable`() {
        assertTrue(PhoneNumber.isReplyable("+15551234567"))
        assertTrue(PhoneNumber.isReplyable("5551234"))
        assertTrue(PhoneNumber.isReplyable("(555) 123-4567"))
    }

    @Test fun `alphanumeric sender IDs are not replyable`() {
        assertFalse(PhoneNumber.isReplyable("MyBank"))
        assertFalse(PhoneNumber.isReplyable("Bank1234567"))
    }

    @Test fun `short codes are not replyable`() {
        assertFalse(PhoneNumber.isReplyable("12345"))
        assertFalse(PhoneNumber.isReplyable("123456"))
    }

    @Test fun `empty is not replyable`() {
        assertFalse(PhoneNumber.isReplyable(""))
    }

    // -------------------------------------------------------------------------
    // matches
    // -------------------------------------------------------------------------

    @Test fun `identical numbers match`() {
        assertTrue(PhoneNumber.matches("+15551234567", "+15551234567"))
    }

    @Test fun `formatting differences match`() {
        assertTrue(PhoneNumber.matches("+1 (555) 123-4567", "+15551234567"))
    }

    @Test fun `plus number matches its national form`() {
        assertTrue(PhoneNumber.matches("+15551234567", "5551234567"))
        assertTrue(PhoneNumber.matches("5551234567", "+15551234567"))
    }

    @Test fun `plus number matches country code without plus`() {
        assertTrue(PhoneNumber.matches("+15551234567", "15551234567"))
    }

    @Test fun `national and country-code-without-plus forms match`() {
        assertTrue(PhoneNumber.matches("5551234567", "15551234567"))
    }

    @Test fun `UK number matches its national form`() {
        assertTrue(PhoneNumber.matches("+447911123456", "07911123456"))
    }

    @Test fun `00 prefix matches plus prefix`() {
        assertTrue(PhoneNumber.matches("0044 7911 123456", "+447911123456"))
    }

    @Test fun `two different plus numbers never match even with equal tails`() {
        assertFalse(PhoneNumber.matches("+15551234567", "+445551234567"))
    }

    @Test fun `different numbers do not match`() {
        assertFalse(PhoneNumber.matches("+15551234567", "+15551234568"))
        assertFalse(PhoneNumber.matches("5551234567", "5551234568"))
    }

    @Test fun `numbers too short to compare safely only match exactly`() {
        assertFalse(PhoneNumber.matches("123456", "0123456"))
        assertTrue(PhoneNumber.matches("123456", "123456"))
    }

    @Test fun `seven digit local numbers can match`() {
        assertTrue(PhoneNumber.matches("5551234", "555-1234"))
    }

    @Test fun `alphanumeric sender only matches itself`() {
        assertTrue(PhoneNumber.matches("MyBank", " MyBank "))
        assertFalse(PhoneNumber.matches("MyBank", "MyBanc"))
        assertFalse(PhoneNumber.matches("Bank1234567", "1234567"))
    }

    @Test fun `matches is symmetric`() {
        val pairs = listOf(
            "+15551234567" to "5551234567",
            "+447911123456" to "07911123456",
            "+15551234567" to "+445551234567",
            "5551234" to "555-1234"
        )
        pairs.forEach { (a, b) -> assertEquals(PhoneNumber.matches(a, b), PhoneNumber.matches(b, a)) }
    }

    // -------------------------------------------------------------------------
    // rateKey
    // -------------------------------------------------------------------------

    @Test fun `rateKey is the last ten digits`() {
        assertEquals("5551234567", PhoneNumber.rateKey("+15551234567"))
    }

    @Test fun `rateKey is shared by spellings that match`() {
        assertEquals(PhoneNumber.rateKey("+15551234567"), PhoneNumber.rateKey("555-123-4567"))
        assertEquals(PhoneNumber.rateKey("+15551234567"), PhoneNumber.rateKey("15551234567"))
        assertEquals(PhoneNumber.rateKey("+447911123456"), PhoneNumber.rateKey("07911123456"))
    }

    @Test fun `rateKey differs for different numbers`() {
        assertNotEquals(PhoneNumber.rateKey("+15551234567"), PhoneNumber.rateKey("+15551234568"))
    }

    @Test fun `rateKey keeps short numbers whole and alphanumeric IDs as is`() {
        assertEquals("5551234", PhoneNumber.rateKey("555-1234"))
        assertEquals("MyBank", PhoneNumber.rateKey(" MyBank "))
    }
}
