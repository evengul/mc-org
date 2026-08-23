package app.mcorg.presentation.security

/** Where a rejected redirect target lands. Signed-in users belong on their worlds list. */
const val DEFAULT_REDIRECT_PATH = "/worlds"

/**
 * Reduces a caller-supplied redirect target to a same-origin path, or to [DEFAULT_REDIRECT_PATH].
 *
 * Three sinks fed `respondRedirect` from user input with no validation (MCO-352): `redirect_to` on
 * the sign-in page, `redirect_to` on the unauthenticated demo endpoint, and — the nastiest — the
 * OAuth `state` parameter, which comes back through a genuine `login.microsoftonline.com`
 * authorize URL. An attacker crafting that URL with `state=https://evil.example` lands the victim
 * on their page from this origin immediately after a real sign-in, which is when a user is most
 * disposed to trust what they see and most likely to re-enter a credential.
 *
 * Allowlist, not denylist: the result must start with a single `/` and parse as a relative
 * reference. That is a property we can state, unlike "contains nothing dangerous", which is a
 * guess that has to be re-made every time a browser learns a new URL trick.
 *
 * Rejected: absolute URLs (`https://evil.example`), scheme-relative URLs (`//evil.example`, which
 * browsers resolve as absolute), `javascript:` and `data:`, backslash variants that some browsers
 * normalise to `/`, and anything with a newline that could split a header.
 */
fun safeRedirectPath(candidate: String?, fallback: String = DEFAULT_REDIRECT_PATH): String {
    if (candidate.isNullOrBlank()) return fallback

    // Control characters would let a crafted value split the Location header.
    if (candidate.any { it.isISOControl() }) return fallback

    // Backslashes first: several browsers treat "/\evil.example" and "\\evil.example" the way
    // they treat "//evil.example", so normalising here means the "//" check below sees them.
    val normalised = candidate.replace('\\', '/')

    // Must be rooted at this origin, and must not be scheme-relative.
    if (!normalised.startsWith("/")) return fallback
    if (normalised.startsWith("//")) return fallback

    // A colon before the first slash-or-query would be a scheme; "/" already rules that out, but
    // guard the mixed forms ("/\t/evil", "/:/...") that some parsers accept.
    val firstSegment = normalised.drop(1).substringBefore('/').substringBefore('?')
    if (firstSegment.contains(':')) return fallback

    return normalised
}
