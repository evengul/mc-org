package app.mcorg.presentation.templates.profile

import app.mcorg.domain.Profile
import app.mcorg.presentation.templates.baseTemplate
import kotlinx.html.*

fun profile(profile: Profile): String = baseTemplate {
    nav {
        button {
            + "Menu"
        }
        h1 {
            + "Profile"
        }
    }
    main {
        p {
            + profile.username
        }
        label {
            + "Connected to Microsoft"
            input {
                checked = true
                type = InputType.checkBox
            }
        }
        label {
            + "Connected to Discord"
            input {
                checked = false
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
            button {
                + "Sign out"
            }
            button {
                + "Delete user"
            }
        }
    }
}