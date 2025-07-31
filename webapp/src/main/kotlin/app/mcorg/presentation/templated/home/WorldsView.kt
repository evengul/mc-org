package app.mcorg.presentation.templated.home

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.world.World
import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.component.addComponent
import kotlinx.html.DIV
import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.p
import kotlinx.html.ul

fun DIV.worldsView(worlds: List<World>) {
    id = "home-worlds"
    if (worlds.isEmpty()) {
        div("card stack--sm") {
            div("stack--xxs") {
                h2 {
                    + "No Worlds Yet"
                }
                p("subtle") {
                    + "Get started by creating your first Minecraft world to organize your projects."
                }
            }
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