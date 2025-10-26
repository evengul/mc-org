package app.mcorg.presentation.templated.home

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.world.World
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxInclude
import app.mcorg.presentation.hxIndicator
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTrigger
import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.component.addComponent
import app.mcorg.presentation.templated.common.emptystate.emptyState
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import kotlinx.html.DIV
import kotlinx.html.InputType
import kotlinx.html.TagConsumer
import kotlinx.html.UL
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.ul

fun DIV.worldsView(worlds: List<World>, supportedVersions: List<MinecraftVersion.Release>) {
    id = "home-worlds"
    if (worlds.isEmpty()) {
        emptyState(
            id = "empty-worlds-state",
            title = "No Worlds Yet",
            description = "Get started by creating your first Minecraft world to organize your projects.",
            icon = Icons.ADD_WORLD
        ) {
            createWorldModal(supportedVersions)
        }
    } else {
        div("home-worlds-search-create") {
            div("search-wrapper") {
                input {
                    id = "home-worlds-search-input"
                    type = InputType.search
                    placeholder = "Search worlds..."
                    name = "query"

                    hxGet(Link.Worlds.to + "/search")
                    hxInclude("#home-worlds-sort-select")
                    hxTarget("#home-worlds-list")
                    hxSwap("outerHTML")
                    hxTrigger("input changed delay:500ms, change changed, search")
                    hxIndicator(".search-wrapper")
                }
            }
            select {
                id = "home-worlds-sort-select"
                name = "sortBy"
                hxGet(Link.Worlds.to + "/search")
                hxInclude("#home-worlds-search-input")
                hxTarget("#home-worlds-list")
                hxSwap("outerHTML")
                hxTrigger("change")
                hxIndicator(".search-wrapper")
                option {
                    selected = true
                    value = "modified_desc"
                    + "Sort by Last Modified"
                }
                option {
                    value = "name_asc"
                    + "Sort by Name (A-Z)"
                }
            }
            createWorldModal(supportedVersions)
        }
        p("subtle") {
            id = "home-worlds-count"
            + "Showing ${worlds.size} of ${worlds.size} world${if(worlds.size == 1) "" else "s"}."
        }
        ul {
            worldList(worlds)
        }
    }
}

fun UL.worldList(worlds: List<World>) {
    id = "home-worlds-list"
    worlds.forEach {
        addComponent(WorldView(world = it))
    }
}

class WorldsView(
    val worlds: List<World>,
    val supportedVersions: List<MinecraftVersion.Release>
) : LeafComponent() {
    override fun render(container: TagConsumer<*>) {
        container.div {
            worldsView(worlds, supportedVersions)
        }
    }
}