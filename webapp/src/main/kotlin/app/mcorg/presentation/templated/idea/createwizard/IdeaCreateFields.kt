package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.schema.CategoryField
import kotlinx.html.*

/**
 * Renders a form input field for idea creation based on its type.
 * Similar to renderFilterField but for creation forms (not filters).
 */
fun DIV.renderCreateField(field: CategoryField, value: Any? = null) {
    when (field) {
        is CategoryField.Text -> renderCreateTextField(field, value as? String?)
        is CategoryField.Number -> renderCreateNumberField(field, value as? Int?)
        is CategoryField.Select -> renderCreateSelectField(field, value as? String?)
        is CategoryField.MultiSelect -> renderCreateMultiSelectField(field, value as? Set<*>?)
        is CategoryField.BooleanField -> renderCreateBooleanField(field, value as? Boolean?)
        is CategoryField.Rate -> renderCreateRateField(field, value as? Int?)
        is CategoryField.Percentage -> renderCreatePercentageField(field, value as? Double?)
        is CategoryField.MapField -> renderCreateMapField(field, value as? Map<*, *>)
        is CategoryField.ListField -> renderCreateListField(field, value as? List<*>)
    }
}

/**
 * Renders a text input field for creation
 */
fun DIV.renderCreateTextField(field: CategoryField.Text, value: String? = null) {
    label {
        htmlFor = "create-${field.key}"
        +field.label
        if (field.required) {
            span("required-indicator") { +"*" }
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
            value?.let { +it }
        }
    } else {
        input(type = InputType.text, classes = "form-control") {
            id = "create-${field.key}"
            name = "categoryData[${field.key}]"
            if (field.required) required = true
            field.placeholder?.let { placeholder = it }
            field.maxLength?.let { maxLength = it.toString() }
            value?.let { this.value = it }
        }
    }
    p("validation-error-message") {
        id = "validation-error-categoryData[${field.key}]"
    }
}

/**
 * Renders a number input field
 */
fun DIV.renderCreateNumberField(field: CategoryField.Number, value: Int? = null) {
    label {
        htmlFor = "create-${field.key}"
        +field.label
        if (field.required) {
            span("required-indicator") { +"*" }
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
            value?.let {
                this.value = it.toString()
            }
        }
        field.suffix?.let { suffix ->
            span {
                style = "align-self: center; color: var(--clr-text-subtle);"
                +suffix
            }
        }
    }
    p("validation-error-message") {
        id = "validation-error-categoryData[${field.key}]"
    }
}

/**
 * Renders a select dropdown
 */
fun DIV.renderCreateSelectField(field: CategoryField.Select, selectedValue: String? = null) {
    label {
        htmlFor = "create-${field.key}"
        +field.label
        if (field.required) {
            span("required-indicator") { +"*" }
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
                if (opt == field.defaultValue || opt == selectedValue) selected = true
                +opt
            }
        }
    }
    p("validation-error-message") {
        id = "validation-error-categoryData[${field.key}]"
    }
}

/**
 * Renders checkboxes for multi-select
 */
fun DIV.renderCreateMultiSelectField(field: CategoryField.MultiSelect, selectedValues: Set<*>? = null) {
    label {
        +field.label
        if (field.required) {
            span("required-indicator") { +"*" }
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
                    if (selectedValues != null && selectedValues.contains(opt)) {
                        checked = true
                    }
                }
                +opt
            }
        }
    }
    p("validation-error-message") {
        id = "validation-error-categoryData[${field.key}]"
    }
}

/**
 * Renders a boolean checkbox
 */
fun DIV.renderCreateBooleanField(field: CategoryField.BooleanField, checked: Boolean? = null) {
    label("filter-checkbox-label") {
        input(type = InputType.checkBox) {
            id = "create-${field.key}"
            name = "categoryData[${field.key}]"
            value = "true"
            if (field.defaultValue || checked == true) this.checked = true
        }
        +field.label
        if (field.helpText != null) {
            small("form-help-text subtle") {
                style = "display: inline; margin-left: var(--spacing-xxs);"
                +" (${field.helpText})"
            }
        }
    }
    p("validation-error-message") {
        id = "validation-error-categoryData[${field.key}]"
    }
}

