package app.mcorg.presentation.templated.project

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.task.ItemRequirement
import app.mcorg.domain.model.task.Priority
import app.mcorg.domain.model.task.Task
import app.mcorg.domain.model.task.TaskProjectStage
import app.mcorg.pipeline.task.FoundIdea
import app.mcorg.presentation.hxDeleteWithConfirm
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxIndicator
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTrigger
import app.mcorg.presentation.templated.common.button.IconButtonColor
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.button.iconButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.chip.ChipVariant
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.emptystate.emptyState
import app.mcorg.presentation.templated.common.form.searchField.searchField
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.progress.progressComponent
import app.mcorg.presentation.templated.utils.formatAsRelativeOrDate
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.*

fun DIV.tasksTab(tab: ProjectTab.Tasks) {
    classes += "project-tasks-tab"
    div("project-tasks-content") {
        projectProgressSection(tab.project)
        taskManagementSection(tab)
    }
    projectDetailsSidebar(tab.project)
}

private fun DIV.projectProgressSection(project: Project) {
    div("project-tasks-progress") {
        h2 {
            + "Project Progress"
        }
        projectProgress(project.tasksCompleted, project.tasksTotal)
    }
}

fun DIV.projectProgress(completed: Int, total: Int) {
    progressComponent {
        id = "project-progress"
        value = completed.toDouble()
        max = total.toDouble()
        showPercentage = false
        label = "$completed of $total task${if(total == 1) "" else "s"} completed"
    }
}

private fun DIV.taskManagementSection(tab: ProjectTab.Tasks) {
    div("project-tasks") {
        taskManagementHeader(tab.project.worldId, tab.project.id, tab.project.stage)
        if (tab.totalTasksCount == 0) {
            div {
                emptyTasksDisplay()
            }
        }
        p {
            id = "no-tasks-found"
            classes += "subtle"
            if (tab.tasks.isEmpty() && tab.totalTasksCount > 0) {
                + "No tasks found matching the search criteria."
            }
        }
        ul {
            tasksList(tab.project.worldId, tab.project.id, tab.tasks)
        }
    }
}

private fun DIV.taskManagementHeader(worldId: Int, projectId: Int, projectStage: ProjectStage) {
    h2 {
        + "Tasks"
    }
    taskSearchAndFilters(worldId, projectId, projectStage)
}

private fun DIV.taskSearchAndFilters(worldId: Int, projectId: Int, projectStage: ProjectStage) {
    form(classes = "project-tasks-search-filter") {
        encType = FormEncType.applicationXWwwFormUrlEncoded
        hxGet("${Link.Worlds.world(worldId).project(projectId).tasks().to}/search")
        hxTarget("#tasks-list")
        hxSwap("outerHTML")
        hxIndicator(".search-wrapper")
        hxTrigger("""
            input from:#task-search-input delay:500ms,
            change from:#task-search-input changed,
            change from:select[name="completionStatus"],
            change from:select[name="priority"],
            change from:select[name="stage"],
            change from:select[name="sortBy"]
            reset delay:100ms,
            submit
        """.trimIndent())
        searchField("task-search-input") {
            placeHolder = "Search tasks by name or description..."
        }
        select {
            name = "completionStatus"
            option {
                value = "ALL"
                + "All Tasks"
            }
            option {
                value = "IN_PROGRESS"
                selected = true
                + "Active Tasks"
            }
            option {
                value = "COMPLETED"
                + "Completed Tasks"
            }
        }
        select {
            name = "priority"
            option {
                value = "ALL"
                + "All Priorities"
            }
            Priority.entries.forEach {
                option {
                    value = it.name
                    + it.toPrettyEnumName()
                }
            }
        }
        select {
            name = "stage"
            option {
                value = "ALL"
                + "All Stages"
            }
            TaskProjectStage.entries.forEach {
                option {
                    selected = when(projectStage) {
                        ProjectStage.IDEA -> it == TaskProjectStage.IDEA
                        ProjectStage.DESIGN -> it == TaskProjectStage.DESIGN
                        ProjectStage.PLANNING -> it == TaskProjectStage.PLANNING
                        ProjectStage.RESOURCE_GATHERING -> it == TaskProjectStage.RESOURCE_GATHERING
                        ProjectStage.BUILDING -> it == TaskProjectStage.BUILDING
                        ProjectStage.TESTING -> it == TaskProjectStage.TESTING
                        else -> false
                    }
                    value = it.name
                    + it.toPrettyEnumName()
                }
            }
        }
        select {
            name = "sortBy"
            option {
                value = "required_amount_desc"
                selected = true
                + "Sort by Required Amount (High to Low)"
            }
            option {
                value = "priority_asc"
                + "Sort by Priority (High to Low)"
            }
            option {
                value = "lastModified_desc"
                + "Sort by Last Modified"
            }
            option {
                value = "name_asc"
                + "Sort by Name (A-Z)"
            }
        }
        neutralButton("Clear filters") {
            buttonBlock = {
                type = ButtonType.reset
            }
        }
    }
}

