package app.mcorg.presentation.templated.home

import app.mcorg.domain.model.world.World
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.chip.ChipColor
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.progress.progressComponent
import app.mcorg.presentation.templated.utils.SimpleDateFormat
import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.span

class WorldView(
    val world: World
) : LeafComponent() {
    override fun render(container: TagConsumer<*>) {
        container.li("home-world-item") {
            div("home-world-item-header") {
                h2 {
                    + world.name
                }
                chipComponent {
                    classes += "subtle"
                    icon = Icons.Dimensions.OVERWORLD
                    color = ChipColor.ACTIVE
                    + "MC ${world.version}"
                }
            }
            p("world-description subtle") {
                + world.description
            }
            div("home-world-item-statistics") {
                p("subtle") {
                    + "${world.completedProjects} of ${world.totalProjects} projects completed"
                }
                p("subtle") {
                    +"Created at: ${world.createdAt.format(SimpleDateFormat.INSTANCE)}"
                }
            }
            progressComponent {
                value = world.completedProjects.toDouble()
                max = world.totalProjects.toDouble()
            }
            actionButton("View World")
        }
    }
}