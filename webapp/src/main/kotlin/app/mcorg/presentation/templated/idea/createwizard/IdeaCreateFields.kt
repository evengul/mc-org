package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.schema.CategoryField
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.presentation.templated.common.button.iconButton
import app.mcorg.presentation.templated.common.form.searchableselect.searchableSelect
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import kotlinx.html.*

/**
 * Renders a form input field for idea creation based on its type.
 * Similar to renderFilterField but for creation forms (not filters).
 */
fun DIV.renderCreateField(versionRange: MinecraftVersionRange, field: CategoryField, value: Any? = null) {
    when (field) {
        is CategoryField.Text -> renderCreateTextField(field, value as? String?)
        is CategoryField.Number -> renderCreateNumberField(field, value as? Int?)
        is CategoryField.Select<*> -> @Suppress("UNCHECKED_CAST") renderCreateSelectField(versionRange, field as CategoryField.Select<Any>, value)
        is CategoryField.MultiSelect -> renderCreateMultiSelectField(field, value as? Set<*>?)
        is CategoryField.BooleanField -> renderCreateBooleanField(field, value as? Boolean?)
        is CategoryField.Rate -> renderCreateRateField(field, value as? Int?)
        is CategoryField.Percentage -> renderCreatePercentageField(field, value as? Double?)
        is CategoryField.TypedMapField -> renderCreateTypedMapField(versionRange, field, value as? Map<*, *>)
        is CategoryField.StructField -> renderCreateStructField(versionRange, field, value as? Map<*, *>)
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
            name = field.getCompleteKey()
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
            name = field.getCompleteKey()
            if (field.required) required = true
            field.placeholder?.let { placeholder = it }
            field.maxLength?.let { maxLength = it.toString() }
            value?.let { this.value = it }
        }
    }
    p("validation-error-message") {
        id = "validation-error-${field.getCompleteKey().replace("[]", "")}"
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
            name = field.getCompleteKey()
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
        id = "validation-error-${field.getCompleteKey().replace("[]", "")}"
    }
}

/**
 * Renders a select dropdown
 */
fun DIV.renderCreateSelectField(versionRange: MinecraftVersionRange, field: CategoryField.Select<Any>, selectedValue: Any? = null) {
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

    if (field.options(versionRange).size > field.showSearchLimit) {
        searchableSelect(
            id = "create-${field.key}",
            name = field.getCompleteKey(),
            options = field.options(versionRange)
        ) {
            selectedValue?.let {
                this.selectedValue = it
            }
        }
    } else {
        select(classes = "form-control") {
            id = "create-${field.key}"
            name = field.getCompleteKey()
            if (field.required) required = true

            if (!field.required) {
                option {
                    value = ""
                    +"Select..."
                }
            }

            field.options(versionRange).forEach { opt ->
                option {
                    value = opt.value.toString()
                    if (opt.value == field.defaultValue || opt.value == selectedValue) selected = true
                    + opt.label
                }
            }
        }
    }
    p("validation-error-message") {
        id = "validation-error-${field.getCompleteKey().replace("[]", "")}"
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
                    name = field.getCompleteKey()
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
        id = "validation-error-${field.getCompleteKey().replace("[]", "")}"
    }
}

/**
 * Renders a boolean checkbox
 */
fun DIV.renderCreateBooleanField(field: CategoryField.BooleanField, checked: Boolean? = null) {
    label("filter-checkbox-label") {
        input(type = InputType.checkBox) {
            id = "create-${field.key}"
            name = field.getCompleteKey()
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
        id = "validation-error-${field.getCompleteKey().replace("[]", "")}"
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
            name = field.getCompleteKey()
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
        id = "validation-error-${field.getCompleteKey().replace("[]", "")}"
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
            name = field.getCompleteKey()
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
        id = "validation-error-${field.getCompleteKey().replace("[]", "")}"
    }
}

/**
 * Renders a map field (key-value pairs)
 */
fun DIV.renderCreateTypedMapField(versionRange: MinecraftVersionRange, field: CategoryField.TypedMapField, values: Map<*, *>? = null) {
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
        if (values != null && values.isNotEmpty()) {
            values.forEach { (k, v) ->
                div("cluster cluster--xs map-field-row") {
                    renderCreateField(versionRange, field.keyType, k)
                    renderCreateField(versionRange, field.valueType, v)
                    iconButton(Icons.DELETE, "Remove Entry") {
                        iconSize = IconSize.SMALL
                        onClick = "this.closest('.map-field-row').remove(); return false;"
                    }
                }
            }
        }
        div("cluster cluster--xs map-field-row") {
            renderCreateField(versionRange, field.keyType)
            renderCreateField(versionRange, field.valueType)
            iconButton(Icons.MENU_ADD, "Add Entry") {
                iconSize = IconSize.SMALL
                onClick = "alert('Not implemented yet'); return false;"
            }
        }
    }
}

fun DIV.renderCreateStructField(versionRange: MinecraftVersionRange, field: CategoryField.StructField, values: Map<*, *>? = null) {
    field.fields.forEach { subField ->
        val value = values?.get(subField.key)
        renderCreateField(versionRange, subField, value)
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
        name = field.getCompleteKey()
        placeholder = "Enter values separated by commas"
        if (field.required) required = true
        selectedValues?.let {
            this.value = it.joinToString(", ")
        }
    }
    p("validation-error-message") {
        id = "validation-error-${field.getCompleteKey().replace("[]", "")}"
    }
}

