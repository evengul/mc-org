package app.mcorg.presentation.templated.home

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.world.World
import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.component.addComponent
import kotlinx.html.TagConsumer
import kotlinx.html.UL
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.ul

fun UL.worldsListView(worlds: List<World>) {
    id = "home-worlds-list"
    worlds.forEach {
        addComponent(WorldView(world = it))
    }
}

class WorldsView(
    val worlds: List<World>
) : LeafComponent() {
    override fun render(container: TagConsumer<*>) {
        container.div {
            id = "home-worlds"
            div("home-worlds-search-create") {
                input {
                    placeholder = "Search worlds..."
                }
                createWorldModal(MinecraftVersion.supportedVersions.filterIsInstance<MinecraftVersion.Release>())
            }
            ul {
                worldsListView(worlds)
            }
        }
    }
}