package app.mcorg.presentation.templated.error

fun bannedPage(): String = errorPageLayout(
    pageTitle = "Account Suspended · MC-ORG",
    heading = "Account Suspended",
    body = "Your account has been suspended from MC-ORG. If you believe this is in error, contact support.",
)
