package com.gideontek.phonetrack

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Pure PBKDF2 hashing for the settings PIN, so the PIN is never stored in the clear.
 *
 * A hash is one self-describing string, `v1:<iterations>:<saltB64>:<hashB64>`, so the
 * parameters can change later without breaking stored PINs. Uses only the JDK/Android
 * `javax.crypto` (no third-party library).
 *
 * Honest limit: a 4-digit PIN has 10,000 possibilities, so even a PBKDF2 hash can be brute-forced
 * offline in minutes by someone who copies the prefs file. Hashing keeps the PIN from being read
 * directly; the in-app lockout ([PinLockout]) and keeping the file out of backups are the
 * protections that matter.
 */
object PinHasher {
    const val DEFAULT_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 10_000_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val VERSION = "v1"
    private val random = SecureRandom()

    /** Hashes [pin] with a fresh random salt. [pin] must not be empty. */
    fun hash(pin: String, iterations: Int = DEFAULT_ITERATIONS): String =
        hash(pin, ByteArray(SALT_BYTES).also { random.nextBytes(it) }, iterations)

    fun hash(pin: String, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): String {
        require(pin.isNotEmpty()) { "PIN must not be empty" }
        val b64 = Base64.getEncoder()
        return "$VERSION:$iterations:${b64.encodeToString(salt)}:${b64.encodeToString(derive(pin, salt, iterations))}"
    }

    /** True if [pin] matches [stored]. Any malformed stored value simply fails to verify. */
    fun verify(pin: String, stored: String): Boolean {
        if (pin.isEmpty()) return false
        val parts = stored.split(":")
        if (parts.size != 4 || parts[0] != VERSION) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it in 1..MAX_ITERATIONS } ?: return false
        val decoder = Base64.getDecoder()
        val salt: ByteArray
        val expected: ByteArray
        try {
            salt = decoder.decode(parts[2])
            expected = decoder.decode(parts[3])
        } catch (_: IllegalArgumentException) {
            return false
        }
        if (salt.isEmpty() || expected.isEmpty()) return false
        // Constant-time compare, so timing doesn't reveal how many bytes matched.
        return MessageDigest.isEqual(derive(pin, salt, iterations), expected)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
