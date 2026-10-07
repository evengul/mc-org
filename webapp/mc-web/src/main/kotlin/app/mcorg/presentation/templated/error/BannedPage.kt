package app.mcorg.presentation.templated.error

const val BANNED_TITLE = "Account Suspended"
const val BANNED_MESSAGE =
    "Your account has been suspended from Seam. If you believe this is in error, contact support."

fun bannedPage(): String = errorPageLayout(
    pageTitle = "$BANNED_TITLE · Seam",
    heading = BANNED_TITLE,
    body = BANNED_MESSAGE,
)
