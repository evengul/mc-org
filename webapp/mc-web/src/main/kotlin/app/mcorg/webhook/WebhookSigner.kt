package app.mcorg.webhook

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Signs a webhook body so the receiver can verify it came from Seam and was not tampered with.
 * The signature is `sha256=<hex>` where `<hex>` is the lowercase hex HMAC-SHA256 of the exact raw
 * request body keyed by the subscription's shared secret. Sent in the `X-Seam-Signature` header.
 *
 * The receiver recomputes the HMAC over the raw bytes it received and compares in constant time.
 */
object WebhookSigner {
    const val HEADER = "X-Seam-Signature"
    private const val ALGORITHM = "HmacSHA256"

    fun sign(secret: String, body: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), ALGORITHM))
        val digest = mac.doFinal(body.toByteArray(Charsets.UTF_8))
        return "sha256=" + digest.joinToString("") { "%02x".format(it) }
    }
}
