package app.mcorg.presentation.templates.contraptions

import app.mcorg.domain.Contraption
import app.mcorg.presentation.templates.MainPage
import app.mcorg.presentation.templates.mainPageTemplate
import kotlinx.html.*

fun contraptions(worldId: Int?, contraptions: List<Contraption>) = mainPageTemplate(
    title = "Contraptions",
    selectedPage = MainPage.CONTRAPTIONS,
    worldId = worldId
) {
    createContraptionDialog()
    button {
        id = "show-create-contraption-dialog-button"
        onClick = "showDialog('create-contraption-dialog')"
        classes = setOf("button", "button-icon", "button-fab", "icon-menu-add")
    }
    ul {
        id = "contraptions-list"
        contraptions.forEach {
            li {
                contraptionListElement(it)
            }
        }
    }
}