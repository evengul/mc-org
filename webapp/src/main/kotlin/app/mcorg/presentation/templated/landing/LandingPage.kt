package app.mcorg.presentation.templated.landing

import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.icon.IconColor
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.icon.iconComponent
import app.mcorg.presentation.templated.common.page.createPage
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.ul

fun landingPage(microsoftUrl: String) = createPage {
    classes += "landing-page"
    h1 {
        + "Organize Your Minecraft Projects"
    }
    p {
        + "Plan, track and collaborate on your Minecraft builds, farms and redstone contraptions"
    }
    div {
        classes += "sign-in-container"
        actionButton("Sign in with Microsoft") {
            classes += "microsoft-sign-in"
            iconLeft = Icons.MICROSOFT_LOGO
            href = microsoftUrl
        }
        p {
            + "Sign in with your Microsoft account to start organizing your Minecraft projects."
        }
    }
    ul {
        classes += "landing-features"
        li {
            iconComponent(Icons.Priority.HIGH, color = IconColor.ON_BACKGROUND)
            p("card-title") {
                + "Organize Projects"
            }
            p("card-description") {
                + "Keep track of all your Minecraft projects, from simple builds to complex redstone contraptions."
            }
        }
        li {
            iconComponent(Icons.Users.GROUP, color = IconColor.ON_BACKGROUND)
            p("card-title") {
                + "Collaborate"
            }
            p("card-description") {
                + "Invite friends to collaborate on your worlds and projects, assigning tasks and tracking progress together."
            }
        }
        li {
            iconComponent(Icons.Menu.UTILITIES, color = IconColor.ON_BACKGROUND)
            p("card-title") {
                + "Resource Management"
            }
            p("card-description") {
                + "Track resource locations, manage farms, and plan your resource gathering efficiently."
            }
        }
    }
}
