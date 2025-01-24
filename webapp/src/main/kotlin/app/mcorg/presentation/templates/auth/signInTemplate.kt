package app.mcorg.presentation.templates.auth

import app.mcorg.presentation.templates.baseTemplate
import kotlinx.html.*

fun signInTemplate(microsoftUrl: String): String = baseTemplate("MC-ORG | Sign In") {
    main {
        id = "sign-in"
        div {
            classes = setOf("sign-in-container")
            h1 {
                id = "sign-in-header"
                + "Welcome to MCORG!"
            }
            p {
                + "This app aims to solve all your project planning issues in Minecraft, along with much else."
            }
            p {
                + "To start, sign in with the personal Microsoft account you have connected to Minecraft, and we will pull up your username to start your planning journey."
            }
            a {
                id = "sign-in-link"
                href = microsoftUrl
                button {
                    id = "microsoft-button"
                    span {
                        classes = setOf("icon", "icon-microsoft")
                    }
                    + "Sign in with Microsoft"
                }
            }
        }
    }
}