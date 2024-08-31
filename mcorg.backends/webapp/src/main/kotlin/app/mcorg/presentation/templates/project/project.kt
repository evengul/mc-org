package app.mcorg.presentation.templates.project

import app.mcorg.domain.Project
import app.mcorg.domain.countable
import app.mcorg.domain.doable
import app.mcorg.domain.isDone
import app.mcorg.presentation.templates.NavBarRightIcon
import app.mcorg.presentation.templates.subPageTemplate
import kotlinx.html.*

fun project(backLink: String, project: Project): String = subPageTemplate(project.name, backLink = backLink, listOf(
    NavBarRightIcon("", "Assign user", "/app/worlds/${project.worldId}/projects/${project.id}/assign"),
    NavBarRightIcon("", "Add task", "/app/worlds/${project.worldId}/projects/${project.id}/add-task")
)) {
    ul {
        project.countable().forEach {
            li {
                div {
                    + it.name
                    a {
                        href = "/app/worlds/${project.worldId}/projects/${project.id}/tasks/${it.id}/assign"
                        button {
                            + "Assign user"
                        }
                    }
                }
                progress {
                    max = it.needed.toString()
                    value = it.done.toString()
                }
            }
        }
        project.doable().forEach {
            li {
                div {
                    + it.name
                }
                a {
                    href = "/app/worlds/${project.worldId}/projects/${project.id}/tasks/${it.id}/assign"
                    button {
                        + "Assign user"
                    }
                }
                input {
                    type = InputType.checkBox
                    checked = it.isDone()
                }
            }
        }
    }
}