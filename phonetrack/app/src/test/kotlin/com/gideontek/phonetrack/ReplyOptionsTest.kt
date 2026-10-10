package com.gideontek.phonetrack

import org.junit.Assert.*
import org.junit.Test

class ReplyOptionsTest {

    @Test fun `the default is accuracy, battery and the map link`() {
        val d = ReplyOptions.DEFAULT
        assertTrue(d.accuracy && d.battery && d.osm)
        assertFalse(d.coords || d.time || d.geo)
        assertEquals(3, d.count)
    }

    @Test fun `a no-argument instance has everything off`() {
        assertEquals(0, ReplyOptions().count)
        assertFalse(ReplyOptions().hasAny)
    }

    @Test fun `link-only is just the map link`() {
        val l = ReplyOptions.LINK_ONLY
        assertTrue(l.osm)
        assertEquals(1, l.count)
    }

    @Test fun `count reflects the parts switched on`() {
        assertEquals(6, ReplyOptions(true, true, true, true, true, true).count)
        assertEquals(2, ReplyOptions(coords = true, osm = true).count)
        assertEquals(0, ReplyOptions(false, false, false, false, false, false).count)
    }

    @Test fun `hasAny is false only when everything is off`() {
        assertTrue(ReplyOptions.DEFAULT.hasAny)
        assertTrue(ReplyOptions(coords = true, osm = false).hasAny)
        assertFalse(ReplyOptions(false, false, false, false, false, false).hasAny)
    }

    @Test fun `normalizing an all-off set gives the map link alone`() {
        assertEquals(ReplyOptions.LINK_ONLY, ReplyOptions(false, false, false, false, false, false).normalized())
        assertEquals(ReplyOptions.LINK_ONLY, ReplyOptions().normalized())
    }

    @Test fun `normalizing leaves a set with a location part alone`() {
        val opts = ReplyOptions(coords = true, geo = true, osm = false)
        assertEquals(opts, opts.normalized())
        val geoOnly = ReplyOptions(accuracy = true, geo = true)
        assertEquals(geoOnly, geoOnly.normalized())
    }

    @Test fun `accuracy, battery and time alone are not a location, so the map link is added`() {
        assertFalse(ReplyOptions(accuracy = true, battery = true, time = true).hasLocation)
        assertEquals(
            ReplyOptions(accuracy = true, battery = true, time = true, osm = true),
            ReplyOptions(accuracy = true, battery = true, time = true).normalized()
        )
    }

    @Test fun `the location parts are coordinates, geo link and map link`() {
        assertEquals(0, ReplyOptions(accuracy = true, battery = true, time = true).locationCount)
        assertEquals(3, ReplyOptions(coords = true, geo = true, osm = true).locationCount)
        assertTrue(ReplyOptions(geo = true).hasLocation)
    }
}
