package app.mcorg.presentation.templated.project

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.task.ActionRequirement
import app.mcorg.domain.model.task.ItemRequirement
import app.mcorg.domain.model.task.Priority
import app.mcorg.domain.model.task.Task
import app.mcorg.presentation.templated.common.button.iconButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.chip.ChipColor
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.progress.progressComponent
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.DIV
import kotlinx.html.InputType
import kotlinx.html.aside
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.ul
import java.time.format.DateTimeFormatter

fun DIV.tasksTab(project: Project, tasks: List<Task>) {
    classes += "project-tasks-tab"
    div("project-tasks-content") {
        div("project-tasks-progress") {
            h2 {
                + "Project Progress"
            }
            p("subtle") {
                + "${project.tasksCompleted} of ${project.tasksTotal} tasks completed"
            }
            progressComponent {
                value = project.tasksCompleted.toDouble()
                max = project.tasksTotal.toDouble()
            }
            p("subtle") {
                + "Current stage progress"
            }
            progressComponent {
                value = project.stageProgress
                max = 100.0
            }
        }
        div("project-tasks") {
            h2 {
                + "Tasks"
            }
            div("project-tasks-search-filter") {
                input {
                    placeholder = "Search tasks..."
                }
                select {
                    name = "completionStatus"
                    option {
                        value = "all"
                        + "All Tasks"
                    }
                    option {
                        value = "in-progress"
                        + "Active Tasks"
                    }
                    option {
                        value = "completed"
                        + "Completed Tasks"
                    }
                }
                select {
                    Priority.entries.forEach {
                        option {
                            value = it.name
                            + it.toPrettyEnumName()
                        }
                    }
                }
            }
            if (tasks.isEmpty()) {
                div("project-tasks-empty") {
                    h2 {
                        + "No Tasks Yet"
                    }
                    p("subtle") {
                        + "Create your first task to get started."
                    }
                    createTaskModal(project)
                }
            }
            ul {
                tasks.forEach { task ->
                    li {
                        div("task-header") {
                            div("task-header-start") {
                                input {
                                    type = InputType.checkBox
                                }
                                h3 {
                                    + task.name
                                }
                            }
                            div("task-header-end") {
                                chipComponent {
                                    icon = when(task.priority) {
                                        Priority.HIGH, Priority.CRITICAL -> Icons.Priority.HIGH
                                        Priority.MEDIUM -> Icons.Priority.MEDIUM
                                        Priority.LOW -> Icons.Priority.LOW
                                    }
                                    color = when(task.priority) {
                                        Priority.HIGH, Priority.CRITICAL -> ChipColor.DANGER
                                        Priority.MEDIUM -> ChipColor.WARNING
                                        Priority.LOW -> ChipColor.SUCCESS
                                    }
                                }
                            }
                        }
                        if (task.description.isNotBlank()) {
                            p("subtle") {
                                + task.description
                            }
                        }
                        div("task-progress-description") {
                            p("subtle") {
                                +"Progress"
                            }
                            p("subtle") {
                                +"${task.progress().toInt()}% completed"
                            }
                        }
                        progressComponent {
                            value = task.progress()
                            max = 100.0
                        }
                        neutralButton("Show details") {
                            classes += "task-requirements-toggle"
                            onClick = "document.getElementById('task-requirements-${task.id}')?.classList.toggle('visible'); this.textContent = this.textContent === 'Show details' ? 'Hide details' : 'Show details';"
                        }
                        div("task-requirements") {
                            id = "task-requirements-${task.id}"
                            p {
                                + "Requirements:"
                            }
                            ul {
                                task.requirements.forEach { requirement ->
                                    li {
                                        if (requirement.isCompleted()) {
                                            classes += "completed"
                                        }
                                        when(requirement) {
                                            is ActionRequirement -> {
                                                classes += "action-requirement"
                                                input {
                                                    id = "requirement-${requirement.id}"
                                                    checked = requirement.isCompleted()
                                                    type = InputType.checkBox
                                                }
                                                label {
                                                    htmlFor = "requirement-${requirement.id}"
                                                    + requirement.action
                                                }
                                            }
                                            is ItemRequirement -> {
                                                classes += "item-requirement"
                                                div("item-requirement-counts") {
                                                    p {
                                                        + requirement.item
                                                    }
                                                    span {
                                                        p {
                                                            + "${requirement.collected} / ${requirement.requiredAmount}"
                                                        }
                                                        // TODO(ICON): Edit icon for item requirement
                                                        iconButton(Icons.MENU_ADD, iconSize = IconSize.SMALL)
                                                    }
                                                }
                                                progressComponent {
                                                    value = requirement.collected.toDouble()
                                                    max = requirement.requiredAmount.toDouble()
                                                }
                                                div("item-requirement-actions") {
                                                    neutralButton("+1")
                                                    neutralButton("+64")
                                                    neutralButton("+1728")
                                                    neutralButton("+3456")
                                                    neutralButton("+ Custom")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    aside {
        h2 {
            + "Project Details"
        }
        p("details-label") {
            + "Project Type"
        }
        p {
            + project.type.toPrettyEnumName()
        }
        p("details-label") {
            + "Created"
        }
        p {
            + project.createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        }
        p("details-label") {
            + "Last Updated"
        }
        p {
            + project.updatedAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        }
    }
}