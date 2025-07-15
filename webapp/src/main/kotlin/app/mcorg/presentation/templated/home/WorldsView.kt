package app.mcorg.presentation.templated.home

import app.mcorg.presentation.entities.world.SlimWorld
import app.mcorg.presentation.templated.common.button.primaryButton
import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.component.addComponent
import app.mcorg.presentation.templated.common.icon.Icons
import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.input
import kotlinx.html.ul

class WorldsView(
    val worlds: List<SlimWorld>
) : LeafComponent() {
    override fun render(container: TagConsumer<*>) {
        container.div {
            container.div {
                input {
                    placeholder = "Search worlds..."
                }
                primaryButton("Create world") {
                    iconLeft = Icons.ADD_WORLD
                }
            }
            container.ul {
                worlds.forEach {
                    addComponent(SlimWorldView(world = it))
                }
            }
        }
    }
}