package app.mcorg.presentation.templates.project

import app.mcorg.domain.projects.Project
import app.mcorg.presentation.templates.task.addCountableDialog
import app.mcorg.presentation.templates.task.addDoableTaskDialog
import app.mcorg.presentation.templates.task.addLitematicaTasksDialog
import io.ktor.util.*
import kotlinx.html.*

fun MAIN.addTaskButtons(project: Project) {
    addCountableDialog(project)
    addDoableTaskDialog(project)
    addLitematicaTasksDialog(project)
    div {
        id = "project-add-task-buttons"
        script {
            nonce = generateNonce()
            src = "/static/scripts/activateTaskButtons.js"
        }
        button {
            id = "add-task-button"
            classes = setOf("button", "button-icon", "button-fab", "icon-menu-add")
            onClick = "activateTaskButtons()"
        }
        div {
            id = "hidden-task-buttons"
            classes = setOf("hidden")
            button {
                classes = setOf("button-primary")
                onClick = "showDialog('add-task-doable-dialog'); hideTaskButtons()"
                span { classes = setOf("icon", "icon-menu-add") }
                + "Add doable tasks"
            }
            button {
                classes = setOf("button-primary")
                onClick = "showDialog('add-task-countable-dialog'); hideTaskButtons()"
                span { classes = setOf("icon", "icon-menu-add") }
                + "Add countable tasks"
            }
            button {
                classes = setOf("button-primary")
                onClick = "showDialog('add-task-litematica-dialog'); hideTaskButtons()"
                span { classes = setOf("icon", "icon-menu-add") }
                + "Add from litematica"
            }
        }
    }
}