package app.mcorg.presentation.templates.task.board

import app.mcorg.domain.projects.Project
import app.mcorg.domain.projects.Task
import app.mcorg.domain.projects.TaskStage
import app.mcorg.domain.projects.TaskStages
import kotlinx.html.*
import kotlinx.html.stream.createHTML

fun createTaskBoard(project: Project) = createHTML().main {
    taskBoard(project)
}

fun MAIN.taskBoard(project: Project) {
    div {
        id = "task-board"
        section {
            id = "doable-tasks"
            h2 {
                + "Doable Tasks"
            }
            div {
                classes = setOf("task-columns")
                taskColumn(project.doable().filter { it.stage == TaskStages.TODO }, TaskStages.TODO)
                taskColumn(project.doable().filter { it.stage == TaskStages.IN_PROGRESS }, TaskStages.IN_PROGRESS)
                taskColumn(project.doable().filter { it.stage == TaskStages.DONE }, TaskStages.DONE)
            }
        }
        section {
            id = "countable-tasks"
            h2 {
                + "Countable Tasks"
            }
            div {
                classes = setOf("task-columns")
                taskColumn(project.countable().filter { it.stage == TaskStages.TODO }, TaskStages.TODO)
                taskColumn(project.countable().filter { it.stage == TaskStages.IN_PROGRESS }, TaskStages.IN_PROGRESS)
                taskColumn(project.countable().filter { it.stage == TaskStages.DONE }, TaskStages.DONE)
            }
        }
    }
}

private fun DIV.taskColumn(tasks: List<Task>, stage: TaskStage) {
    div {
        id = stage.id
        classes = setOf("task-column")
        h3 {
            + stage.name
        }
        ul {
            tasks.forEach {
                li {
                    id = "task-${it.id}"
                    classes = setOf("task")
                    attributes["draggable"] = "true"
                    attributes["ondragstart"] = "onDragStart(event)"
                    attributes["ondragover"] = "onDragOver(event)"
                    attributes["ondrop"] = "onDrop(event)"
                    + it.name
                }
            }
        }
    }
}