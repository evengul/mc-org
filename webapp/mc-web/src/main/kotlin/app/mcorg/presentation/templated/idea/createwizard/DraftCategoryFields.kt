package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDraft
import app.mcorg.domain.model.idea.schema.IdeaCategorySchemas
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.pipeline.idea.draft.DraftData
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTrigger
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

fun FlowContent.draftCategoryFields(draft: IdeaDraft) {
    val data = runCatching { json.decodeFromString(DraftData.serializer(), draft.data) }.getOrDefault(DraftData())
    val selectedCategory = data.category
    val versionRange = data.versionRange ?: MinecraftVersionRange.Unbounded

    val versionRangeType = when (versionRange) {
        is MinecraftVersionRange.Bounded -> "bounded"
        is MinecraftVersionRange.LowerBounded -> "lowerBounded"
        is MinecraftVersionRange.UpperBounded -> "upperBounded"
        else -> "unbounded"
    }
    val versionFrom = when (versionRange) {
        is MinecraftVersionRange.Bounded -> versionRange.from.toString()
        is MinecraftVersionRange.LowerBounded -> versionRange.from.toString()
        else -> ""
    }
    val versionTo = when (versionRange) {
        is MinecraftVersionRange.Bounded -> versionRange.to.toString()
        is MinecraftVersionRange.UpperBounded -> versionRange.to.toString()
        else -> ""
    }

    div {
        label {
            +"Category"
            span("required-indicator") { +"*" }
        }
        div("category-select") {
            IdeaCategory.entries.forEach { category ->
                label("filter-radio-label") {
                    input(type = InputType.radio) {
                        classes += "category-radio"
                        name = "category"
                        value = category.name
                        checked = category == selectedCategory
                        required = true
                        hxGet("/ideas/create/fields/${category.name}?versionRangeType=$versionRangeType&versionFrom=$versionFrom&versionTo=$versionTo")
                        hxTrigger("change")
                        hxTarget("#category-specific-fields")
                        hxSwap("innerHTML")
                    }
                    +category.toPrettyEnumName()
                }
            }
        }
        p("form-error") { id = "error-category" }
    }

    val schema = selectedCategory?.let { IdeaCategorySchemas.getSchema(it) }
    div("wizard-category-fields") {
        id = "category-specific-fields"
        if (schema != null) {
            schema.fields.forEach { field ->
                renderCreateField(versionRange, field, data.categoryData?.get(field.key))
            }
        }
    }
}
