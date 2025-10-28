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
import app.mcorg.presentation.templated.common.searchField.SearchFieldHxValues
import app.mcorg.presentation.templated.common.searchField.searchField
import kotlinx.html.DIV
import kotlinx.html.TagConsumer
import kotlinx.html.UL
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.p
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
            searchField("home-worlds-search-input") {
                placeHolder = "Search worlds by name or description..."
                hxValues = SearchFieldHxValues(
                    hxGet = Link.Worlds.to + "/search",
                    hxTarget = "#home-worlds-list",
                    hxInclude = "#home-worlds-sort-select",
                )
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