package com.gideontek.phonetrack

/** How the map link in a reply is written. The default is exactly what replies contained before this existed. */
data class MapLinkOptions(
    val shortDomain: Boolean = false,
    val encoded: Boolean = false,
    val zoom: Int = DEFAULT_ZOOM
) {
    fun normalized(): MapLinkOptions = copy(zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM))

    companion object {
        const val MIN_ZOOM = 8
        const val MAX_ZOOM = 18
        const val DEFAULT_ZOOM = 12
        val DEFAULT = MapLinkOptions()
    }
}

/** Builds the OpenStreetMap link for a reply. Pure: no Android imports. */
object OsmLink {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_~"
    private const val FULL_HOST = "www.openstreetmap.org"
    private const val SHORT_HOST = "osm.org"

    /** Characters carrying the position at [zoom]: 3 bits per axis each, `ceil((zoom + 8) / 3)`. */
    private fun charCount(zoom: Int) = (zoom + 8 + 2) / 3

    /**
     * OSM's "shortlink" code: the position interleaved bit by bit, 6 bits per character, then `-`
     * padding that records the zoom. `https://osm.org/go/<code>?m` opens that spot with a marker.
     */
    fun shortCode(lat: Double, lon: Double, zoom: Int): String {
        val x = ((lon + 180.0) / 360.0 * 4294967296.0).toLong().coerceIn(0L, 0xFFFFFFFFL)
        val y = ((lat + 90.0) / 180.0 * 4294967296.0).toLong().coerceIn(0L, 0xFFFFFFFFL)
        var c = 0L
        for (i in 0 until 32) {
            c = (c shl 2) or (((x shr (31 - i)) and 1L) shl 1) or ((y shr (31 - i)) and 1L)
        }
        val sb = StringBuilder()
        for (i in 0 until charCount(zoom)) sb.append(ALPHABET[((c ushr (58 - 6 * i)) and 0x3F).toInt()])
        repeat((zoom + 8) % 3) { sb.append('-') }
        return sb.toString()
    }

    /** Worst-case position error in metres of an encoded link at [zoom], measured along the equator. */
    fun precisionMeters(zoom: Int): Double {
        val bitsPerAxis = 3 * charCount(zoom)
        return 360.0 / (1L shl bitsPerAxis) * 111_320.0
    }

    fun url(lat: Double, lon: Double, options: MapLinkOptions): String {
        val o = options.normalized()
        val host = if (o.shortDomain) SHORT_HOST else FULL_HOST
        return if (o.encoded) {
            "https://$host/go/${shortCode(lat, lon, o.zoom)}?m"
        } else {
            val la = SmsComposer.fmt(lat)
            val lo = SmsComposer.fmt(lon)
            "https://$host/?mlat=$la&mlon=$lo#map=${o.zoom}/$la/$lo"
        }
    }
}
