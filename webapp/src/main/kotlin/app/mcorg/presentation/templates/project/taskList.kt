package app.mcorg.presentation.templates.project

import app.mcorg.domain.projects.Project
import app.mcorg.domain.users.User
import app.mcorg.presentation.templates.task.countableTask
import app.mcorg.presentation.templates.task.doableTask
import kotlinx.html.*

fun UL.taskList(users: List<User>, currentUser: User, project: Project) {
    id = "task-list"
    project.tasks.forEach {
        li {
            if (it.isCountable()) countableTask(project, it, users, currentUser)
            else doableTask(it, users, currentUser, project)
        }
    }
}