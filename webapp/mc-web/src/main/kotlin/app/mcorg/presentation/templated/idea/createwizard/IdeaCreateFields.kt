package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.schema.CategoryField
import app.mcorg.domain.model.idea.schema.CategoryValue
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.presentation.templated.dsl.iconButton
import app.mcorg.presentation.templated.dsl.IconSize
import app.mcorg.presentation.templated.dsl.Icons
import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.InputType
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.small
import kotlinx.html.span
import kotlinx.html.textArea
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("IdeaCreateFields")

/**
 * Renders a form input field for idea creation based on its type.
 * Similar to renderFilterField but for creation forms (not filters).
 */
fun DIV.renderCreateField(versionRange: MinecraftVersionRange, field: CategoryField, value: CategoryValue? = null) {
    when (field) {
        is CategoryField.Text if (value is CategoryValue.TextValue?) -> renderCreateTextField(field, value)
        is CategoryField.Number if (value is CategoryValue.IntValue?) -> renderCreateNumberField(field, value)
        is CategoryField.Select if (value is CategoryValue.TextValue?) -> renderCreateSelectField(versionRange, field, value)
        is CategoryField.MultiSelect if (value is CategoryValue.MultiSelectValue?) -> renderCreateMultiSelectField(field, value)
        is CategoryField.BooleanField if (value is CategoryValue.BooleanValue?) -> renderCreateBooleanField(field, value)
        is CategoryField.Rate if (value is CategoryValue.IntValue?) -> renderCreateRateField(field, value)
        is CategoryField.Percentage if (value is CategoryValue.IntValue?) -> renderCreatePercentageField(field, value)
        is CategoryField.TypedMapField if (value is CategoryValue.MapValue?) -> renderCreateTypedMapField(versionRange, field, value)
        is CategoryField.StructField if (value is CategoryValue.MapValue?) -> renderCreateStructField(versionRange, field, value)
        is CategoryField.ListField if (value is CategoryValue.MultiSelectValue?) -> renderCreateListField(field, value)
        else -> {
            logger.warn("Unsupported field type for creation: ${field.key} of type ${field::class.simpleName} with value of type ${value?.let { it::class.simpleName } ?: "null"}")
            p {
                +"Unsupported field type for key: ${field.key} with value of type ${value?.let { it::class.simpleName } ?: "null"}"
            }
        }
    }
}

/**
 * Renders a text input field for creation
 */
