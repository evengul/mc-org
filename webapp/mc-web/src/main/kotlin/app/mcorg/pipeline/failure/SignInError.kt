package app.mcorg.pipeline.failure

/**
 * Every reason a sign-in may be sent back to `/auth/sign-in?error=<code>`, with the one line the
 * sign-in page shows above the Microsoft button for it (MCO-625).
 *
 * The same allowlist rule as [SignOutReason]: the page never prints anything from its URL, because
 * anyone can craft a link to it. A code that is not listed renders [GENERIC]; no `error` at all
 * renders nothing. Producers go through [url] so the two ends cannot drift.
 */
enum class SignInError(val code: String, val message: String) {
    INVALID_STATE(
        "invalid_state",
        "That sign-in could not be matched to this browser, so it was stopped. Please sign in again.",
    ),
    DEMO_SIGN_IN_NOT_CONFIGURED(
        "demo_sign_in_not_configured",
        "Demo sign-in is not set up on this server.",
    ),
    GENERIC(
        "sign_in_failed",
        "Sign-in did not finish. Please try again.",
    );

    val url: String get() = "$PATH?error=$code"

    companion object {
        private const val PATH = "/auth/sign-in"

        /** `null` when there is no error to show; [GENERIC] for a code that is not listed. */
        fun fromCode(code: String?): SignInError? = when {
            code.isNullOrBlank() -> null
            else -> entries.firstOrNull { it.code == code } ?: GENERIC
        }
    }
}
