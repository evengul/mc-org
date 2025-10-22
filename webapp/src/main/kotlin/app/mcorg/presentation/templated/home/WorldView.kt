package app.mcorg.presentation.templated.home

import app.mcorg.domain.model.world.World
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.chip.ChipSize
import app.mcorg.presentation.templated.common.chip.actionChip
import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.progress.progressComponent
import app.mcorg.presentation.templated.utils.formatAsRelativeOrDate
import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p

class WorldView(
    val world: World
) : LeafComponent() {
    override fun render(container: TagConsumer<*>) {
        container.li("home-world-item") {
            div("home-world-item-header") {
                h2 {
                    + world.name
                }
                actionChip(text = "MC ${world.version}", icon = Icons.Dimensions.OVERWORLD, size = ChipSize.SMALL)
            }
            p("world-description subtle") {
                + world.description
            }
            div("home-world-item-statistics") {
                p("subtle") {
                    +"Created: ${world.createdAt.formatAsRelativeOrDate()}"
                }
            }
            progressComponent {
                value = world.completedProjects.toDouble()
                max = world.totalProjects.toDouble()
                label = "${world.completedProjects} of ${world.totalProjects} project${if (world.totalProjects == 1) "" else "s"} completed"
                showPercentage = false
            }
            actionButton("View World") {
                href = Link.Worlds.world(world.id).to
            }
        }
    }
}