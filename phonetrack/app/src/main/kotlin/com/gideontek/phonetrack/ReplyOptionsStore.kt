package com.gideontek.phonetrack

import android.content.Context

/** SharedPreferences-backed [ReplyOptions]: six booleans, `reply_*`; an absent key means its default. */
object ReplyOptionsStore {
    const val KEY_PREFIX = "reply_"
    private const val COORDS = "reply_coords"
    private const val ACCURACY = "reply_accuracy"
    private const val BATTERY = "reply_battery"
    private const val TIME = "reply_time"
    private const val GEO = "reply_geo"
    private const val OSM = "reply_osm"

    fun read(ctx: Context): ReplyOptions {
        val p = ctx.getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE)
        val d = ReplyOptions.DEFAULT
        return ReplyOptions(
            coords = p.getBoolean(COORDS, d.coords),
            accuracy = p.getBoolean(ACCURACY, d.accuracy),
            battery = p.getBoolean(BATTERY, d.battery),
            time = p.getBoolean(TIME, d.time),
            geo = p.getBoolean(GEO, d.geo),
            osm = p.getBoolean(OSM, d.osm)
        ).normalized()
    }

    /** Writes [options] (normalized, so an all-off set can never be stored). */
    fun write(ctx: Context, options: ReplyOptions) {
        val o = options.normalized()
        ctx.getSharedPreferences("phonetrack_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean(COORDS, o.coords).putBoolean(ACCURACY, o.accuracy).putBoolean(BATTERY, o.battery)
            .putBoolean(TIME, o.time).putBoolean(GEO, o.geo).putBoolean(OSM, o.osm)
            .apply()
    }
}
