package app.mcorg.presentation.templated.profile

import app.mcorg.domain.model.user.Profile
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.templated.common.avatar.avatar
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.button.dangerButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.icon.IconColor
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.page.createPage
import kotlinx.html.InputType
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.ul

enum class ProfilePageToggles {
    PROFILE_PICTURE,
    EMAIL,
    CONNECTIONS
}

fun profilePage(
    user: TokenProfile,
    profile: Profile,
    toggles: Set<ProfilePageToggles> = emptySet()
) = createPage("Profile", user = user) {
    classes += "profile-page"
    div("profile-page-header") {
        h1 {
            +"Your Profile"
        }
        p("subtle") {
            +"Manage your account settings and preferences"
        }
    }
    section("profile-information") {
        h2 {
            +"Profile Information"
            p("subtle") {
                +"Update your profile information and how others see you"
            }
            form {
                if (ProfilePageToggles.PROFILE_PICTURE in toggles) {
                    div("profile-picture") {
                        avatar(size = IconSize.MEDIUM, color = IconColor.ON_BACKGROUND)
                        input {
                            type = InputType.file
                        }
                    }
                }
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
                actionButton("Save Changes")
            }
        }
    }
    if (ProfilePageToggles.CONNECTIONS in toggles) {
        section("profile-connections") {
            h2 {
                +"Connected Accounts"
            }
            p("subtle") {
                +"Manage your connected accounts and services"
            }
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
    }
    section("profile-settings") {
        div("header") {
            h2 {
                +"Account settings"
            }
            p("subtle") {
                + "Manage your account settings and preferences"
            }
        }
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
        div("danger-zone") {
            p("danger-zone-title") {
                + "Danger Zone"
            }
            p("subtle") {
                + "Permanently delete your account and all associated data. This action cannot be undone."
            }
            dangerButton("Delete Account") {
                iconLeft = Icons.DELETE
                iconSize = IconSize.SMALL
            }
        }

    }
}