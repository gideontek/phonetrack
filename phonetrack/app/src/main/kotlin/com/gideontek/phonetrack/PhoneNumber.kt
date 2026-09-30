package com.gideontek.phonetrack

/**
 * Pure helpers for comparing the sender addresses that arrive with incoming SMS.
 *
 * The same person can show up as "+15551234567", "15551234567", "555-123-4567" or, for
 * UK numbers, "+447911123456" / "07911123456", so exact string comparison lets one person
 * hold several approval or subscription entries.
 */
object PhoneNumber {

    /** Fewest digits for a number we can reply to and compare safely (short codes have fewer). */
    private const val MIN_DIGITS = 7

    /** When one side has no country code, only the trailing digits are comparable. */
    private const val MATCH_DIGITS = 10

    /**
     * Strips spaces, dashes, dots and parentheses; keeps a leading '+'; turns a leading "00"
     * into '+'. Alphanumeric sender IDs (any letter) are returned trimmed but otherwise as-is.
     */
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        if (hasLetter(trimmed)) return trimmed
        val digits = trimmed.filter { it.isDigit() }
        return when {
            trimmed.startsWith("+") -> "+$digits"
            digits.startsWith("00") -> "+" + digits.drop(2)
            else -> digits
        }
    }

    /**
     * True if a reply could actually reach [raw]: no letters (alphanumeric sender IDs can't be
     * texted back) and at least [MIN_DIGITS] digits (shorter ones are short codes).
     */
    fun isReplyable(raw: String): Boolean {
        val trimmed = raw.trim()
        return !hasLetter(trimmed) && trimmed.count { it.isDigit() } >= MIN_DIGITS
    }

    /**
     * True if [a] and [b] are the same number. Equal after normalizing always matches. Two
     * numbers that both carry a country code ('+') must be identical. Otherwise the trailing
     * digits (up to 10) are compared, requiring at least [MIN_DIGITS] of them.
     */
    fun matches(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na == nb) return true
        if (hasLetter(na) || hasLetter(nb)) return false
        if (na.startsWith("+") && nb.startsWith("+")) return false
        val da = na.filter { it.isDigit() }
        val db = nb.filter { it.isDigit() }
        val n = minOf(MATCH_DIGITS, da.length, db.length)
        return n >= MIN_DIGITS && da.takeLast(n) == db.takeLast(n)
    }

    /**
     * A stable key for per-sender accounting (rate limits). Uses the same trailing digits
     * [matches] compares, so alternate spellings of one number ("+1555…" / "555…") share a
     * key and can't be used to get a second budget. Alphanumeric IDs are returned as-is.
     */
    fun rateKey(raw: String): String {
        val n = normalize(raw)
        return if (hasLetter(n)) n else n.filter { it.isDigit() }.takeLast(MATCH_DIGITS)
    }

    private fun hasLetter(s: String) = s.any { it.isLetter() }
}
