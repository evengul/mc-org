package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.idea.schema.CategoryField
import kotlinx.html.*

/**
 * Renders a form input field for idea creation based on its type.
 * Similar to renderFilterField but for creation forms (not filters).
 */
fun DIV.renderCreateField(field: CategoryField) {
    when (field) {
        is CategoryField.Text -> renderCreateTextField(field)
        is CategoryField.Number -> renderCreateNumberField(field)
        is CategoryField.Select -> renderCreateSelectField(field)
        is CategoryField.MultiSelect -> renderCreateMultiSelectField(field)
        is CategoryField.BooleanField -> renderCreateBooleanField(field)
        is CategoryField.Rate -> renderCreateRateField(field)
        is CategoryField.Percentage -> renderCreatePercentageField(field)
        is CategoryField.Dimensions -> renderCreateDimensionsField(field)
        is CategoryField.MapField -> renderCreateMapField(field)
        is CategoryField.ListField -> renderCreateListField(field)
    }
}

/**
 * Renders a text input field for creation
 */
fun DIV.renderCreateTextField(field: CategoryField.Text) {
    label {
        htmlFor = "create-${field.key}"
        +field.label
        if (field.required) {
            span("required-indicator") { +" *" }
        }
        if (field.helpText != null) {
            small("form-help-text subtle") {
                style = "display: block; margin-top: var(--spacing-xxs);"
                +field.helpText
            }
        }
    }
    if (field.multiline) {
        textArea {
            id = "create-${field.key}"
            name = "categoryData[${field.key}]"
            classes += "form-control"
            if (field.required) required = true
            field.placeholder?.let { placeholder = it }
            field.maxLength?.let { maxLength = it.toString() }
            rows = "4"
        }
    } else {
        input(type = InputType.text, classes = "form-control") {
            id = "create-${field.key}"
            name = "categoryData[${field.key}]"
            if (field.required) required = true
            field.placeholder?.let { placeholder = it }
            field.maxLength?.let { maxLength = it.toString() }
        }
    }
}

/**
 * Renders a number input field
 */
fun DIV.renderCreateNumberField(field: CategoryField.Number) {
    label {
        htmlFor = "create-${field.key}"
        +field.label
        if (field.required) {
            span("required-indicator") { +" *" }
        }
        if (field.helpText != null) {
            small("form-help-text subtle") {
                style = "display: block; margin-top: var(--spacing-xxs);"
                +field.helpText
            }
        }
    }
    div("cluster cluster--xs") {
        input(type = InputType.number, classes = "form-control") {
            id = "create-${field.key}"
            name = "categoryData[${field.key}]"
            if (field.required) required = true
            field.min?.let { attributes["min"] = it.toString() }
            field.max?.let { attributes["max"] = it.toString() }
            field.step?.let { attributes["step"] = it.toString() }
            placeholder = field.label
        }
        field.suffix?.let { suffix ->
            span {
                style = "align-self: center; color: var(--clr-text-subtle);"
                +suffix
            }
        }
    }
}

/**
 * Renders a select dropdown
 */
fun DIV.renderCreateSelectField(field: CategoryField.Select) {
    label {
        htmlFor = "create-${field.key}"
        +field.label
        if (field.required) {
            span("required-indicator") { +" *" }
        }
        if (field.helpText != null) {
            small("form-help-text subtle") {
                style = "display: block; margin-top: var(--spacing-xxs);"
                +field.helpText
            }
        }
    }
    select(classes = "form-control") {
        id = "create-${field.key}"
        name = "categoryData[${field.key}]"
        if (field.required) required = true

        if (!field.required) {
            option {
                value = ""
                +"Select..."
            }
        }

        field.options.forEach { opt ->
            option {
                value = opt
                if (opt == field.defaultValue) selected = true
                +opt
            }
        }
    }
}

/**
 * Renders checkboxes for multi-select
 */
fun DIV.renderCreateMultiSelectField(field: CategoryField.MultiSelect) {
    label {
        +field.label
        if (field.required) {
            span("required-indicator") { +" *" }
        }
        if (field.helpText != null) {
            small("form-help-text subtle") {
                style = "display: block; margin-top: var(--spacing-xxs);"
                +field.helpText
            }
        }
    }
    div("stack stack--xs") {
        field.options.forEach { opt ->
            label("filter-checkbox-label") {
                input(type = InputType.checkBox) {
                    name = "categoryData[${field.key}][]"
                    value = opt
                }
                +opt
            }
        }
    }
}

/**
 * Renders a boolean checkbox
 */
fun DIV.renderCreateBooleanField(field: CategoryField.BooleanField) {
    label("filter-checkbox-label") {
        input(type = InputType.checkBox) {
            id = "create-${field.key}"
            name = "categoryData[${field.key}]"
            value = "true"
            if (field.defaultValue) checked = true
        }
        +field.label
        if (field.helpText != null) {
            small("form-help-text subtle") {
                style = "display: inline; margin-left: var(--spacing-xxs);"
                +" (${field.helpText})"
            }
        }
    }
}

/**
 * Renders a rate field with unit
 */
