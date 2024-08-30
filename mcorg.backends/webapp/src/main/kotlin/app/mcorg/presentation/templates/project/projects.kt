package app.mcorg.presentation.templates.project

import app.mcorg.domain.SlimProject
import app.mcorg.presentation.templates.baseTemplate
import kotlinx.html.*

fun projects(projects: List<SlimProject>): String = baseTemplate("MC-ORG - Projects") {
    nav {
        button {
            + "Menu"
        }
        h1 {
            + "Projects"
        }
        button {
            + "Add"
        }
    }
    main {
        if (projects.isEmpty()) {
            + "No projects? Add one with the button above."
        }
        ul {
            for (project in projects) {
                li {
                    div {
                        + (project.priority.name + " | " + project.dimension.name + " | " + project.name)
                    }
                    div {
                        if (project.assignee == null) {
                            + "Assign user"
                        } else {
                            + project.assignee.username
                        }
                    }
                    progress {
                        max = "100"
                        value = project.progress.toString()
                    }
                }
            }
        }
    }
}