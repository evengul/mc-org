package app.mcorg.presentation.templated.profile

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.templated.common.button.dangerButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.dangerzone.dangerZone
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.page.createPage
import kotlinx.html.*

fun profilePage(
    user: TokenProfile,
    unreadNotificationCount: Int = 0,
) = createPage(
    pageTitle = "Profile", 
    user = user,
    unreadNotificationCount = unreadNotificationCount
) {
    classes += "profile-page"

    h1 {
        + "${user.minecraftUsername}'s Profile"
    }
    section("sign-out") {
        p("subtle") {
            + "Sign out of your account on this device. You will need to sign in again to access your worlds and projects."
        }
        neutralButton("Sign Out") {
            href = "/auth/sign-out"
        }
    }
    dangerZone(description = "Permanently delete your account and all associated data. This action cannot be undone.") {
        dangerButton("Delete Account") {
            iconLeft = Icons.DELETE
            iconSize = IconSize.SMALL
        }
    }
}