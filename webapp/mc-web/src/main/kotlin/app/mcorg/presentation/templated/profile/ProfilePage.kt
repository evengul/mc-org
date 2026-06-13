package app.mcorg.presentation.templated.profile

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.hxDeleteWithConfirm
import app.mcorg.presentation.templated.dsl.appHeader
import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.dangerZone
import app.mcorg.presentation.templated.dsl.pageHeading
import app.mcorg.presentation.templated.dsl.pageShell
import app.mcorg.presentation.templated.dsl.section
import kotlinx.html.ButtonType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.main

fun profilePage(user: TokenProfile): String = pageShell(
    pageTitle = "Seam — Profile",
    user = user,
    stylesheets = listOf(
        "/static/styles/components/danger-zone.css",
        "/static/styles/pages/profile-page.css",
    )
) {
    appHeader(
        user = user,
        breadcrumbBlock = {
            current("Profile")
        }
    )
    main {
        container {
            pageHeading(title = "${user.minecraftUsername}'s Profile")
            div("profile-page__sections") {
                accountSection()
                deleteAccountSection()
            }
        }
    }
}

private fun kotlinx.html.FlowContent.accountSection() {
    section(
        eyebrow = "ACCOUNT",
        subtitle = "Sign out of your account on this device. You will need to sign in again to access your worlds and projects.",
        tight = true,
    ) {
        a(classes = "btn btn--secondary") {
            href = "/auth/sign-out"
            +"Sign out"
        }
    }
}

private fun kotlinx.html.FlowContent.deleteAccountSection() {
    dangerZone(
        description = "Permanently delete your account and all associated data. This action cannot be undone.",
    ) {
        button {
            classes = setOf("btn", "btn--danger")
            type = ButtonType.button
            hxDeleteWithConfirm(
                url = "/account",
                title = "Delete Account",
                description = "Are you sure you want to delete your account? This action cannot be undone.",
                warning = "This will permanently delete your account and all associated data, including your worlds and projects.",
                confirmText = "DELETE MY ACCOUNT"
            )
            +"Delete account"
        }
    }
}
