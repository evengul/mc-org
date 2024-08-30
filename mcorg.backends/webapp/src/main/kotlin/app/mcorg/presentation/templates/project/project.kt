package app.mcorg.presentation.templates.project

import app.mcorg.domain.Project
import app.mcorg.domain.countable
import app.mcorg.domain.doable
import app.mcorg.domain.isDone
import app.mcorg.presentation.templates.baseTemplate
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
            button {
                + "Assign project"
            }
            button {
                + "Add task"
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