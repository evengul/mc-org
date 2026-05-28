package app.mcorg.presentation.templated.settings

import app.mcorg.domain.model.world.World
import app.mcorg.presentation.hxDeleteWithConfirm
import app.mcorg.presentation.templated.dsl.Link
import app.mcorg.presentation.templated.dsl.dangerZone
import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.button
import kotlinx.html.classes

fun DIV.dangerSection(world: World) {
    dangerZone(
        description = "Once you delete a world, there is no going back. All projects, tasks, and resources will be permanently deleted.",
    ) {
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
