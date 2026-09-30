package com.gideontek.phonetrack

/** Pure decision for the one-time move from the legacy plaintext PIN to a hash. */
object PinMigration {

    /**
     * The hash to store for a legacy [plaintext] PIN, or null if there is nothing to hash: no
     * (or blank) plaintext PIN, or a hash already exists (it wins; the plaintext is just dropped).
     */
    fun hashFor(plaintext: String?, existingHash: String?): String? =
        if (plaintext.isNullOrEmpty() || !existingHash.isNullOrEmpty()) null else PinHasher.hash(plaintext)
}
