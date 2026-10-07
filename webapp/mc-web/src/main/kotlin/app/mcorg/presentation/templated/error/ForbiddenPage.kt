package app.mcorg.presentation.templated.error

/** @param reason why this person was refused, in the words the refusing plugin chose. */
fun forbiddenPage(reason: String): String = errorPageLayout(
    pageTitle = "403 — Forbidden · Seam",
    heading = "403 — Forbidden",
    body = reason,
    ctaText = "Back to worlds",
    ctaHref = "/worlds",
)