fun DIV.renderCreateRateField(field: CategoryField.Rate) {
    label {
        htmlFor = "create-${field.key}"
        +field.label
        if (field.required) {
            span("required-indicator") { +" *" }
        }
        if (field.helpText != null) {
            small("form-help-text subtle") {
                style = "display: block; margin-top: var(--spacing-xxs);"
                +field.helpText
            }
        }
    }
    div("cluster cluster--xs") {
        input(type = InputType.number, classes = "form-control") {
            id = "create-${field.key}"
            name = "categoryData[${field.key}]"
            if (field.required) required = true
            field.min?.let { attributes["min"] = it.toString() }
            field.max?.let { attributes["max"] = it.toString() }
            placeholder = field.unit
        }
        span {
            style = "align-self: center; color: var(--clr-text-subtle);"
            +field.unit
        }
    }
}

/**
 * Renders a percentage field
 */
fun DIV.renderCreatePercentageField(field: CategoryField.Percentage) {
    label {
        htmlFor = "create-${field.key}"
        +field.label
        if (field.required) {
            span("required-indicator") { +" *" }
        }
        if (field.helpText != null) {
            small("form-help-text subtle") {
                style = "display: block; margin-top: var(--spacing-xxs);"
                +field.helpText
            }
        }
    }
    div("cluster cluster--xs") {
        input(type = InputType.number, classes = "form-control") {
            id = "create-${field.key}"
            name = "categoryData[${field.key}]"
            if (field.required) required = true
            attributes["min"] = field.min.toString()
            attributes["max"] = field.max.toString()
            attributes["step"] = "0.01"
            placeholder = "e.g., 0.75"
        }
        span {
            style = "align-self: center; color: var(--clr-text-subtle);"
            +"(0-1)"
        }
    }
}

/**
 * Renders dimension inputs (X, Y, Z)
 */
fun DIV.renderCreateDimensionsField(field: CategoryField.Dimensions) {
    label {
        +field.label
        if (field.required) {
            span("required-indicator") { +" *" }
        }
        if (field.helpText != null) {
            small("form-help-text subtle") {
                style = "display: block; margin-top: var(--spacing-xxs);"
                +field.helpText
            }
        }
    }
    div("cluster cluster--xs") {
        input(type = InputType.number, classes = "form-control") {
            name = "categoryData[${field.key}][x]"
            placeholder = "X"
            attributes["min"] = "1"
            if (field.required) required = true
        }
        span {
            style = "align-self: center;"
            +"×"
        }
        input(type = InputType.number, classes = "form-control") {
            name = "categoryData[${field.key}][y]"
            placeholder = "Y"
            attributes["min"] = "1"
            if (field.required) required = true
        }
        span {
            style = "align-self: center;"
            +"×"
        }
        input(type = InputType.number, classes = "form-control") {
            name = "categoryData[${field.key}][z]"
            placeholder = "Z"
            attributes["min"] = "1"
            if (field.required) required = true
        }
    }
}

/**
 * Renders a map field (key-value pairs)
 */
fun DIV.renderCreateMapField(field: CategoryField.MapField) {
    label {
        +field.label
        if (field.required) {
            span("required-indicator") { +" *" }
        }
        if (field.helpText != null) {
            small("form-help-text subtle") {
                style = "display: block; margin-top: var(--spacing-xxs);"
                +field.helpText
            }
        }
    }
    div("map-field-container stack stack--xs") {
        id = "map-${field.key}"
        // Initial empty row
        div("cluster cluster--xs map-field-row") {
            if (field.keyOptions != null) {
                select(classes = "form-control") {
                    name = "categoryData[${field.key}][key][]"
                    option { value = ""; +"Select ${field.keyLabel}..." }
                    field.keyOptions.forEach { opt ->
                        option { value = opt; +opt }
                    }
                }
            } else {
                input(type = InputType.text, classes = "form-control") {
                    name = "categoryData[${field.key}][key][]"
                    placeholder = field.keyLabel
                }
            }
            input(type = InputType.text, classes = "form-control") {
                name = "categoryData[${field.key}][value][]"
                placeholder = field.valueLabel
            }
        }
    }
    button(type = ButtonType.button, classes = "btn btn--sm btn--neutral") {
        attributes["onclick"] = "addMapFieldRow('map-${field.key}')"
        +"+ Add Another"
    }
}

/**
 * Renders a list field
 */
fun DIV.renderCreateListField(field: CategoryField.ListField) {
    label {
        htmlFor = "create-${field.key}"
        +field.label
        if (field.required) {
            span("required-indicator") { +" *" }
        }
        if (field.helpText != null) {
            small("form-help-text subtle") {
                style = "display: block; margin-top: var(--spacing-xxs);"
                +field.helpText
            }
        }
    }

    if (field.allowedValues != null) {
        // Show as checkboxes
        div("stack stack--xs") {
            field.allowedValues.forEach { value ->
                label("filter-checkbox-label") {
                    input(type = InputType.checkBox) {
                        name = "categoryData[${field.key}][]"
                        this.value = value
                    }
                    +value
                }
            }
        }
    } else {
        // Free-form text list (comma-separated)
        input(type = InputType.text, classes = "form-control") {
            id = "create-${field.key}"
            name = "categoryData[${field.key}]"
            placeholder = "Enter values separated by commas"
            if (field.required) required = true
        }
    }
}

