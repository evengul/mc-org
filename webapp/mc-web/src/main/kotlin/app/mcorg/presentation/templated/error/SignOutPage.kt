package app.mcorg.presentation.templated.error

import app.mcorg.pipeline.failure.SignOutReason

/** Shown by `/auth/sign-out?error=…` after the session is cleared, so the only way on is to sign in. */
fun signOutPage(reason: SignOutReason): String = errorPageLayout(
    pageTitle = "${reason.heading} · Seam",
    heading = reason.heading,
    body = reason.body,
    ctaText = "Sign in again",
    ctaHref = "/auth/sign-in",
)
