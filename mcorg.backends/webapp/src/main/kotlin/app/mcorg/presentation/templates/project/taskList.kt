package app.mcorg.presentation.templates.project

import app.mcorg.domain.Project
import app.mcorg.domain.User
import app.mcorg.domain.isCountable
import app.mcorg.presentation.templates.task.countableTask
import app.mcorg.presentation.templates.task.doableTask
import kotlinx.html.*

fun MAIN.taskList(users: List<User>, currentUser: User, project: Project) {
    ul {
        id = "task-list"
        project.tasks.forEach {
            li {
                if (it.isCountable()) countableTask(project, it, users, currentUser)
                else doableTask(it, users, currentUser, project)
            }
        }
    }
}