/**
 * Renders a rate field with unit
 */
fun DIV.renderCreateRateField(field: CategoryField.Rate, value: Int? = null) {
    label {
        htmlFor = "create-${field.key}"
        +field.label
        if (field.required) {
            span("required-indicator") { +"*" }
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
            value?.let {
                this.value = it.toString()
            }
        }
        span {
            style = "align-self: center; color: var(--clr-text-subtle);"
            +field.unit
        }
    }
    p("validation-error-message") {
        id = "validation-error-categoryData[${field.key}]"
    }
}

/**
 * Renders a percentage field
 */
fun DIV.renderCreatePercentageField(field: CategoryField.Percentage, value: Double? = null) {
    label {
        htmlFor = "create-${field.key}"
        +field.label
        if (field.required) {
            span("required-indicator") { +"*" }
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
            value?.let {
                this.value = it.toString()
            }
        }
        span {
            style = "align-self: center; color: var(--clr-text-subtle);"
            +"(0-1)"
        }
    }
    p("validation-error-message") {
        id = "validation-error-categoryData[${field.key}]"
    }
}

/**
 * Renders a map field (key-value pairs)
 */
fun DIV.renderCreateMapField(field: CategoryField.MapField, values: Map<*, *>? = null) {
    label {
        +field.label
        if (field.required) {
            span("required-indicator") { +"*" }
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
        if (field.keyOptions != null) {
            field.keyOptions.forEach { option ->
                label {
                    htmlFor = "create-${field.key}-$option"
                    +option
                    if (field.required) {
                        span("required-indicator") { +"*" }
                    }
                }
                input(type = InputType.text, classes = "form-control") {
                    id = "create-${field.key}-$option"
                    name = "categoryData[${field.key}][$option][value]"
                    placeholder = field.valueLabel
                    required = field.required
                    values?.get(option)?.let { this.value = it.toString() }
                }
            }
            p("validation-error-message") {
                id = "validation-error-categoryData[${field.key}][key][]"
            }
        } else {
            if (values != null && values.isNotEmpty()) {
                values.forEach { (k, v) ->
                    div("cluster cluster--xs map-field-row") {
                        input(type = InputType.text, classes = "form-control") {
                            name = "categoryData[${field.key}][key][]"
                            placeholder = field.keyLabel
                            if (k != null) this.value = k.toString()
                        }
                        input(type = InputType.text, classes = "form-control") {
                            name = "categoryData[${field.key}][value][]"
                            placeholder = field.valueLabel
                            if (v != null) this.value = v.toString()
                        }
                        p("validation-error-message") {
                            id = "validation-error-categoryData[${field.key}][key]"
                        }
                    }
                }
            } else {
                div("cluster cluster--xs map-field-row") {
                    input(type = InputType.text, classes = "form-control") {
                        name = "categoryData[${field.key}][key][]"
                        placeholder = field.keyLabel
                    }
                    input(type = InputType.text, classes = "form-control") {
                        name = "categoryData[${field.key}][value][]"
                        placeholder = field.valueLabel
                    }
                    p("validation-error-message") {
                        id = "validation-error-categoryData[${field.key}][key]"
                    }
                }
            }

        }
        p("validation-error-message") {
            id = "validation-error-categoryData[${field.key}][value]"
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
fun DIV.renderCreateListField(field: CategoryField.ListField, selectedValues: List<*>? = null) {
    label {
        htmlFor = "create-${field.key}"
        +field.label
        if (field.required) {
            span("required-indicator") { +"*" }
        }
        if (field.helpText != null) {
            small("form-help-text subtle") {
                style = "display: block; margin-top: var(--spacing-xxs);"
                +field.helpText
            }
        }
    }

    input(type = InputType.text, classes = "form-control") {
        id = "create-${field.key}"
        name = "categoryData[${field.key}]"
        placeholder = "Enter values separated by commas"
        if (field.required) required = true
        selectedValues?.let {
            this.value = it.joinToString(", ")
        }
    }
    p("validation-error-message") {
        id = "validation-error-categoryData[${field.key}]"
    }
}

