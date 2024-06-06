package app.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*

fun profilePage(username: String, userId: Int): String {
    return page {
        p {
            + "Your username is $username."
        }
        button {
            id = "change-username-for-$userId"
            disabled = true
            type = ButtonType.button
            + "Change username (not yet possible)"
        }
        div {
            a {
                href = "/signout"
                + "Sign out"
            }
        }
    }
}