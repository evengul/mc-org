package app.mcorg.presentation.templates.world

import kotlinx.html.MAIN
import kotlinx.html.dialog
import kotlinx.html.form
import kotlinx.html.id

fun MAIN.createWorldDialog(versions: List<String>, playerIsTechnical: Boolean) {
    dialog {
        id = "create-world-dialog"
        form {
            createWorld(versions, playerIsTechnical, canCancel = true)
        }
    }
}