package com.gideontek.phonetrack

import org.junit.Assert.*
import org.junit.Test

class OsmLinkTest {

    // The codes below were each resolved by osm.org/go/<code> on the live site to the right place and zoom.
    @Test fun `shortCode matches codes the live site resolved`() {
        assertEquals("TZHvSR7N", OsmLink.shortCode(37.7749, -122.4194, 16))
        assertEquals("ZW0DCLd--", OsmLink.shortCode(41.52962, -81.459, 12))
        assertEquals("ZW0DCLd", OsmLink.shortCode(41.52962, -81.459, 13))
        assertEquals("ZW0DCLdr-", OsmLink.shortCode(41.52962, -81.459, 14))
        assertEquals("ZW0DCLdr--", OsmLink.shortCode(41.52962, -81.459, 15))
        assertEquals("ZW0DCLdr", OsmLink.shortCode(41.52962, -81.459, 16))
    }

    @Test fun `code length is position characters plus zoom padding`() {
        for (z in 8..18) {
            val chars = (z + 8 + 2) / 3
            assertEquals("zoom $z", chars + (z + 8) % 3, OsmLink.shortCode(10.0, 20.0, z).length)
        }
    }

    @Test fun `zoom 13 and 16 give the shortest codes`() {
        val lengths = (8..18).associateWith { OsmLink.shortCode(10.0, 20.0, it).length }
        assertEquals(7, lengths[13]); assertEquals(8, lengths[16])
        assertTrue(lengths.getValue(12) > lengths.getValue(13))
        assertTrue(lengths.getValue(14) > lengths.getValue(13))
    }

    @Test fun `the default is the full readable URL used before this option existed`() {
        assertEquals(
            "https://www.openstreetmap.org/?mlat=37.7749&mlon=-122.4194#map=12/37.7749/-122.4194",
            OsmLink.url(37.7749, -122.4194, MapLinkOptions())
        )
    }

    @Test fun `short domain readable`() {
        assertEquals(
            "https://osm.org/?mlat=37.7749&mlon=-122.4194#map=12/37.7749/-122.4194",
            OsmLink.url(37.7749, -122.4194, MapLinkOptions(shortDomain = true))
        )
    }

    @Test fun `zoom changes only the map fragment of a readable link`() {
        val u = OsmLink.url(37.7749, -122.4194, MapLinkOptions(zoom = 16))
        assertEquals("https://www.openstreetmap.org/?mlat=37.7749&mlon=-122.4194#map=16/37.7749/-122.4194", u)
    }

    @Test fun `coordinates are trimmed like the rest of the reply`() {
        assertEquals(
            "https://osm.org/?mlat=41.52962&mlon=-81.459#map=12/41.52962/-81.459",
            OsmLink.url(41.52962, -81.459, MapLinkOptions(shortDomain = true))
        )
    }

    @Test fun `encoded short domain`() {
        assertEquals(
            "https://osm.org/go/TZHvSR7N?m",
            OsmLink.url(37.7749, -122.4194, MapLinkOptions(shortDomain = true, encoded = true, zoom = 16))
        )
    }

    @Test fun `encoded full domain`() {
        assertEquals(
            "https://www.openstreetmap.org/go/ZW0DCLd--?m",
            OsmLink.url(41.52962, -81.459, MapLinkOptions(encoded = true, zoom = 12))
        )
    }

    @Test fun `an out-of-range zoom is clamped`() {
        assertTrue(OsmLink.url(1.0, 2.0, MapLinkOptions(zoom = 99)).endsWith("#map=18/1/2"))
        assertTrue(OsmLink.url(1.0, 2.0, MapLinkOptions(zoom = -3)).endsWith("#map=8/1/2"))
    }

    @Test fun `every hemisphere encodes with alphabet characters only`() {
        val ok = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_~-"
        for ((la, lo) in listOf(-33.8688 to 151.2093, 51.5074 to -0.1278, -34.6 to -58.4, 35.7 to 139.7)) {
            val code = OsmLink.shortCode(la, lo, 12)
            assertTrue("$la,$lo $code", code.all { it in ok })
        }
    }

    @Test fun `the poles and the date line stay in range`() {
        for ((la, lo) in listOf(90.0 to 180.0, -90.0 to -180.0, 90.0 to -180.0, 0.0 to 0.0)) {
            assertEquals("$la,$lo", 9, OsmLink.shortCode(la, lo, 12).length)
        }
    }

    @Test fun `precision follows the character count`() {
        assertEquals(153.0, OsmLink.precisionMeters(8), 1.0)
        assertEquals(153.0, OsmLink.precisionMeters(10), 1.0)
        assertEquals(19.1, OsmLink.precisionMeters(12), 0.1)
        assertEquals(19.1, OsmLink.precisionMeters(13), 0.1)
        assertEquals(2.4, OsmLink.precisionMeters(14), 0.1)
        assertEquals(0.3, OsmLink.precisionMeters(17), 0.05)
    }

    @Test fun `the real decoded position is within the stated precision`() {
        // 37.7749, -122.4194 at zoom 12: the site decoded the code to lat 37.77489, lon -122.41941 at zoom 16 (see plan).
        // Here: re-derive a cell from the code and compare against the original within precisionMeters.
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_~"
        for (z in listOf(10, 12, 14, 16, 18)) {
            val code = OsmLink.shortCode(41.52962, -81.459, z).trimEnd('-')
            var c = 0L
            for ((i, ch) in code.withIndex()) c = c or (alphabet.indexOf(ch).toLong() shl (58 - 6 * i))
            var x = 0L; var y = 0L
            for (i in 0 until 32) { x = (x shl 1) or ((c ushr (63 - 2 * i)) and 1L); y = (y shl 1) or ((c ushr (62 - 2 * i)) and 1L) }
            val lon = x / 4294967296.0 * 360.0 - 180.0
            val lat = y / 4294967296.0 * 180.0 - 90.0
            val errM = Math.hypot((lat - 41.52962) * 111_320.0, (lon + 81.459) * 111_320.0 * Math.cos(Math.toRadians(41.5)))
            assertTrue("zoom $z error $errM m", errM <= OsmLink.precisionMeters(z) * 1.5)
        }
    }
}
