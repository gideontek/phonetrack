package com.gideontek.phonetrack.support

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider

/**
 * Reads back what the app texted: sent messages are recorded in the SMS provider, and the emulator
 * also loops those sent to its own number back into the inbox (which is why [ownNumber] is the
 * sender in tests). Reads in-process (not via `content query`) because the shell user lacks READ_SMS on API 26-28.
 */
object Loopback {

    /**
     * The emulator's own number. The `iphonesubinfo` transaction that returns it differs by API
     * level (15 on newer images, 13 or 17 on older ones), and older emulators derive the number
     * from the console port, so ask the device rather than assume.
     */
    val ownNumber: String by lazy {
        listOf(15, 13, 17).firstNotNullOfOrNull { code ->
            parseParcelString(Shell.run("service call iphonesubinfo $code"))?.takeIf { it.length >= 7 }
        } ?: "+15551234567"
    }

    private val smsUri: Uri = Uri.parse("content://sms")
    private val resolver get() = ApplicationProvider.getApplicationContext<Context>().contentResolver

    private fun maxId(): Long =
        resolver.query(smsUri, arrayOf("_id"), null, null, "_id DESC")?.use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        } ?: 0L

    /**
     * Highest SMS provider row id once nothing new has landed for [quietMs]; pass it to [await]
     * so only newer rows count. Every reply to the own number appears twice (the sent copy at
     * once, the looped-back copy a moment later), so a baseline taken too early would see the
     * previous test's second copy as a fresh reply.
     */
    fun lastId(quietMs: Long = 1_500): Long {
        var last = maxId()
        var since = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - since < quietMs) {
            SystemClock.sleep(250)
            val current = maxId()
            if (current != last) {
                last = current
                since = SystemClock.elapsedRealtime()
            }
        }
        return last
    }

    /**
     * Bodies of the messages the app sent (provider type 2) newer than [afterId], oldest first.
     * Only the sent rows are used: the looped-back inbox copy of each reply is not reliable (it is
     * garbled on some images), while the sent row is exact and appears once per message. Needs
     * READ_SMS (debug builds declare it).
     */
    fun bodies(afterId: Long): List<String> =
        resolver.query(smsUri, arrayOf("_id", "body"), "_id>? AND type=2", arrayOf(afterId.toString()), "_id ASC")?.use { c ->
            buildList { while (c.moveToNext()) add(c.getString(1) ?: "") }
        } ?: emptyList()

    /** The replies the app sent after [afterId], in order (one entry per message, repeats kept). */
    fun replies(afterId: Long): List<String> = bodies(afterId)

    /** Waits up to [timeoutMs] for a reply (newer than [afterId]) that matches [predicate]. */
    fun await(afterId: Long, timeoutMs: Long = 15_000, predicate: (String) -> Boolean = { true }): String? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            bodies(afterId).firstOrNull(predicate)?.let { return it }
            SystemClock.sleep(300)
        }
        return null
    }

    /** True if nothing matching shows up within [windowMs]; used to assert "no reply". */
    fun staysQuiet(afterId: Long, windowMs: Long = 4_000, predicate: (String) -> Boolean = { true }): Boolean =
        await(afterId, windowMs, predicate) == null

    /**
     * Decodes the UTF-16 string in `service call` parcel output: after the status and length
     * words, each 32-bit word holds two characters, low half first.
     */
    internal fun parseParcelString(raw: String): String? {
        val words = Regex("0x[0-9a-f]{8}:((?: [0-9a-f]{8}){1,4})").findAll(raw)
            .flatMap { it.groupValues[1].trim().split(' ') }
            .map { it.toLong(16) }
            .toList()
        if (words.size < 3) return null
        val length = words[1].toInt()
        val chars = words.drop(2).flatMap { listOf((it and 0xFFFF).toInt(), ((it shr 16) and 0xFFFF).toInt()) }
        return chars.take(length).map { it.toChar() }.joinToString("")
    }
}
