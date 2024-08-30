package app.mcorg.presentation.templates.task

import app.mcorg.presentation.templates.baseTemplate
import kotlinx.html.*

fun addTask(worldId: Int, projectId: Int) = baseTemplate {
    nav {
        button {
            + "Back"
        }
        h1 {
            + "Add task"
        }
    }
    main {
        a {
            href = "/app/worlds/$worldId/projects/$projectId/add-task/doable"
            button {
                + "Add doable"
            }
        }
        a {
            href = "/app/worlds/$worldId/projects/$projectId/add-task/countable"
            button {
                + "Add countable"
            }
        }
    }
}