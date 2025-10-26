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
import app.mcorg.presentation.templated.common.tabs.TabData
import app.mcorg.presentation.templated.common.tabs.tabsComponent
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.*

enum class CreateTaskModalTab {
    ITEM_REQUIREMENT,
    ACTION_REQUIREMENT
}

fun <T : Tag> T.createTaskModal(project: Project, itemNames: List<Item>, tab: CreateTaskModalTab) = formModal(
    modalId = "create-task-modal",
    title = "Create Task",
    description = "Create a new task with requirements for your Minecraft project.",
    saveText = "Create Task",
    hxValues = FormModalHxValues(
        hxTarget = "#tasks-list",
        hxSwap = "afterbegin",
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

        tabsComponent(
            TabData.create("item", "Item Requirement"),
            TabData.create("action", "Action Requirement")
        ) {
            activeTab = when (tab) {
                CreateTaskModalTab.ITEM_REQUIREMENT -> "item"
                CreateTaskModalTab.ACTION_REQUIREMENT -> "action"
            }
            classes = mutableSetOf("task-requirements-tabs")
            queryName = "requirementTab"
            this.hxTarget = "#task-requirements-tab-content"
            this.hxSwap = "innerHTML"
        }

        div {
            id = "task-requirements-tab-content"
            when(tab) {
                CreateTaskModalTab.ACTION_REQUIREMENT -> actionRequirementForm()
                CreateTaskModalTab.ITEM_REQUIREMENT -> itemRequirementForm(itemNames)
            }
        }
    }
}

fun DIV.actionRequirementForm() {
    input {
        id = "action-requirement-name-input"
        placeholder = "Action description (e.g., Build a house, Defeat the Ender Dragon)"
        type = InputType.text
        classes += "form-control"
        name = "action"
        required = true
    }
}

fun DIV.itemRequirementForm(itemNames: List<Item>) {
    input {
        id = "item-requirement-name-input"
        placeholder = "Item name (e.g., Oak Logs, Stone, Diamond)"
        type = InputType.text
        list = "item-names-list"
        classes += "form-control"
        name = "itemName"
        required = true
    }
    dataList {
        id = "item-names-list"
        itemNames.distinctBy { it.name }.sortedBy { it.name }.forEach { (_, name) ->
            option {
                value = name
            }
        }
    }
    input {
        id = "item-requirement-amount-input"
        placeholder = "Required amount (e.g., 64, 128, 256)"
        type = InputType.number
        required = true
        name = "requiredAmount"
        min = "1"
        max = 2_000_000_000.toString()
    }
}