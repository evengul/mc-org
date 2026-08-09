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

/**
 * Category picker plus its schema fields, together. Kept for callers that want both in one place;
 * the single-page create form (MCO-310) uses the two halves separately, because the picker is
 * required and belongs up front while the schema fields are optional detail.
 */
fun FlowContent.draftCategoryFields(draft: IdeaDraft) {
    draftCategorySelect(draft)
    draftCategorySchemaFields(draft)
}

/** The required "what kind of thing is this" picker. */
fun FlowContent.draftCategorySelect(draft: IdeaDraft) {
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
}

/**
 * The chosen category's own fields. Swapped in by the picker's `hx-get`, so the container id is
 * global and this can live in a different part of the page from the picker.
 */
fun FlowContent.draftCategorySchemaFields(draft: IdeaDraft) {
    val data = runCatching { json.decodeFromString(DraftData.serializer(), draft.data) }.getOrDefault(DraftData())
    val versionRange = data.versionRange ?: MinecraftVersionRange.Unbounded
    val schema = data.category?.let { IdeaCategorySchemas.getSchema(it) }

    div("wizard-category-fields") {
        id = "category-specific-fields"
        if (schema != null) {
            schema.fields.forEach { field ->
                renderCreateField(versionRange, field, data.categoryData?.get(field.key))
            }
        } else {
            p("subtle") { +"Pick a category above to see the fields that fit it." }
        }
    }
}
