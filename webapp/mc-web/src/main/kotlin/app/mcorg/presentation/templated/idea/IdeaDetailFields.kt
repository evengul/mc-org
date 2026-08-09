package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.idea.Idea
import app.mcorg.domain.model.idea.schema.CategoryField
import app.mcorg.domain.model.idea.schema.CategoryValue
import app.mcorg.domain.model.idea.schema.IdeaCategorySchemas
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.section
import kotlinx.html.UL
import kotlinx.html.span
import kotlinx.html.ul

/**
 * Renders an idea's [Idea.categoryData] against its category schema (MCO-309).
 *
 * Everything submitted on the wizard's Category stage used to be write-only — the detail page never
 * looked at `categoryData` at all. That matters more since the MCO-204 slimming, because the free-form
 * `specs` block is now where most of a design's interesting detail lives.
 *
 * Keys the schema no longer defines are skipped, so data left behind by an older schema neither
 * crashes nor leaks raw keys onto the page.
 */
fun FlowContent.ideaDetailFields(idea: Idea) {
    val schema = IdeaCategorySchemas.getSchema(idea.category)

    // Schema order, not map order — every idea in a category then reads top-to-bottom the same way.
    val rows = schema.fields.mapNotNull { field ->
        idea.categoryData[field.key]?.takeUnless { it.isEmptyValue() }?.let { field to it }
    }
    if (rows.isEmpty()) return

    section("idea-detail__fields") {
        h2("idea-detail__section-title") { +"Design details" }
        div("idea-fields") {
            rows.forEach { (field, value) -> ideaFieldRow(field, value) }
        }
    }
}

private fun FlowContent.ideaFieldRow(field: CategoryField, value: CategoryValue) {
    div("idea-fields__row") {
        span("idea-fields__label") { +field.label }
        when {
            field is CategoryField.ListField && value is CategoryValue.MultiSelectValue ->
                referenceList(value.values)

            field is CategoryField.StructField && value is CategoryValue.MapValue ->
                structValue(field, value)

            field is CategoryField.TypedMapField && value is CategoryValue.MapValue ->
                pairList(field, value)

            else -> span("idea-fields__value") { +value.display() }
        }
    }
}

/** Reference links are URLs often enough to be worth linking, but the field is free text — check first. */
private fun FlowContent.referenceList(values: Set<String>) {
    ul("idea-fields__links") {
        values.filter { it.isNotBlank() }.forEach { entry ->
            li {
                if (entry.isLinkable()) {
                    a(href = entry, classes = "idea-fields__link") {
                        attributes["rel"] = "noopener noreferrer nofollow"
                        attributes["target"] = "_blank"
                        +entry
                    }
                } else {
                    span("idea-fields__value") { +entry }
                }
            }
        }
    }
}

/**
 * `size` is the only struct in the schema, and "12 × 4 × 9" beats three labelled rows — but only when
 * the whole footprint is known. A partial size falls back to labelled pairs so "12" is never shown alone.
 */
private fun FlowContent.structValue(field: CategoryField.StructField, value: CategoryValue.MapValue) {
    val dimensions = field.fields.mapNotNull { value.value[it.key] as? CategoryValue.IntValue }
    if (dimensions.size > 1 && dimensions.size == field.fields.size) {
        span("idea-fields__value") { +dimensions.joinToString(" × ") { it.display() } }
        return
    }

    ul("idea-fields__pairs") {
        field.fields.forEach { subField ->
            value.value[subField.key]?.takeUnless { it.isEmptyValue() }?.let { subValue ->
                pairItem(subField.label, subValue.display())
            }
        }
    }
}

private fun FlowContent.pairList(field: CategoryField.TypedMapField, value: CategoryValue.MapValue) {
    val unit = (field.valueType as? CategoryField.Rate)?.unit
    ul("idea-fields__pairs") {
        value.value.forEach { (key, entry) ->
            if (entry.isEmptyValue()) return@forEach
            val label = if (field.keyType is CategoryField.Select) key.prettifyId() else key
            pairItem(label, if (unit == null) entry.display() else "${entry.display()} $unit")
        }
    }
}

private fun UL.pairItem(key: String, value: String) {
    li("idea-fields__pair") {
        span("idea-fields__pair-key") { +key }
        span("idea-fields__pair-value") { +value }
    }
}

/**
 * Select keys are stored as raw ids ("minecraft:iron_ingot"). Resolving them to their catalog names
 * needs a database lookup, which does not belong in a template — tidy the id up instead. Free-text
 * keys (the `specs` block) are left exactly as the submitter typed them.
 */
internal fun String.prettifyId(): String = substringAfter(':')
    .split('_')
    .filter { it.isNotBlank() }
    .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

internal fun String.isLinkable(): Boolean = startsWith("http://") || startsWith("https://")

/** Absent and present-but-empty are the same thing to a reader — neither earns a row. */
internal fun CategoryValue.isEmptyValue(): Boolean = when (this) {
    is CategoryValue.MapValue -> value.isEmpty() || value.values.all { it.isEmptyValue() }
    is CategoryValue.MultiSelectValue -> values.all { it.isBlank() }
    is CategoryValue.TextValue -> value.isBlank()
    is CategoryValue.BooleanValue -> false
    is CategoryValue.IntValue -> false
    CategoryValue.IgnoredValue -> true
}
