package com.gideontek.phonetrack

import android.content.Context

/**
 * SharedPreferences-backed [ReplyOptions]: six booleans (`reply_coords` ... `reply_osm`) plus the four map link
 * settings (`reply_osm_short`, `reply_osm_encoded`, `reply_osm_zoom`, `reply_osm_avoid_tilde`). An absent key
 * means its default. Every key starts with [KEY_PREFIX], which is what the view model listens for.
 */
object ReplyOptionsStore {
    const val KEY_PREFIX = "reply_"
    private const val COORDS = "reply_coords"
    private const val ACCURACY = "reply_accuracy"
    private const val BATTERY = "reply_battery"
    private const val TIME = "reply_time"
    private const val GEO = "reply_geo"
    private const val OSM = "reply_osm"
    private const val LINK_SHORT = "reply_osm_short"
    private const val LINK_ENCODED = "reply_osm_encoded"
    private const val LINK_ZOOM = "reply_osm_zoom"
    private const val LINK_AVOID_TILDE = "reply_osm_avoid_tilde"

    fun read(ctx: Context): ReplyOptions {
        val p = ctx.getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)
        val d = ReplyOptions.DEFAULT
        val l = d.link
        return ReplyOptions(
            coords = p.getBoolean(COORDS, d.coords),
            accuracy = p.getBoolean(ACCURACY, d.accuracy),
            battery = p.getBoolean(BATTERY, d.battery),
            time = p.getBoolean(TIME, d.time),
            geo = p.getBoolean(GEO, d.geo),
            osm = p.getBoolean(OSM, d.osm),
            link = MapLinkOptions(
                shortDomain = p.getBoolean(LINK_SHORT, l.shortDomain),
                encoded = p.getBoolean(LINK_ENCODED, l.encoded),
                zoom = p.getInt(LINK_ZOOM, l.zoom),
                avoidTilde = p.getBoolean(LINK_AVOID_TILDE, l.avoidTilde)
            )
        ).normalized()
    }

    /** Writes [options] (normalized, so an all-off set can never be stored and the zoom stays in range). */
    fun write(ctx: Context, options: ReplyOptions) {
        val o = options.normalized()
        ctx.getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean(COORDS, o.coords).putBoolean(ACCURACY, o.accuracy).putBoolean(BATTERY, o.battery)
            .putBoolean(TIME, o.time).putBoolean(GEO, o.geo).putBoolean(OSM, o.osm)
            .putBoolean(LINK_SHORT, o.link.shortDomain).putBoolean(LINK_ENCODED, o.link.encoded)
            .putInt(LINK_ZOOM, o.link.zoom).putBoolean(LINK_AVOID_TILDE, o.link.avoidTilde)
            .apply()
    }
}
