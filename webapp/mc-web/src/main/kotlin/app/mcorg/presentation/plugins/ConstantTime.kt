package app.mcorg.presentation.plugins

import java.security.MessageDigest

/**
 * Compares a presented secret with the configured one without leaking, through timing, how many
 * leading bytes matched. The one copy for every shared-secret gate in this package.
 */
internal fun constantTimeEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
