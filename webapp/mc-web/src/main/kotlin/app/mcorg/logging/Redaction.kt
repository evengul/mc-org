package app.mcorg.logging

/**
 * Renders a secret as a non-reversible placeholder for `toString()` (MCO-340).
 *
 * The convention, in one place so every secret-bearing type reads the same way:
 * `<redacted:N>` where N is the length. The length is deliberately kept — it is the one property
 * that helps you debug ("the token came back empty" versus "the token looks like a JWT") without
 * revealing anything usable.
 *
 * Apply it in an explicit `toString()` override on any type holding a token, a bearer or HMAC
 * secret, or a verbatim upstream response body. The compiler-generated `toString()` on a data
 * class prints every field, so a type without an override is one `logger.debug("$thing")` away
 * from leaking. See `mc-web/CLAUDE.md` → Critical Rules and `documentation/logging.md`.
 */
fun redacted(secret: String?): String = when {
    secret == null -> "<redacted:null>"
    secret.isEmpty() -> "<redacted:empty>"
    else -> "<redacted:${secret.length}>"
}
