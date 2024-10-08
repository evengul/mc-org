package app.mcorg.presentation.templates.world

import app.mcorg.domain.GameType
import app.mcorg.domain.World
import app.mcorg.domain.toVersionString
import app.mcorg.presentation.*
import app.mcorg.presentation.templates.MainPage
import app.mcorg.presentation.templates.mainPageTemplate
import kotlinx.html.*

fun worlds(selectedWorldId: Int?, worlds: List<World>, versions: List<String>, playerIsTechnical: Boolean): String = mainPageTemplate(
    selectedPage = MainPage.WORLDS,
    worldId = selectedWorldId,
    title = "Worlds",
) {
    if (worlds.isEmpty()) {
        p {
            id = "worlds-info"
            + ("MC-ORG consists of worlds, which again contain projects. " +
                    "These worlds represent your actual Minecraft world.")
        }
        form {
            createWorld(versions, playerIsTechnical, canCancel = false)
        }
    } else {
        button {
            id = "show-create-world-dialog-button"
            onClick = "showDialog('create-world-dialog')"
            classes = setOf("button", "button-icon", "button-fab", "icon-menu-add")
        }
        createWorldDialog(versions, playerIsTechnical)
    }
    ul {
        id = "worlds-list"
        worlds.sortedByDescending { it.id == selectedWorldId }.forEach {
            li {
                id = "world-${it.id}"
                span {
                    classes = setOf("world-title-delete")
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
                        classes = setOf("icon-row button button-icon icon-small icon-delete-small delete-project-button")
                        hxConfirm("Are you sure you want to delete this world? This can not be reverted, and all your projects, tasks and progress will vanish.")
                        hxDelete("/app/worlds/${it.id}")
                        hxTarget("#world-${it.id}")
                        hxSwap("outerHTML")
                    }
                }
                p {
                    + "Version: ${it.version.toVersionString()}"
                }
                p {
                    + "Game type: ${it.gameType.presentable()}"
                }
                if (it.isTechnical) {
                    p {
                        + "Technical world"
                    }
                }
            }
        }
    }
}

fun GameType.presentable() = when(this) {
    GameType.JAVA -> "Java"
    GameType.BEDROCK -> "Bedrock"
}