fun DIV.renderCreateTextField(field: CategoryField.Text, value: CategoryValue.TextValue? = null) {
    label {
        htmlFor = "create-${field.key}"
        +field.label
        if (field.required) {
            span("required-indicator") { +"*" }
        }
        if (field.helpText != null) {
            small("form-help-text") {
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
            value?.let { + it.value }
        }
    } else {
        input(type = InputType.text, classes = "form-control") {
            id = "create-${field.key}"
            name = field.getCompleteKey()
            if (field.required) required = true
            field.placeholder?.let { placeholder = it }
            field.maxLength?.let { maxLength = it.toString() }
            value?.let { this.value = it.value }
        }
    }
    p("validation-error-message") {
        id = "validation-error-${field.getCompleteKey().replace("[]", "")}"
    }
}

/**
 * Renders a number input field
 */
fun DIV.renderCreateNumberField(field: CategoryField.Number, value: CategoryValue.IntValue? = null) {
    label {
        htmlFor = "create-${field.key}"
        +field.label
        if (field.required) {
            span("required-indicator") { +"*" }
        }
        if (field.helpText != null) {
            small("form-help-text") {
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
                this.value = it.value.toString()
            }
        }
        field.suffix?.let { suffix ->
            span {
                classes += "form-suffix"
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
fun DIV.renderCreateSelectField(versionRange: MinecraftVersionRange, field: CategoryField.Select, selectedValue: CategoryValue.TextValue? = null) {
    label {
        htmlFor = "create-${field.key}"
        +field.label
        if (field.required) {
            span("required-indicator") { +"*" }
        }
        if (field.helpText != null) {
            small("form-help-text") {
                +field.helpText
            }
        }
    }

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
                value = opt.value
                if (opt.value == field.defaultValue || opt.value == selectedValue?.value) selected = true
                +opt.label
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
fun DIV.renderCreateMultiSelectField(field: CategoryField.MultiSelect, selectedValues: CategoryValue.MultiSelectValue? = null) {
    label {
        +field.label
        if (field.required) {
            span("required-indicator") { +"*" }
        }
        if (field.helpText != null) {
            small("form-help-text") {
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
                    if (selectedValues != null && selectedValues.values.contains(opt)) {
                        checked = true
                    }
                }
                + opt
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
fun DIV.renderCreateBooleanField(field: CategoryField.BooleanField, checked: CategoryValue.BooleanValue? = null) {
    label("filter-checkbox-label") {
        input(type = InputType.checkBox) {
            id = "create-${field.key}"
            name = field.getCompleteKey()
            value = "true"
            if (field.defaultValue || checked?.value == true) this.checked = true
        }
        +field.label
        if (field.helpText != null) {
            small("form-help-text") {
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
fun DIV.renderCreateRateField(field: CategoryField.Rate, value: CategoryValue.IntValue? = null) {
    label {
        htmlFor = "create-${field.key}"
        +field.label
        if (field.required) {
            span("required-indicator") { +"*" }
        }
        if (field.helpText != null) {
            small("form-help-text") {
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
                this.value = it.value.toString()
            }
        }
        span("form-suffix") {
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
fun DIV.renderCreatePercentageField(field: CategoryField.Percentage, value: CategoryValue.IntValue? = null) {
    label {
        htmlFor = "create-${field.key}"
        +field.label
        if (field.required) {
            span("required-indicator") { +"*" }
        }
        if (field.helpText != null) {
            small("form-help-text") {
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
            attributes["step"] = "1"
            placeholder = "e.g., 75%"
            value?.let {
                this.value = it.value.toString()
            }
        }
        span("form-suffix") {
            +"(0-1)"
        }
    }
    p("validation-error-message") {
        id = "validation-error-${field.getCompleteKey().replace("[]", "")}"
    }
}

/**
 * Renders a map field (key-value pairs).
 *
 * Rows are added client-side by cloning the last row and blanking its inputs, rather than
 * rendering a hidden `<template>` — a template would render every option of a dynamic select
 * (e.g. the item list on a farm's production rate) a second time on every page load.
 */
fun DIV.renderCreateTypedMapField(versionRange: MinecraftVersionRange, field: CategoryField.TypedMapField, values: CategoryValue.MapValue? = null) {
    val containerId = "map-${field.key}"

    label {
        +field.label
        if (field.required) {
            span("required-indicator") { +"*" }
        }
        if (field.helpText != null) {
            small("form-help-text") {
                +field.helpText
            }
        }
    }
    div("map-field-container stack stack--xs") {
        id = containerId
        values?.value?.forEach { (k, v) ->
            mapFieldRow(versionRange, field, CategoryValue.TextValue(k), v)
        }
        // Always leave one blank row so the field is usable without clicking "Add entry" first.
        mapFieldRow(versionRange, field)
    }
    div("map-field-actions") {
        iconButton(Icons.MENU_ADD, "Add Entry") {
            iconSize = IconSize.SMALL
            buttonBlock = { type = ButtonType.button }
            onClick = addMapRowScript(containerId)
        }
    }
}

private fun DIV.mapFieldRow(
    versionRange: MinecraftVersionRange,
    field: CategoryField.TypedMapField,
    key: CategoryValue? = null,
    value: CategoryValue? = null,
) {
    div("cluster cluster--xs map-field-row") {
        div("map-field-cell") { renderCreateField(versionRange, field.keyType, key) }
        div("map-field-cell") { renderCreateField(versionRange, field.valueType, value) }
        iconButton(Icons.DELETE, "Remove Entry") {
            iconSize = IconSize.SMALL
            buttonBlock = { type = ButtonType.button }
            onClick = "this.closest('.map-field-row').remove(); return false;"
        }
    }
}

private fun addMapRowScript(containerId: String) = """
    var container = document.getElementById('$containerId');
    var last = container.lastElementChild;
    if (!last) return false;
    var row = last.cloneNode(true);
    row.querySelectorAll('[id]').forEach(function (el) { el.removeAttribute('id'); });
    row.querySelectorAll('input, textarea').forEach(function (el) {
        if (el.type === 'checkbox' || el.type === 'radio') { el.checked = false; } else { el.value = ''; }
    });
    row.querySelectorAll('select').forEach(function (el) { el.selectedIndex = 0; });
    container.appendChild(row);
    return false;
""".trimIndent().replace("\n", " ")

fun DIV.renderCreateStructField(versionRange: MinecraftVersionRange, field: CategoryField.StructField, values: CategoryValue.MapValue? = null) {
    field.fields.forEach { subField ->
        val value = values?.value?.get(subField.key)
        renderCreateField(versionRange, subField, value)
    }
}

/**
 * Renders a list field
 */
fun DIV.renderCreateListField(field: CategoryField.ListField, selectedValues: CategoryValue.MultiSelectValue? = null) {
    label {
        htmlFor = "create-${field.key}"
        +field.label
        if (field.required) {
            span("required-indicator") { +"*" }
        }
        if (field.helpText != null) {
            small("form-help-text") {
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
            this.value = it.values.joinToString(", ")
        }
    }
    p("validation-error-message") {
        id = "validation-error-${field.getCompleteKey().replace("[]", "")}"
    }
}

