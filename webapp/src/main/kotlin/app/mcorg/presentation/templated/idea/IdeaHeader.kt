package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.templated.common.modal.formModal
import kotlinx.html.DIV
import kotlinx.html.HEADER
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.id
import kotlinx.html.p

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
            createIdeaModal()
        }
    }
}

fun DIV.createIdeaModal() = formModal(
    modalId = "ideas-create-modal",
    title = "Submit New Idea",
)

