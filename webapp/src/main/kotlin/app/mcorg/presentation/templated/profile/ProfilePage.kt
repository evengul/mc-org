package app.mcorg.presentation.templated.profile

import app.mcorg.domain.model.user.Profile
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.templated.common.avatar.avatar
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.button.dangerButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.dangerzone.dangerZone
import app.mcorg.presentation.templated.common.icon.IconColor
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.page.createPage
import kotlinx.html.*
import kotlinx.html.InputType

enum class ProfilePageToggles {
    PROFILE_PICTURE,
    EMAIL,
    CONNECTIONS
}

fun profilePage(
    user: TokenProfile,
    profile: Profile,
    unreadNotificationCount: Int = 0,
    toggles: Set<ProfilePageToggles> = emptySet()
) = createPage(
    pageTitle = "Profile", 
    user = user,
    unreadNotificationCount = unreadNotificationCount
) {
    classes += "profile-page"

    profilePageHeader()
    profileInformationSection(user, profile, toggles)
    if (ProfilePageToggles.CONNECTIONS in toggles) {
        connectedAccountsSection(profile)
    }
    accountSettingsSection()
}

private fun MAIN.profilePageHeader() {
    div("profile-page-header") {
        h1 {
            +"Your Profile"
        }
        p("subtle") {
            +"Manage your account settings and preferences"
        }
    }
}

private fun MAIN.profileInformationSection(
    user: TokenProfile,
    profile: Profile,
    toggles: Set<ProfilePageToggles>
) {
    section("profile-information") {
        h2 {
            +"Profile Information"
            p("subtle") {
                +"Update your profile information and how others see you"
            }
            form {
                profilePictureField(toggles)
                profileFormFields(user, profile, toggles)
                actionButton("Save Changes")
            }
        }
    }
}

private fun FlowContent.profilePictureField(toggles: Set<ProfilePageToggles>) {
    if (ProfilePageToggles.PROFILE_PICTURE in toggles) {
        div("profile-picture") {
            avatar(size = IconSize.MEDIUM, color = IconColor.ON_BACKGROUND)
            input {
                type = InputType.file
            }
        }
    }
}

private fun FlowContent.profileFormFields(
    user: TokenProfile,
    profile: Profile,
    toggles: Set<ProfilePageToggles>
) {
    label {
        +"Display Name"
    }
    input {
        value = user.displayName
    }
    if (ProfilePageToggles.EMAIL in toggles) {
        label {
            +"Email"
        }
        input {
            type = InputType.email
            value = profile.email
        }
    }
    label {
        +"Minecraft Username"
    }
    input {
        value = user.minecraftUsername
        disabled = true
    }
}

private fun MAIN.connectedAccountsSection(profile: Profile) {
    section("profile-connections") {
        connectedAccountsHeader()
        connectedAccountsList(profile)
    }
}

private fun FlowContent.connectedAccountsHeader() {
    h2 {
        +"Connected Accounts"
    }
    p("subtle") {
        +"Manage your connected accounts and services"
    }
}

private fun FlowContent.connectedAccountsList(profile: Profile) {
    ul {
        li {
            p {
                +"Discord Account"
            }
            p("subtle") {
                if (profile.discordConnection) {
                    +"Connected"
                } else {
                    +"Not connected"
                }
            }
        }
        li {
            p {
                +"Microsoft Account"
            }
            p("subtle") {
                if (profile.microsoftConnection) {
                    +"Connected"
                } else {
                    +"Not connected"
                }
            }
        }
    }
}

private fun MAIN.accountSettingsSection() {
    section("profile-settings") {
        accountSettingsHeader()
        signOutSection()
        dangerZone(description = "Permanently delete your account and all associated data. This action cannot be undone.") {
            dangerButton("Delete Account") {
                iconLeft = Icons.DELETE
                iconSize = IconSize.SMALL
            }
        }
    }
}

private fun FlowContent.accountSettingsHeader() {
    div("header") {
        h2 {
            +"Account settings"
        }
        p("subtle") {
            + "Manage your account settings and preferences"
        }
    }
}

private fun FlowContent.signOutSection() {
    div("sign-out") {
        p {
            + "Sign Out"
        }
        p("subtle") {
            + "Sign out of your account on this device. You will need to sign in again to access your worlds and projects."
        }
        neutralButton("Sign Out") {
            href = "/auth/sign-out"
        }
    }
}