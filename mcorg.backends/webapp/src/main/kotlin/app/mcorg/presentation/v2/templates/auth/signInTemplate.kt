package app.mcorg.presentation.v2.templates.auth

import app.mcorg.presentation.v2.templates.baseTemplate
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.h1
import kotlinx.html.p

fun signInTemplate(microsoftUrl: String): String = baseTemplate("MC-ORG | Sign In") {
    h1 {
        + "Welcome to MCORG!"
    }
    p {
        + "This app aims to solve all your project planning issues in Minecraft, along with much else."
    }
    p {
        + "To start, connect with the Microsoft account you have connected to Minecraft, and we will pull up your username to start your planning journey."
    }
    a {
        href = microsoftUrl
        button {
            + "Sign in with Microsoft"
        }
    }
}