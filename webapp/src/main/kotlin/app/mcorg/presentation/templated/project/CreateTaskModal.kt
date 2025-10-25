package app.mcorg.presentation.templated.project

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.task.Priority
import app.mcorg.presentation.templated.common.form.radiogroup.RadioGroupLayout
import app.mcorg.presentation.templated.common.form.radiogroup.RadioGroupOption
import app.mcorg.presentation.templated.common.form.radiogroup.radioGroup
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.modal.FormModalHttpMethod
import app.mcorg.presentation.templated.common.modal.FormModalHxValues
import app.mcorg.presentation.templated.common.modal.formModal
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.*

fun <T : Tag> T.createTaskModal(project: Project, itemNames: List<Item>) = formModal(
    modalId = "create-task-modal",
    title = "Create Task",
    description = "Create a new task with requirements for your Minecraft project.",
    saveText = "Create Task",
    hxValues = FormModalHxValues(
        hxTarget = "#tasks-list",
        hxSwap = "outerHTML",
        method = FormModalHttpMethod.POST,
        href = "${Link.Worlds.world(project.worldId).project(project.id).to}/tasks"
    ),
    openButtonBlock = {
        addClass("create-task-button")
        addClass("btn--action")
        iconLeft = Icons.MENU_ADD
        iconSize = IconSize.SMALL
        + "New Task"
    }
) {
    formContent {
        classes += "create-task-form"

        label {
            + "Name"
        }
        input {
            name = "name"
            required = true
            minLength = "3"
            maxLength = "100"
            type = InputType.text
            classes += "form-control"
        }

        label {
            + "Description"
        }
        textArea {
            name = "description"
            maxLength = "2000"
            classes += "form-control"
        }

        label {
            + "Related Stage"
        }
        select {
            name = "stage"
            classes += "form-control"
            ProjectStage.entries.filter { it != ProjectStage.COMPLETED }.forEach {
                option {
                    value = it.name
                    selected = it == project.stage
                    + it.toPrettyEnumName()
                }
            }
        }
        label {
            + "Priority"
        }
        div("priority-group") {
            radioGroup(
                "priority",
                Priority.entries.map { RadioGroupOption(it.name, it.toPrettyEnumName()) },
                selectedOption = "MEDIUM",
                layout = RadioGroupLayout.HORIZONTAL
            )
        }

        taskRequirementsForm(itemNames)
        
        // JavaScript include for form functionality
        script {
            src = "/static/scripts/task-requirements.js"
        }
    }
}