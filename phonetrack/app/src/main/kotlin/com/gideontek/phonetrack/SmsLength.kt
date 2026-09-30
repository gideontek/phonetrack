package com.gideontek.phonetrack

/**
 * Whether text fits in a single SMS. Plain GSM-7 text allows 160 characters, but a few
 * punctuation characters ([ ] { } \ ^ ~ |) are "extension" characters that cost two. Anything
 * outside plain ASCII (an arrow, say) makes the phone switch the whole message to UCS-2, which
 * allows only 70. This errs on the side of splitting: characters we never emit are treated as
 * UCS-2 rather than looked up in the full GSM table.
 */
object SmsLength {
    const val GSM7_LIMIT = 160
    const val UCS2_LIMIT = 70
    private const val EXTENSION_CHARS = "[]{}\\^~|"

    /** GSM-7 septets [text] needs, or null if it can't be sent as GSM-7 (so it would be UCS-2). */
    fun gsm7Septets(text: String): Int? {
        var septets = 0
        for (c in text) {
            septets += when {
                c == '\n' -> 1
                c == '`' -> return null                         // not in the GSM-7 alphabet
                c.code in 0x20..0x7E -> if (c in EXTENSION_CHARS) 2 else 1
                else -> return null
            }
        }
        return septets
    }

    fun fitsOneSms(text: String): Boolean {
        val septets = gsm7Septets(text)
        return if (septets != null) septets <= GSM7_LIMIT else text.length <= UCS2_LIMIT
    }
}
