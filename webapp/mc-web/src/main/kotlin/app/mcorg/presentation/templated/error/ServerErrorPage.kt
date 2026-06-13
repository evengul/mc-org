package app.mcorg.presentation.templated.error

fun serverErrorPage(): String = errorPageLayout(
    pageTitle = "500 — Something Broke · Seam",
    heading = "500 — Something Broke",
    body = "An unexpected error occurred. The error has been logged. Try again, or head back to your worlds.",
    ctaText = "Back to worlds",
    ctaHref = "/worlds",
)
