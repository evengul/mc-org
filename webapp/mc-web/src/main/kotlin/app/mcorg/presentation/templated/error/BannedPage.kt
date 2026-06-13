package app.mcorg.presentation.templated.error

fun bannedPage(): String = errorPageLayout(
    pageTitle = "Account Suspended · Seam",
    heading = "Account Suspended",
    body = "Your account has been suspended from Seam. If you believe this is in error, contact support.",
)
