package app.mcorg.presentation.templates.profile

import app.mcorg.domain.users.Profile
import app.mcorg.presentation.hxConfirm
import app.mcorg.presentation.hxDelete
import app.mcorg.presentation.templates.MainPage
import app.mcorg.presentation.templates.mainPageTemplate
import kotlinx.html.*

fun profile(profile: Profile): String = mainPageTemplate(
    selectedPage = MainPage.PROFILE,
    worldId = profile.selectedWorld,
    title = "Profile",
) {
    section {
        id = "profile-section-user"
        classes = setOf("profile-section")
        label {
            input {
                type = InputType.file
            }
            + "Upload profile picture"
        }
        p {
            + "Username: ${profile.username}"
        }
    }
    section {
        id = "profile-section-connections"
        classes = setOf("profile-section")
        label {
            htmlFor = "profile-microsoft-connect-input-check"
            + "Connected to Microsoft"
        }
        input {
            id = "profile-microsoft-connect-input-check"
            checked = true
            disabled = true
            type = InputType.checkBox
        }
        label {
            htmlFor = "profile-discord-connect-input-check"
            + "Connected to Discord (Not yet available)"
        }
        input {
            id = "profile-discord-connect-input-check"
            checked = false
            disabled = true
            type = InputType.checkBox
        }
        label {
            htmlFor = "profile-mod-connect-input-check"
            + "Connected to MC-ORG Mod (Not yet available)"
        }
        input {
            id = "profile-mod-connect-input-check"
            checked = false
            disabled = true
            type = InputType.checkBox
        }
        label {
            htmlFor = "profile-technical-player-input-check"
            + "Technical player"
        }
        input {
            isTechnicalCheckBox(profile.technicalPlayer)
        }
    }
    section {
        classes = setOf("profile-section")
        a {
            id = "profile-sign-out-link"
            href = "/auth/sign-out"
            button {
                classes = setOf("button-secondary")
                + "Sign out"
            }
        }
        button {
            id = "profile-delete-user-button"
            classes = setOf("button-danger")
            hxDelete("/auth/user")
            hxConfirm("Are you sure you want to delete your user? This can not be reverted, and all your worlds and projects will vanish.")
            + "Delete user"
        }
    }
}