package app.mcorg.pipeline.failure

/**
 * Every reason a request may be sent to `/auth/sign-out?error=<code>`, with the copy that page shows
 * for it (MCO-438).
 *
 * This is an allowlist, and it is the only thing the sign-out page reads from its URL. Anyone can
 * craft a link to that page, so anything it printed from the URL would be text an attacker chose
 * ("your account is suspended, call …") inside real app chrome — escaped, so not XSS, but still a
 * phishing surface. A code that is not listed here renders [GENERIC].
 *
 * Producers go through [url] or [redirect] so the two ends cannot drift, and never put anything built
 * from a value at hand in the URL: an exception's class name, a JWT claim, an upstream description.
 * Those travel into browser history and `Referer` headers; log them instead, within logging.md.
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
            "Please try again in a minute. If it keeps failing, check that this Microsoft account owns " +
            "Minecraft: Java Edition.",
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

    val url: String get() = "$PATH?error=$code"

    fun redirect() = AppFailure.Redirect(path = PATH, queryParameters = mapOf("error" to code))

    companion object {
        private const val PATH = "/auth/sign-out"

        fun fromCode(code: String?): SignOutReason = entries.firstOrNull { it.code == code } ?: GENERIC
    }
}
