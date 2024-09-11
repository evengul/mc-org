package app.mcorg.presentation.templates.world

import app.mcorg.domain.World
import app.mcorg.presentation.*
import app.mcorg.presentation.templates.MainPage
import app.mcorg.presentation.templates.NavBarRightIcon
import app.mcorg.presentation.templates.mainPageTemplate
import kotlinx.html.*

fun worlds(selectedWorldId: Int?, worlds: List<World>): String = mainPageTemplate(
    selectedPage = MainPage.WORLDS,
    selectedWorldId, "Worlds",
    emptyList()
) {
    form {
        method = FormMethod.post
        encType = FormEncType.applicationXWwwFormUrlEncoded
        label {
            htmlFor = "add-world-name-input"
            + "Name of your world"
        }
        input {
            id = "add-world-name-input"
            name = "worldName"
            type = InputType.text
            required = true
            minLength = "3"
            maxLength = "100"
        }
        button {
            id = "add-world-submit-button"
            type = ButtonType.submit
            + "Create"
        }
    }
    ul {
        id = "worlds-list"
        worlds.sortedByDescending { it.id == selectedWorldId }.forEach {
            li {
                id = "world-${it.id}"
                span {
                    classes = setOf("icon-row")
                    hxPatch("/app/worlds/select?worldId=${it.id}")
                    if (it.id == selectedWorldId) {
                        classes += "selected"
                    }
                    span {
                        classes = setOf("icon", "icon-dimension-overworld")
                    }
                    h2 {
                        + it.name
                    }
                }
                button {
                    classes = setOf("button-danger")
                    hxConfirm("Are you sure you want to delete this world? This can not be reverted, and all your projects, tasks and progress will vanish.")
                    hxDelete("/app/worlds/${it.id}")
                    hxTarget("#world-${it.id}")
                    hxSwap("outerHTML")
                    + "Delete"
                }
            }
        }
    }
}