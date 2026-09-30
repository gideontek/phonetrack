package com.gideontek.phonetrack

import org.junit.Assert.*
import org.junit.Test

class PinMigrationTest {

    @Test fun `a legacy plaintext PIN is hashed and the hash verifies`() {
        val hash = PinMigration.hashFor("4821", null)
        assertNotNull(hash)
        assertTrue(PinHasher.verify("4821", hash!!))
        assertFalse(hash.contains("4821"))
    }

    @Test fun `an empty existing hash still migrates`() {
        assertNotNull(PinMigration.hashFor("4821", ""))
    }

    @Test fun `no plaintext PIN means nothing to migrate`() {
        assertNull(PinMigration.hashFor(null, null))
        assertNull(PinMigration.hashFor("", null))
    }

    @Test fun `an existing hash wins over a leftover plaintext PIN`() {
        assertNull(PinMigration.hashFor("4821", PinHasher.hash("9999", 1_000)))
    }
}
