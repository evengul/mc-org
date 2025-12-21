package app.mcorg.presentation.templated.project

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.hxTargetError
import app.mcorg.presentation.templated.common.form.searchableselect.SearchableSelectOption
import app.mcorg.presentation.templated.common.form.searchableselect.searchableSelect
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.modal.FormModalHttpMethod
import app.mcorg.presentation.templated.common.modal.FormModalHxValues
import app.mcorg.presentation.templated.common.modal.formModal
import app.mcorg.presentation.templated.common.tabs.TabData
import app.mcorg.presentation.templated.common.tabs.tabsComponent
import kotlinx.html.DIV
import kotlinx.html.InputType
import kotlinx.html.Tag
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.onSubmit
import kotlinx.html.p

enum class CreateTaskModalTab {
    ITEM_REQUIREMENT,
    ACTION_REQUIREMENT
}

fun <T : Tag> T.createTaskModal(user: TokenProfile, project: Project, itemNames: List<Item>, tab: CreateTaskModalTab) = formModal(
    modalId = "create-task-modal",
    title = "Create Task",
    description = "Create a new task with requirements for your Minecraft project.",
    saveText = "Create Task",
    hxValues = if (user.isDemoUserInProduction) null else FormModalHxValues(
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
        hxTargetError(".validation-error-message")
        classes += "create-task-form"
        if (user.isDemoUserInProduction) {
            onSubmit = "return false;"
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
        if (user.isDemoUserInProduction) {
            p("subtle") {
                + "Task creation is disabled in demo mode."
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
    p("validation-error-message") {
        id = "validation-error-action"
    }
}

fun DIV.itemRequirementForm(itemNames: List<Item>) {
    searchableSelect(
        id = "item-requirement-name-input",
        name = "itemId",
        options = itemNames.distinctBy { it.name }.sortedBy { it.name }.map { SearchableSelectOption(
            value = it.id,
            label = it.name
        ) }
    ) {
        required = true
    }
    p("validation-error-message") {
        id = "validation-error-itemName"
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
    p("validation-error-message") {
        id = "validation-error-requiredAmount"
    }
}