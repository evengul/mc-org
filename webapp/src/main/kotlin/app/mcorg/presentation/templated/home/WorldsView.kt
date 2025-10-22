package app.mcorg.presentation.templated.home

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.world.World
import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.component.addComponent
import app.mcorg.presentation.templated.common.emptystate.emptyState
import app.mcorg.presentation.templated.common.icon.Icons
import kotlinx.html.DIV
import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.ul

fun DIV.worldsView(worlds: List<World>) {
    id = "home-worlds"
    if (worlds.isEmpty()) {
        emptyState(
            id = "empty-worlds-state",
            title = "No Worlds Yet",
            description = "Get started by creating your first Minecraft world to organize your projects.",
            icon = Icons.ADD_WORLD
        ) {
            createWorldModal(MinecraftVersion.supportedVersions.filterIsInstance<MinecraftVersion.Release>())
        }
    } else {
        div("home-worlds-search-create") {
            input {
                placeholder = "Search worlds..."
            }
            createWorldModal(MinecraftVersion.supportedVersions.filterIsInstance<MinecraftVersion.Release>())
        }
        ul {
            id = "home-worlds-list"
            worlds.forEach {
                addComponent(WorldView(world = it))
            }
        }
    }
}

class WorldsView(
    val worlds: List<World>
) : LeafComponent() {
    override fun render(container: TagConsumer<*>) {
        container.div {
            worldsView(worlds)
        }
    }
}