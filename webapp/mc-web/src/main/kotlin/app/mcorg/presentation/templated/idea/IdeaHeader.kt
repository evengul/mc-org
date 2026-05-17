package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.templated.dsl.actionButton
import app.mcorg.presentation.templated.dsl.IconSize
import app.mcorg.presentation.templated.dsl.Icons
import app.mcorg.presentation.templated.dsl.Link
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

