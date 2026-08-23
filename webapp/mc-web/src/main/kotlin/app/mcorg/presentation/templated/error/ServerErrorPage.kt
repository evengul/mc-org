package app.mcorg.presentation.templated.error

/**
 * @param callId this request's correlation id, rendered so the user has something to quote.
 *
 * The page claimed "the error has been logged" while giving the reader nothing to identify it by
 * — and until MCO-350 the claim was not even true for failures that never reached a logger. The
 * id is opaque and server-generated, so showing it discloses nothing (MCO-341 pins the format);
 * null only in contexts where the CallId plugin is not installed.
 */
fun serverErrorPage(callId: String? = null): String = errorPageLayout(
    pageTitle = "500 — Something Broke · Seam",
    heading = "500 — Something Broke",
    body = buildString {
        append("An unexpected error occurred. Try again, or head back to your worlds.")
        if (callId != null) append(" If you report this, quote reference $callId.")
    },
    ctaText = "Back to worlds",
    ctaHref = "/worlds",
)
