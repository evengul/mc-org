package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.schema.IdeaCategorySchemas
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.*

fun FORM.categoryFields(data: CreateIdeaWizardData) {
    val schema = data.categoryData?.let { IdeaCategorySchemas.getSchema(it.first) }

    categoryField(data.categoryData?.first)
    div {
        id = "category-specific-fields"
        if (schema != null) {
            schema.fields.forEach { field ->
                renderCreateField(data.versionRange ?: MinecraftVersionRange.Unbounded, field, data.categoryData.second[field.key])
            }

            // If no fields exist
            if (schema.fields.isEmpty()) {
                p("subtle") {
                    style = "text-align: center; padding: var(--spacing-sm);"
                    +"No additional fields for this category"
                }
            }
        }
    }
}

private fun FORM.categoryField(selectedCategory: IdeaCategory? = null) {
    label {
        htmlFor = "idea-category"
        +"Category"
        span("required-indicator") { +"*" }
    }
    div("category-select") {
        IdeaCategory.entries.forEach { category ->
            label("filter-radio-label") {
                input(type = InputType.radio) {
                    name = "category"
                    value = category.name
                    checked = category == selectedCategory
                    required = true
                    // HTMX attributes to load category-specific fields
                    attributes["hx-get"] = "/app/ideas/create/fields/${category.name.lowercase()}"
                    attributes["hx-target"] = "#category-specific-fields"
                    attributes["hx-swap"] = "innerHTML"
                    attributes["hx-trigger"] = "change"
                    attributes["hx-include"] = "[name='versionRangeType'], [name='versionFrom'], [name='versionTo']"
                }
                +category.toPrettyEnumName()
            }
        }
    }
    p("validation-error-message") {
        id = "validation-error-category"
    }
}