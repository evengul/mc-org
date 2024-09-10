package app.mcorg.presentation.templates.world

import app.mcorg.domain.World
import app.mcorg.presentation.hxDelete
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.templates.MainPage
import app.mcorg.presentation.templates.NavBarRightIcon
import app.mcorg.presentation.templates.mainPageTemplate
import kotlinx.html.*

fun worlds(selectedWorldId: Int?, worlds: List<World>): String = mainPageTemplate(
    selectedPage = MainPage.WORLDS,
    selectedWorldId, "Worlds", listOf(
    NavBarRightIcon("world-add", "Add world", "/app/worlds/add")
)) {
    ul {
        id = "worlds-list"
        worlds.sortedByDescending { it.id == selectedWorldId }.forEach {
            li {
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
                    hxDelete("/app/worlds/${it.id}")
                    + "Delete"
                }
            }
        }
    }
}