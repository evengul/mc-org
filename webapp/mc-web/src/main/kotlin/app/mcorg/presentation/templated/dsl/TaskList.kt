package app.mcorg.presentation.templated.dsl

import app.mcorg.domain.model.task.ActionTask
import app.mcorg.presentation.hxDeleteWithConfirm
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import kotlinx.html.*
import kotlinx.html.stream.createHTML

fun FlowContent.taskList(
    worldId: Int,
    projectId: Int,
    tasks: List<ActionTask>,
    block: FlowContent.() -> Unit = {}
) {
    ul("task-list") {
        id = "task-list"
        if (tasks.isEmpty()) {
            li("task-list__empty") {
                id = "task-list-empty"
                span("text-muted") { +"No tasks yet." }
            }
        }
        tasks.forEach { task ->
            li {
                taskRowItem(worldId, projectId, task)
            }
        }
        block()
    }
}

fun LI.taskRowItem(worldId: Int, projectId: Int, task: ActionTask) {
    classes = buildSet {
        add("task-row")
        if (task.completed) add("task-row--done")
    }
    id = "task-row-${task.id}"
    label("task-row__label") {
        input(type = InputType.checkBox, classes = "task-checkbox") {
            id = "task-${task.id}-complete"
            checked = task.completed
            hxPatch("/worlds/$worldId/projects/$projectId/tasks/${task.id}/complete")
            hxTarget("#task-row-${task.id}")
            hxSwap("outerHTML")
        }
        span("task-row__name${if (task.completed) " task-row__name--done" else ""}") {
            +task.name
        }
    }
    button(classes = "task-row__delete-btn") {
        type = ButtonType.button
        hxDeleteWithConfirm(
            url = "/worlds/$worldId/projects/$projectId/tasks/${task.id}",
            title = "Delete task",
            description = "\"${task.name}\" will be permanently deleted."
        )
        hxTarget("#task-row-${task.id}")
        hxSwap("delete")
        +"×"
    }
}

fun taskRowFragment(worldId: Int, projectId: Int, task: ActionTask): String =
    createHTML().li {
        taskRowItem(worldId, projectId, task)
    }

fun FlowContent.addTaskInline(worldId: Int, projectId: Int) {
    div("add-task-inline") {
        id = "add-task-inline"
        button(classes = "btn btn--secondary btn--sm add-task-inline__trigger") {
            type = ButtonType.button
            attributes["onclick"] = """
                var f=document.getElementById('add-task-form');
                f.classList.toggle('add-task-inline--visible');
                if(f.classList.contains('add-task-inline--visible'))f.querySelector('input').focus();
            """.trimIndent()
            +"+ Add task"
        }
        form(classes = "add-task-inline__form") {
            id = "add-task-form"
            attributes["hx-post"] = "/worlds/$worldId/projects/$projectId/tasks"
            attributes["hx-target"] = "#task-list"
            attributes["hx-swap"] = "afterbegin"
            attributes["hx-on::after-request"] = """
                if (event.detail.xhr.status < 300) {
                    this.reset();
                    this.classList.remove('add-task-inline--visible');
                }
            """.trimIndent()
            input(type = InputType.text, classes = "add-task-inline__input") {
                name = "name"
                placeholder = "Task name..."
                minLength = "3"
                maxLength = "100"
                attributes["onkeydown"] =
                    "if(event.key==='Escape'){this.form.reset();this.form.classList.remove('add-task-inline--visible');}"
            }
            button(classes = "btn btn--primary btn--sm") {
                type = ButtonType.submit
                +"Add"
            }
        }
    }
}
