package app.mcorg.presentation.templated.project

import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import kotlinx.html.*

fun DIV.taskRequirementsForm() {
    div("task-requirements-form") {
        id = "task-requirements-section"

        // Requirements header
        div("requirements-header") {
            h4 {
                +"Task Requirements"
            }
            p("subtle") {
                +"Add specific requirements that must be completed for this task. You can add both item collection requirements and action-based requirements."
            }
        }

        // Requirements type selector
        div("requirement-type-selector") {
            div("u-flex u-flex-gap-sm") {
                neutralButton("Add Item Requirement") {
                    classes += "add-item-requirement-btn"
                    onClick = "addItemRequirement()"
                    iconLeft = Icons.MENU_ADD
                    iconSize = IconSize.SMALL
                }
                neutralButton("Add Action Requirement") {
                    classes += "add-action-requirement-btn"
                    onClick = "addActionRequirement()"
                    iconLeft = Icons.MENU_ADD
                    iconSize = IconSize.SMALL
                }
            }
        }

        // Requirements list container
        div("requirements-list") {
            id = "requirements-container"
            // Requirements will be dynamically added here
        }

        // Hidden template for new requirements
        requirementTemplates()
    }
}

private fun DIV.requirementTemplates() {
    // Item requirement template (hidden)
    div("requirement-template item-requirement-template") {
        style = "display: none;"
        id = "item-requirement-template"

        itemRequirementForm(-1) // Template uses -1 as placeholder index
    }

    // Action requirement template (hidden)
    div("requirement-template action-requirement-template") {
        style = "display: none;"
        id = "action-requirement-template"

        actionRequirementForm(-1) // Template uses -1 as placeholder index
    }
}

fun DIV.itemRequirementForm(index: Int) {
    val isTemplate = index < 0
    div("requirement-item item-requirement") {
        attributes["data-requirement-type"] = "item"
        if (index >= 0) {
            attributes["data-requirement-index"] = index.toString()
        }

        div("requirement-header") {
            h5 {
                +"Item Requirement"
            }
            button(classes = "btn btn--sm btn--danger remove-requirement-btn") {
                type = ButtonType.button
                onClick = "removeRequirement(this)"
                +"Remove"
            }
        }

        div("requirement-fields") {
            div("form-row") {
                div("form-group") {
                    label {
                        htmlFor = "requirement-item-$index"
                        +"Item Name"
                    }
                    input(classes = "form-control") {
                        type = InputType.text
                        name = if (index >= 0) "requirements[$index].item" else "requirements[INDEX].item"
                        id = "requirement-item-$index"
                        placeholder = "e.g., Stone, Oak Wood, Iron Ingot"
                        // Only add required attribute for actual form fields, not templates
                        if (!isTemplate) {
                            required = true
                        }
                    }
                }

                div("form-group") {
                    label {
                        htmlFor = "requirement-amount-$index"
                        +"Required Amount"
                    }
                    input(classes = "form-control") {
                        type = InputType.number
                        name = if (index >= 0) "requirements[$index].requiredAmount" else "requirements[INDEX].requiredAmount"
                        id = "requirement-amount-$index"
                        placeholder = "64"
                        min = "1"
                        max = "999999"
                        // Only add required attribute for actual form fields, not templates
                        if (!isTemplate) {
                            required = true
                        }
                    }
                }
            }

            // Hidden field for requirement type
            hiddenInput {
                name = if (index >= 0) "requirements[$index].type" else "requirements[INDEX].type"
                value = "ITEM"
            }
        }
    }
}

fun DIV.actionRequirementForm(index: Int) {
    val isTemplate = index < 0
    div("requirement-item action-requirement") {
        attributes["data-requirement-type"] = "action"
        if (index >= 0) {
            attributes["data-requirement-index"] = index.toString()
        }

        div("requirement-header") {
            h5 {
                +"Action Requirement"
            }
            button(classes = "btn btn--sm btn--danger remove-requirement-btn") {
                type = ButtonType.button
                onClick = "removeRequirement(this)"
                +"Remove"
            }
        }

        div("requirement-fields") {
            div("form-group") {
                label {
                    htmlFor = "requirement-action-$index"
                    +"Action Description"
                }
                textArea(classes = "form-control") {
                    name = if (index >= 0) "requirements[$index].action" else "requirements[INDEX].action"
                    id = "requirement-action-$index"
                    placeholder = "e.g., Build foundation layer, Connect redstone circuit, Place all windows"
                    maxLength = "500"
                    // Only add required attribute for actual form fields, not templates
                    if (!isTemplate) {
                        required = true
                    }
                }
            }

            // Hidden field for requirement type
            hiddenInput {
                name = if (index >= 0) "requirements[$index].type" else "requirements[INDEX].type"
                value = "ACTION"
            }
        }
    }
}
