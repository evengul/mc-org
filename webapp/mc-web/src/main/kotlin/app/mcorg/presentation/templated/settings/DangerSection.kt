package app.mcorg.presentation.templated.settings

import app.mcorg.domain.model.world.World
import app.mcorg.presentation.hxDeleteWithConfirm
import app.mcorg.presentation.templated.common.link.Link
import kotlinx.html.*

fun DIV.dangerSection(world: World) {
    div("settings-section settings-section--danger danger-zone") {
        p("danger-zone__title") { +"Danger Zone" }
        p("danger-zone__description subtle") {
            +"Once you delete a world, there is no going back. All projects, tasks, and resources will be permanently deleted."
        }
        div("danger-zone__content") {
            button {
                classes = setOf("btn", "btn--danger")
                type = ButtonType.button
                hxDeleteWithConfirm(
                    url = Link.Worlds.world(world.id).settings().to,
                    title = "Delete World",
                    description = "This action cannot be undone. All projects, tasks, and resources will be permanently deleted.",
                    warning = "Warning: This will permanently delete the world \"${world.name}\" and all associated data.",
                    confirmText = world.name,
                )
                +"Delete World"
            }
        }
    }
}