fun DIV.emptyTasksDisplay() {
    emptyState(
        id = "empty-tasks-state",
        title = "No Tasks Yet",
        description = "Create your first task to break down this project into manageable steps.",
        icon = Icons.MENU_ADD
    ) {
        actionButton("Add first task") {
            onClick = "document.getElementById('create-task-modal')?.showModal()"
        }
    }
}

fun UL.tasksList(worldId: Int, projectId: Int, tasks: List<Task>) {
    id = "tasks-list"
    tasks.sortedBy { it.isCompleted() }.forEach { task ->
        li {
            taskItem(worldId, projectId, task)
        }
    }
}

fun LI.taskItem(worldId: Int, projectId: Int, task: Task) {
    classes += "task-item"
    id = "task-${task.id}"
    taskHeader(worldId, projectId, task)
    if (task.description.isNotBlank()) {
        p("subtle") {
            + task.description
        }
    }
    if (task.requirement is ItemRequirement) {
        taskItemProgress(task.id, task.requirement)

        if (!task.isCompleted()) {
            itemRequirementActions(worldId, projectId, task.id)
        }
        div {
            foundIdeas(worldId, task.id to task.name)
        }
    }
}

fun DIV.foundIdeas(worldId: Int, task: Pair<Int, String>, ideas: List<FoundIdea>? = null) {
    id = "found-ideas-for-task-${task.first}"
    if (ideas != null) {
        p {
            + "Ideas that produce ${task.second}: "
        }
        if (ideas.isEmpty()) {
            p("subtle") {
                + "No ideas found."
            }
        } else {
            ul {
                ideas.forEach { idea ->
                    li {
                        if (idea.alreadyImported) {
                            span {
                                + "${idea.name} (Already imported)"
                            }
                        } else {
                            a(href = Link.Ideas.single(idea.id)) {
                                attributes["target"] = "_blank"
                                attributes["rel"] = "noopener noreferrer"
                                + idea.name
                            }
                            + "Rate: ${idea.rate} / hour"
                            actionButton("Import ${idea.name} into this world") {
                                buttonBlock = {
                                    hxPost(Link.Ideas.single(idea.id) + "/import?worldId=$worldId&forTask=${task.first}")
                                    hxSwap("none")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun LI.taskItemProgress(taskId: Int, itemRequirement: ItemRequirement) {
    progressComponent {
        id = "task-item-${taskId}-progress"
        value = itemRequirement.collected.toDouble()
        max = itemRequirement.requiredAmount.toDouble()
        showPercentage = false
        label = "${itemRequirement.collected} of ${itemRequirement.requiredAmount} item${if (itemRequirement.requiredAmount == 1) "" else "s"} collected"
    }
}

private fun LI.taskHeader(worldId: Int, projectId: Int, task: Task) {
    div("task-header") {
        div("task-header-start") {
            input {
               taskCompletionCheckbox(worldId, projectId, task.id, task.isCompleted())
            }
            h3 {
                + task.name
            }
        }
        div("task-header-end") {
            if (task.requirement is ItemRequirement) {
                if (task.solvedByProject != null) {
                    chipComponent {
                        icon = Icons.Menu.PROJECTS
                        variant = ChipVariant.SUCCESS
                        href = Link.Worlds.world(worldId).project(task.solvedByProject.first).to
                        + "Farmable with ${task.solvedByProject.second}"
                    }
                } else {
                    neutralButton("Find Farm Ideas") {
                        iconLeft = Icons.Search
                        iconSize = IconSize.SMALL
                        buttonBlock = {
                            hxGet("/app/worlds/$worldId/projects/$projectId/tasks/${task.id}/matching-ideas")
                            hxTarget("#found-ideas-for-task-${task.id}")
                            hxSwap("innerHTML")
                        }
                    }
                }
            }
            chipComponent {
                icon = when(task.priority) {
                    Priority.HIGH, Priority.CRITICAL -> Icons.Priority.HIGH
                    Priority.MEDIUM -> Icons.Priority.MEDIUM
                    Priority.LOW -> Icons.Priority.LOW
                }
                variant = when(task.priority) {
                    Priority.HIGH, Priority.CRITICAL -> ChipVariant.DANGER
                    Priority.MEDIUM -> ChipVariant.WARNING
                    Priority.LOW -> ChipVariant.INFO
                }
            }
            iconButton(
                icon = Icons.DELETE,
                ariaLabel = "Delete task",
                iconSize = IconSize.SMALL,
                color = IconButtonColor.DANGER,
            ) {
                buttonBlock = {
                    hxDeleteWithConfirm(
                        url = Link.Worlds.world(worldId).project(projectId).tasks().task(task.id),
                        title = "Delete Task",
                        description = "Are you sure you want to delete the task \"${task.name}\"? This action cannot be undone."
                    )
                    hxTarget("#task-${task.id}")
                    hxSwap("delete")
                    title = "Delete task"
                }
            }
        }
    }
}

fun INPUT.taskCompletionCheckbox(worldId: Int, projectId: Int, taskId: Int, completed: Boolean) {
    id = "task-${taskId}-complete"
    classes += "task-completion-checkbox"
    checked = completed
    disabled = completed
    hxTarget("#task-${taskId}-complete")
    hxPatch(Link.Worlds.world(worldId).project(projectId).tasks().task(taskId) + "/complete")
    hxSwap("outerHTML")
    type = InputType.checkBox
}

private fun LI.itemRequirementActions(worldId: Int, projectId: Int, taskId: Int) {
    div("item-requirement-actions") {
        neutralButton("+1") {
            buttonBlock = {
                attributes["hx-vals"] = """{"amount": 1}"""
                hxPatch("/app/worlds/$worldId/projects/$projectId/tasks/$taskId/requirements/done-more")
                hxTarget("#task-item-${taskId}-progress")
                hxSwap("outerHTML")
            }
        }
        neutralButton("+64") {
            buttonBlock = {
                attributes["hx-vals"] = """{"amount": 64}"""
                hxPatch("/app/worlds/$worldId/projects/$projectId/tasks/$taskId/requirements/done-more")
                hxTarget("#task-item-${taskId}-progress")
                hxSwap("outerHTML")
            }
        }
        neutralButton("+1728") {
            buttonBlock = {
                attributes["hx-vals"] = """{"amount": 1728}"""
                hxPatch("/app/worlds/$worldId/projects/$projectId/tasks/$taskId/requirements/done-more")
                hxTarget("#task-item-${taskId}-progress")
                hxSwap("outerHTML")
            }
        }
        neutralButton("+3456") {
            buttonBlock = {
                attributes["hx-vals"] = """{"amount": 3456}"""
                hxPatch("/app/worlds/$worldId/projects/$projectId/tasks/$taskId/requirements/done-more")
                hxTarget("#task-item-${taskId}-progress")
                hxSwap("outerHTML")
            }
        }
    }
}

private fun DIV.projectDetailsSidebar(project: Project) {
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
            + project.createdAt.formatAsRelativeOrDate()
        }
        p("details-label") {
            + "Last Updated"
        }
        p {
            + project.updatedAt.formatAsRelativeOrDate()
        }
    }
}