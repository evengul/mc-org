package no.mcorg.presentation.htmx.templates.pages

import kotlinx.html.*
import no.mcorg.domain.Project
import no.mcorg.domain.isDone

fun projectPage(project: Project): String {
    return page(title = project.name) {
        h2 {
            + "This is a project you have created"
        }
        button {
            type = ButtonType.button
            + "Add task to project"
        }
        ul {
            for (task in project.tasks.sortedByDescending { it.isDone() }) {
                li {
                    val doneText = if(task.isDone()) "Done"
                                    else "${(task.done.toDouble() / task.needed.toDouble()) * 100}%"
                    + "${task.name} ($doneText)"
                    button {
                        type = ButtonType.button
                        + "Delete"
                    }
                }
            }
        }
    }
}