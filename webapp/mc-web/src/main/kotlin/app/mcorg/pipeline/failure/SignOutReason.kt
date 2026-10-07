package app.mcorg.pipeline.failure

/**
 * Every reason a request may be sent to `/auth/sign-out?error=<code>`, with the copy that page shows
 * for it (MCO-438).
 *
 * This is an allowlist, and it is the only thing the sign-out page reads from its URL. Anyone can
 * craft a link to that page, so before this the code and every other query parameter were printed
 * verbatim on a Seam-branded page — escaped, so not XSS, but text an attacker chose ("your account
 * is suspended, call …") inside real app chrome. A code that is not listed here renders [GENERIC].
 *
 * Producers emit [code] rather than a literal so the two ends cannot drift, and never anything built
 * from a value at hand: an exception's class name, a JWT claim, an upstream description. Those travel
 * into browser history, access logs and `Referer` headers.
 */
enum class SignOutReason(val code: String, val heading: String, val body: String) {
    INVALID_TOKEN(
        "invalid_token",
        "You have been signed out",
        "Your session could not be verified, so we signed you out. Sign in again to continue.",
    ),
    EXPIRED_TOKEN(
        "expired_token",
        "Your session has expired",
        "You have been signed out. Sign in again to pick up where you left off.",
    ),
    MISSING_CODE(
        "missing_code",
        "Sign-in did not finish",
        "Microsoft sent you back without completing the sign-in. Please try again.",
    ),
    MICROSOFT_DENIED(
        "microsoft_denied",
        "Sign-in cancelled",
        "The Microsoft sign-in was declined, so you are not signed in. Sign in again whenever you are ready.",
    ),
    MICROSOFT_FAILED(
        "microsoft_failed",
        "Microsoft sign-in failed",
        "Microsoft could not complete the sign-in. Please try again.",
    ),
    EXTERNAL_API_ERROR(
        "external_api_error",
        "Could not load your Minecraft profile",
        "Signing in goes through Microsoft, Xbox and Minecraft, and one of them did not answer as expected. " +
            "Check that this Microsoft account owns Minecraft: Java Edition, then try again.",
    ),
    MISCONFIGURED(
        "misconfigured",
        "Sign-in is unavailable",
        "Sign-in is not set up correctly on our side. Please try again later.",
    ),
    INTERNAL_ERROR(
        "internal_error",
        "Sign-in failed",
        "Something went wrong on our side while signing you in. Please try again.",
    ),
    GENERIC(
        "signed_out",
        "You have been signed out",
        "Sign in again to continue.",
    );

    companion object {
        fun fromCode(code: String?): SignOutReason = entries.firstOrNull { it.code == code } ?: GENERIC
    }
}
