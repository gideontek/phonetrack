package com.gideontek.phonetrack

/**
 * The movement part of a subscription update: the direction and distance since the previous update.
 * [show] off drops it. [cardinal] writes the direction as compass letters (`NE395m`) instead of an arrow
 * (`⇗395m`); the letters are in the 160-character SMS alphabet, the arrow is not.
 */
data class MovementOptions(
    val show: Boolean = true,
    val cardinal: Boolean = false
) {
    companion object {
        val DEFAULT = MovementOptions()
    }
}

/**
 * What a location reply contains. The app's default ([DEFAULT]) is accuracy, battery and the
 * OpenStreetMap link; coordinates, time of fix and the `geo:` link are opt-in from the "Reply contents"
 * settings card. At least one of coordinates, `geo:` link and map link must stay on ([normalized] adds the
 * map link otherwise). The constructor itself starts with everything off, so `ReplyOptions(coords = true)`
 * means exactly "coordinates only".
 *
 * - [coords]   `Lat: 37.7749, Lon: -122.4194`
 * - [accuracy] `Acc: 5m`
 * - [battery]  `Bat: 85%` (plus "(charging)" while charging)
 * - [time]     `Time: 14:32Z` (UTC time of the fix)
 * - [geo]      a `geo:` URI that any maps app opens
 * - [osm]      an OpenStreetMap link that opens in any browser
 *
 * [link] is how the map link is written (domain, readable or encoded position, zoom); it only has an effect
 * while [osm] is on.
 *
 * [movement] is how a subscription update says how far the phone moved since the previous one (arrow or
 * compass letters, or nothing); it has no effect on any other reply. The age header in `last` is not an
 * option: it is part of what that reply is for.
 */
data class ReplyOptions(
    val coords: Boolean = false,
    val accuracy: Boolean = false,
    val battery: Boolean = false,
    val time: Boolean = false,
    val geo: Boolean = false,
    val osm: Boolean = false,
    val link: MapLinkOptions = MapLinkOptions.DEFAULT,
    val movement: MovementOptions = MovementOptions.DEFAULT
) {
    val hasAny: Boolean get() = coords || accuracy || battery || time || geo || osm

    /** How many parts are switched on. */
    val count: Int get() = listOf(coords, accuracy, battery, time, geo, osm).count { it }

    /** How many of the parts that say where the phone is ([coords], [geo], [osm]) are switched on. */
    val locationCount: Int get() = listOf(coords, geo, osm).count { it }

    /** A reply must say where the phone is: accuracy, battery and time alone do not. */
    val hasLocation: Boolean get() = locationCount > 0

    /** A reply without a location part gets the map link added, so an all-off set means "just the map link". */
    fun normalized(): ReplyOptions {
        val withLink = copy(link = link.normalized())
        return if (hasLocation) withLink else withLink.copy(osm = true)
    }

    companion object {
        /** What a reply contains until the owner changes it. */
        val DEFAULT = ReplyOptions(accuracy = true, battery = true, osm = true)

        /** The minimal reply: used when nothing is selected, or nothing selected has anything to show. */
        val LINK_ONLY = ReplyOptions(osm = true)
    }
}
