package app.mcorg.presentation.templates.project

import app.mcorg.domain.Project
import app.mcorg.domain.countable
import app.mcorg.domain.doable
import app.mcorg.domain.isDone
import app.mcorg.presentation.templates.baseTemplate
import app.mcorg.presentation.utils.Paths
import kotlinx.html.*

fun project(project: Project): String = baseTemplate {
    nav {
        button {
            + "Back"
        }
        h1 {
            project.name
        }
        span {
            a {
                href = "/app/worlds/${project.worldId}/projects/${project.id}/assign"
                button {
                    + "Assign user"
                }
            }
            a {
                href = "/app/worlds/${project.worldId}/projects/${project.id}/add-task"
                button {
                    + "Add task"
                }
            }
        }
    }
    main {
        ul {
            project.countable().forEach {
                li {
                    div {
                        + it.name
                        button {
                            + "Assign user"
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
                    button {
                        + "Assign user"
                    }
                    input {
                        type = InputType.checkBox
                        checked = it.isDone()
                    }
                }
            }
        }
    }
}