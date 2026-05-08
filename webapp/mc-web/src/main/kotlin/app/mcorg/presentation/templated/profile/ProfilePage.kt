package app.mcorg.presentation.templated.profile

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.hxDeleteWithConfirm
import app.mcorg.presentation.templated.dsl.appHeader
import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.dangerZone
import app.mcorg.presentation.templated.dsl.pageHeading
import app.mcorg.presentation.templated.dsl.pageShell
import kotlinx.html.ButtonType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.main
import kotlinx.html.p
import kotlinx.html.section

fun profilePage(user: TokenProfile): String = pageShell(
    pageTitle = "MC-ORG — Profile",
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
    section("profile-section") {
        div("profile-section__heading") {
            h2("section-label") { +"ACCOUNT" }
        }
        p("profile-section__body") {
            +"Sign out of your account on this device. You will need to sign in again to access your worlds and projects."
        }
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
