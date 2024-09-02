package app.mcorg.presentation.templates.profile

import app.mcorg.domain.Profile
import app.mcorg.presentation.templates.mainPageTemplate
import kotlinx.html.*

fun profile(profile: Profile): String = mainPageTemplate(worldId = profile.selectedWorld, title = "Profile", rightIcons = emptyList()) {
    p {
        + profile.username
    }
    label {
        + "Connected to Microsoft"
        input {
            id = "profile-microsoft-connect-input-check"
            checked = true
            disabled = true
            type = InputType.checkBox
        }
    }
    label {
        + "Connected to Discord (Not yet available)"
        input {
            id = "profile-discord-connect-input-check"
            checked = false
            disabled = true
            type = InputType.checkBox
        }
    }
    label {
        + "Connected to MC-ORG Mod (Not yet available)"
        input {
            id = "profile-mod-connect-input-check"
            checked = false
            disabled = true
            type = InputType.checkBox
        }
    }
    label {
        + "Technical player"
        input {
            id = "profile-technical-player-input-check"
            checked = profile.technicalPlayer
            type = InputType.checkBox
        }
    }
    section {
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