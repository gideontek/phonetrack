package com.gideontek.phonetrack

import org.junit.Assert.*
import org.junit.Test

class ReplyOptionsTest {

    @Test fun `the default is the map link alone`() {
        val d = ReplyOptions.DEFAULT
        assertTrue(d.osm)
        assertFalse(d.coords || d.accuracy || d.battery || d.time || d.geo)
        assertEquals(1, d.count)
    }

    @Test fun `a no-argument instance equals the default`() {
        assertEquals(ReplyOptions.DEFAULT, ReplyOptions())
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

    @Test fun `normalizing an all-off set gives the map link`() {
        assertEquals(ReplyOptions.DEFAULT, ReplyOptions(false, false, false, false, false, false).normalized())
    }

    @Test fun `normalizing leaves any non-empty set alone`() {
        val opts = ReplyOptions(coords = true, geo = true, osm = false)
        assertEquals(opts, opts.normalized())
    }
}
