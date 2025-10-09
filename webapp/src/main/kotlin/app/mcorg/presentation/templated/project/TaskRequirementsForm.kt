package app.mcorg.presentation.templated.project

import app.mcorg.presentation.templated.common.button.IconButtonColor
import app.mcorg.presentation.templated.common.button.iconButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.tabs.TabData
import app.mcorg.presentation.templated.common.tabs.tabsComponent
import kotlinx.html.*

fun FORM.taskRequirementsForm() {
    h4 {
        +"Task Requirements"
    }
    tabsComponent(
        TabData.create("item-requirements-tab", "Item Requirements"),
        TabData.create("action-requirements-tab", "Action Requirements")) {
        activeTab = "item-requirements-tab"
        classes = mutableSetOf("task-requirements-tabs")
        onClick = { tab ->
            "switchTab('${tab.value}')"
        }
    }
    div {
        id = "item-requirements-tab"
        input {
            id = "item-requirement-name-input"
            placeholder = "Item name (e.g., Oak Logs, Stone, Diamond)"
            type = InputType.text
        }
        input {
            id = "item-requirement-amount-input"
            placeholder = "Required amount (e.g., 64, 128, 256)"
            type = InputType.number
        }
        neutralButton("Add Item Requirement") {
            onClick = "addItemRequirement()"
            addClass("add-requirement-button")
            buttonBlock = {
                type = ButtonType.button
            }
        }
        ul("requirements-list") {
            id = "item-requirements-container"
            // Requirements will be dynamically added here
        }
    }
    div {
        id = "action-requirements-tab"
        style = "display:none"
        input {
            id = "action-requirement-input"
            type = InputType.text
            placeholder = "Action description"
        }
        neutralButton("Add Action Requirement") {
            onClick = "addActionRequirement()"
            addClass("add-requirement-button")
            buttonBlock = {
                type = ButtonType.button
            }
        }
        ul("requirements-list") {
            id = "action-requirements-container"
            // Requirements will be dynamically added here
        }
    }
    div {
        classes = setOf("requirement-item")
        id = "requirement-template"
        p("requirement-text") {

        }
        hiddenInput(classes = "requirement-input") {

        }
        iconButton(Icons.DELETE, color = IconButtonColor.GHOST) {
            iconSize = IconSize.SMALL
            buttonBlock = {
                type = ButtonType.button
            }
        }
    }
}

