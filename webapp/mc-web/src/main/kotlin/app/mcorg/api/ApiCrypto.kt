package app.mcorg.api

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Token generation and hashing for the mod-facing API (MCO-236).
 *
 * Bearer tokens are opaque 32-byte random values, base64url-encoded, returned to the client exactly
 * once. Only the SHA-256 hash is persisted (see [sha256Hex]); a stolen database row cannot be
 * replayed as a token. Device/user codes for the device-code flow are generated here too.
 */
object ApiCrypto {
    private val random = SecureRandom()
    private val base64Url = Base64.getUrlEncoder().withoutPadding()

    /** Unambiguous alphabet for the human-typed user code (no 0/O/1/I/L). */
    private const val USER_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    /** A fresh opaque bearer/device token: 32 random bytes, base64url, no padding. */
    fun newToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return base64Url.encodeToString(bytes)
    }

    /** A short, human-typable user code formatted `XXXX-XXXX` from an unambiguous alphabet. */
    fun newUserCode(): String {
        val sb = StringBuilder(9)
        repeat(8) { i ->
            if (i == 4) sb.append('-')
            sb.append(USER_CODE_ALPHABET[random.nextInt(USER_CODE_ALPHABET.length)])
        }
        return sb.toString()
    }

    /** Lowercase hex SHA-256 of [value]; the stored form of a bearer token. */
    fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
