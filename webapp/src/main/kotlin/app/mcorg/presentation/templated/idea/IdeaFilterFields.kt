package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.idea.schema.CategoryField
import kotlinx.html.*

/**
 * Renders a filterable field based on its type.
 * This is used to dynamically generate category-specific filters.
 */
fun DIV.renderFilterField(field: CategoryField) {
    if (!field.filterable) return

    when (field) {
        is CategoryField.Text -> renderTextField(field)
        is CategoryField.Number -> renderNumberField(field)
        is CategoryField.Select<*> -> @Suppress("UNCHECKED_CAST") renderSelectField(field as CategoryField.Select<Any>)
        is CategoryField.MultiSelect -> renderMultiSelectField(field)
        is CategoryField.BooleanField -> renderBooleanField(field)
        is CategoryField.Rate -> renderRateField(field)
        is CategoryField.Percentage -> renderPercentageField(field)
        else -> {
            // Skip non-filterable or unsupported field types
        }
    }
}

/**
 * Renders a text input field
 */
fun DIV.renderTextField(field: CategoryField.Text) {
    div("filter-group") {
        label {
            htmlFor = "filter-${field.key}"
            +field.label
            if (field.helpText != null) {
                span("subtle") {
                    style = "font-size: var(--text-sm); margin-left: var(--spacing-xxs);"
                    +" (${field.helpText})"
                }
            }
        }
        input(type = InputType.text, classes = "form-control form-control--sm filter-field") {
            id = "filter-${field.key}"
            name = "categoryFilters[${field.key}]"
            field.placeholder?.let { placeholder = it }
            if (field.required) {
                required = true
            }
        }
    }
}

/**
 * Renders a number input field with optional min/max/step
 */
fun DIV.renderNumberField(field: CategoryField.Number) {
    div("filter-group") {
        label {
            +field.label
            if (field.helpText != null) {
                span("subtle") {
                    style = "font-size: var(--text-sm); margin-left: var(--spacing-xxs);"
                    +" (${field.helpText})"
                }
            }
        }
        div("cluster cluster--xs") {
            input(type = InputType.number, classes = "form-control form-control--sm filter-field") {
                name = "categoryFilters[${field.key}_min]"
                placeholder = "Min"
                field.min?.let { attributes["min"] = it.toString() }
                field.max?.let { attributes["max"] = it.toString() }
                field.step?.let { attributes["step"] = it.toString() }
            }
            span {
                style = "align-self: center;"
                +"to"
            }
            input(type = InputType.number, classes = "form-control form-control--sm filter-field") {
                name = "categoryFilters[${field.key}_max]"
                placeholder = "Max"
                field.min?.let { attributes["min"] = it.toString() }
                field.max?.let { attributes["max"] = it.toString() }
                field.step?.let { attributes["step"] = it.toString() }
            }
            field.suffix?.let { suffix ->
                span("subtle") {
                    style = "align-self: center;"
                    +suffix
                }
            }
        }
    }
}

/**
 * Renders a select dropdown
 */
fun DIV.renderSelectField(field: CategoryField.Select<Any>) {
    div("filter-group") {
        label {
            htmlFor = "filter-${field.key}"
            +field.label
            if (field.helpText != null) {
                span("subtle") {
                    style = "font-size: var(--text-sm); margin-left: var(--spacing-xxs);"
                    +" (${field.helpText})"
                }
            }
        }
        select(classes = "form-control form-control--sm filter-field") {
            id = "filter-${field.key}"
            name = "categoryFilters[${field.key}]"
            option {
                value = ""
                +"Any"
            }
            field.options().forEach { opt ->
                option {
                    value = opt.value.toString()
                    + opt.label
                }
            }
        }
    }
}

/**
 * Renders multiple checkboxes for multi-select
 */
fun DIV.renderMultiSelectField(field: CategoryField.MultiSelect) {
    div("filter-group") {
        label {
            +field.label
            if (field.helpText != null) {
                span("subtle") {
                    style = "font-size: var(--text-sm); margin-left: var(--spacing-xxs);"
                    +" (${field.helpText})"
                }
            }
        }
        div("stack stack--xs") {
            field.options.forEach { opt ->
                label("filter-checkbox-label") {
                    input(type = InputType.checkBox, classes = "filter-field") {
                        name = "categoryFilters[${field.key}][]"
                        value = opt
                    }
                    +opt
                }
            }
        }
    }
}

/**
 * Renders a boolean checkbox
 */
fun DIV.renderBooleanField(field: CategoryField.BooleanField) {
    div("filter-group") {
        label("filter-checkbox-label") {
            input(type = InputType.checkBox, classes = "filter-field") {
                name = "categoryFilters[${field.key}]"
                value = "true"
                if (field.defaultValue) {
                    checked = true
                }
            }
            +field.label
            if (field.helpText != null) {
                span("subtle") {
                    style = "font-size: var(--text-sm); margin-left: var(--spacing-xxs);"
                    +" (${field.helpText})"
                }
            }
        }
    }
}

/**
 * Renders a rate field (similar to number field but with unit display)
 */
fun DIV.renderRateField(field: CategoryField.Rate) {
    div("filter-group") {
        label {
            +field.label
            if (field.helpText != null) {
                span("subtle") {
                    style = "font-size: var(--text-sm); margin-left: var(--spacing-xxs);"
                    +" (${field.helpText})"
                }
            }
        }
        div("cluster cluster--xs") {
            input(type = InputType.number, classes = "form-control form-control--sm filter-field") {
                name = "categoryFilters[${field.key}_min]"
                placeholder = "Min ${field.unit}"
                field.min?.let { attributes["min"] = it.toString() }
                field.max?.let { attributes["max"] = it.toString() }
            }
            span {
                style = "align-self: center;"
                +"to"
            }
            input(type = InputType.number, classes = "form-control form-control--sm filter-field") {
                name = "categoryFilters[${field.key}_max]"
                placeholder = "Max ${field.unit}"
                field.min?.let { attributes["min"] = it.toString() }
                field.max?.let { attributes["max"] = it.toString() }
            }
        }
    }
}

/**
 * Renders a percentage field (0-1 or 0-100 based on min/max)
 */
fun DIV.renderPercentageField(field: CategoryField.Percentage) {
    div("filter-group") {
        label {
            +field.label
            if (field.helpText != null) {
                span("subtle") {
                    style = "font-size: var(--text-sm); margin-left: var(--spacing-xxs);"
                    +" (${field.helpText})"
                }
            }
        }
        div("cluster cluster--xs") {
            input(type = InputType.number, classes = "form-control form-control--sm filter-field") {
                name = "categoryFilters[${field.key}_min]"
                placeholder = "Min %"
                attributes["min"] = field.min.toString()
                attributes["max"] = field.max.toString()
                attributes["step"] = "0.01"
            }
            span {
                style = "align-self: center;"
                +"to"
            }
            input(type = InputType.number, classes = "form-control form-control--sm filter-field") {
                name = "categoryFilters[${field.key}_max]"
                placeholder = "Max %"
                attributes["min"] = field.min.toString()
                attributes["max"] = field.max.toString()
                attributes["step"] = "0.01"
            }
        }
    }
}

