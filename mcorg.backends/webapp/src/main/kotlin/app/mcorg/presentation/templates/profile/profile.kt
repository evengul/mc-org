package app.mcorg.presentation.templates.profile

import app.mcorg.domain.Profile
import app.mcorg.presentation.templates.mainPageTemplate
import kotlinx.html.*

fun profile(profile: Profile): String = mainPageTemplate(worldId = profile.selectedWorld, title = "Profile", rightIcons = emptyList()) {
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
            + profile.username
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
            id = "profile-technical-player-input-check"
            checked = profile.technicalPlayer
            type = InputType.checkBox
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
            + "Delete user"
        }
    }
}