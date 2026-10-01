package com.gideontek.phonetrack.support

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.gideontek.phonetrack.SmsReceiver
import java.io.ByteArrayOutputStream

/**
 * Delivers an incoming SMS to the real [SmsReceiver] in-process. The intent carries a genuine
 * SMS-DELIVER PDU, so `Telephony.Sms.Intents.getMessagesFromIntent` parses it exactly as it does
 * for a message from the radio. (Calling the receiver directly sidesteps the protected-broadcast
 * restriction on `SMS_RECEIVED`.)
 */
object SyntheticSms {

    fun deliver(context: Context, from: String, body: String) {
        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
            .putExtra("pdus", arrayOf<Any>(pdu(from, body)))
            .putExtra("format", "3gpp")
        SmsReceiver().onReceive(context, intent)
    }

    /** A single-part SMS-DELIVER PDU (GSM 7-bit when the text allows, otherwise UCS-2). */
    fun pdu(from: String, body: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x00) // no SMSC address
        out.write(0x04) // SMS-DELIVER
        val digits = from.filter { it.isDigit() }
        out.write(digits.length)
        out.write(if (from.trim().startsWith("+")) 0x91 else 0x81)
        out.write(swappedBcd(digits))
        out.write(0x00) // PID
        val gsm = body.all { isGsmBasic(it) }
        out.write(if (gsm) 0x00 else 0x08) // DCS
        out.write(byteArrayOf(0x62, 0x10, 0x01, 0x12, 0x00, 0x00, 0x00)) // timestamp
        if (gsm) {
            val septets = body.map { gsmCode(it) }.toIntArray()
            require(septets.size <= 160) { "single-part helper: at most 160 septets" }
            out.write(septets.size)
            out.write(packSeptets(septets))
        } else {
            val bytes = body.toByteArray(Charsets.UTF_16BE)
            require(bytes.size <= 140) { "single-part helper: at most 70 UCS-2 characters" }
            out.write(bytes.size)
            out.write(bytes)
        }
        return out.toByteArray()
    }

    private fun swappedBcd(digits: String): ByteArray {
        val padded = if (digits.length % 2 == 1) digits + "F" else digits
        return ByteArray(padded.length / 2) { i ->
            val lo = Character.digit(padded[2 * i], 16)
            val hi = Character.digit(padded[2 * i + 1], 16)
            ((hi shl 4) or lo).toByte()
        }
    }

    // Characters whose GSM 03.38 code equals their ASCII code, plus the three that differ.
    private const val SAME_AS_ASCII = " !\"#%&'()*+,-./0123456789:;<=>?" +
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz\n\r"

    private fun isGsmBasic(c: Char) = c in SAME_AS_ASCII || c == '@' || c == '$' || c == '_'

    private fun gsmCode(c: Char): Int = when (c) {
        '@' -> 0x00
        '$' -> 0x02
        '_' -> 0x11
        else -> c.code
    }

    private fun packSeptets(septets: IntArray): ByteArray {
        val out = ByteArray((septets.size * 7 + 7) / 8)
        var bit = 0
        for (s in septets) {
            val index = bit / 8
            val offset = bit % 8
            out[index] = (out[index].toInt() or ((s shl offset) and 0xFF)).toByte()
            if (offset > 1) out[index + 1] = (out[index + 1].toInt() or (s shr (8 - offset))).toByte()
            bit += 7
        }
        return out
    }
}
