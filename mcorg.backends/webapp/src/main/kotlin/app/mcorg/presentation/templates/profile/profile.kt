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
            checked = true
            disabled = true
            type = InputType.checkBox
        }
    }
    label {
        + "Connected to Discord (Not yet available)"
        input {
            checked = false
            disabled = true
            type = InputType.checkBox
        }
    }
    label {
        + "Connected to MC-ORG Mod (Not yet available)"
        input {
            checked = false
            disabled = true
            type = InputType.checkBox
        }
    }
    label {
        + "Technical player"
        input {
            checked = profile.technicalPlayer
            type = InputType.checkBox
        }
    }
    section {
        a {
            href = "/auth/sign-out"
            button {
                + "Sign out"
            }
        }
        button {
            + "Delete user"
        }
    }
}