package app.mcorg.presentation.templated.home

import app.mcorg.domain.model.world.World
import app.mcorg.presentation.templated.common.button.primaryButton
import app.mcorg.presentation.templated.common.chip.ChipColor
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.progress.progressComponent
import app.mcorg.presentation.templated.utils.SimpleDateFormat
import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.p

class WorldView(
    val world: World
) : LeafComponent() {
    override fun render(container: TagConsumer<*>) {
        container.div {
            h2 {
                + world.name
            }
            p("world-description") {
                + world.description
            }
            chipComponent {
                icon = Icons.Dimensions.OVERWORLD
                color = ChipColor.PRIMARY
                + "MC ${world.version}"
            }
            p {
                + "${world.completedProjects} of ${world.totalProjects} projects completed"
            }
            p {
                +"Created at: ${world.createdAt.format(SimpleDateFormat.INSTANCE)}"
            }
            progressComponent {
                value = world.completedProjects.toDouble()
                max = world.totalProjects.toDouble()
            }
            primaryButton("View World")
        }
    }
}