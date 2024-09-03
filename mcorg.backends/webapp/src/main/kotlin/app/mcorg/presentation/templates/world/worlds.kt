package app.mcorg.presentation.templates.world

import app.mcorg.domain.World
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
        if (selectedWorldId != null) {
            val selectedWorld = worlds.find { it.id == selectedWorldId }
            if (selectedWorld != null) {
                li {
                    classes = setOf("selected")
                    span {
                        classes = setOf("icon", "icon-dimension-overworld")
                    }
                    + selectedWorld.name
                }
            }
        }
        for (world in worlds) {
            if (world.id != selectedWorldId) {
                li {
                    span {
                        classes = setOf("icon", "icon-dimension-overworld")
                    }
                    + world.name
                }
            }
        }
    }
}