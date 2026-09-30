package com.gideontek.phonetrack

/**
 * What a location reply contains. The app's default ([DEFAULT]) is coordinates, accuracy, battery
 * and the OpenStreetMap link; time of fix and the `geo:` link are opt-in from the "Reply contents"
 * settings card. The constructor itself starts with everything off, so `ReplyOptions(coords = true)`
 * means exactly "coordinates only".
 *
 * - [coords]   `Lat: 37.7749, Lon: -122.4194`
 * - [accuracy] `Acc: 5m`
 * - [battery]  `Bat: 85%` (plus "(charging)" while charging)
 * - [time]     `Time: 14:32Z` (UTC time of the fix)
 * - [geo]      a `geo:` URI that any maps app opens
 * - [osm]      an OpenStreetMap link that opens in any browser
 *
 * The movement arrow and distance in periodic updates, and the age header in `last`, are not
 * options: they are part of what those replies are for.
 */
data class ReplyOptions(
    val coords: Boolean = false,
    val accuracy: Boolean = false,
    val battery: Boolean = false,
    val time: Boolean = false,
    val geo: Boolean = false,
    val osm: Boolean = false
) {
    val hasAny: Boolean get() = coords || accuracy || battery || time || geo || osm

    /** How many parts are switched on. */
    val count: Int get() = listOf(coords, accuracy, battery, time, geo, osm).count { it }

    /** A reply with nothing in it is useless, so an all-off set means "just the map link". */
    fun normalized(): ReplyOptions = if (hasAny) this else LINK_ONLY

    companion object {
        /** What a reply contains until the owner changes it. */
        val DEFAULT = ReplyOptions(coords = true, accuracy = true, battery = true, osm = true)

        /** The minimal reply: used when nothing is selected, or nothing selected has anything to show. */
        val LINK_ONLY = ReplyOptions(osm = true)
    }
}
