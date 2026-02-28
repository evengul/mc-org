package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import kotlinx.html.*

fun HEADER.ideasHeader(user: TokenProfile) {
    id = "ideas-header"
    div {
        id = "ideas-header-start"
        h1 {
            +"Idea Bank"
        }
        p {
            classes += "subtle"
            +"Browse and share Minecraft contraption ideas with the community"
        }
    }
    div {
        id = "ideas-header-end"
        if (user.isIdeaCreator) {
            actionButton("Submit new idea") {
                iconLeft = Icons.MENU_ADD
                iconSize = IconSize.SMALL
                href = Link.Ideas.to + "/create"
            }
        }
    }
}

