package app.mcorg.presentation.templated.error

fun notFoundPage(): String = errorPageLayout(
    pageTitle = "404 — Not Found · Seam",
    heading = "404 — Not Found",
    body = "That page doesn't exist or has moved.",
    ctaText = "Back to worlds",
    ctaHref = "/worlds",
)
