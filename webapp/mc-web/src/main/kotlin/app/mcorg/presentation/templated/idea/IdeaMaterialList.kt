package app.mcorg.presentation.templated.idea

import app.mcorg.pipeline.idea.single.IdeaMaterial
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.ul

/**
 * What an idea costs to build (MCO-309 follow-up).
 *
 * `idea_item_requirements` was written on create and read only by the import pipeline, so the
 * person deciding whether to import a design could not see what it would cost them — the one thing
 * a material list is for.
 */
fun FlowContent.ideaMaterialList(materials: List<IdeaMaterial>) {
    if (materials.isEmpty()) return

    section("idea-detail__materials") {
        h2("idea-detail__section-title") { +"Materials" }
        p("idea-detail__section-subtitle") {
            +"${materials.size} ${if (materials.size == 1) "item" else "items"}, ${materials.sumOf { it.quantity.toLong() }.formatWithSeparators()} total"
        }
        ul("idea-materials") {
            materials.forEach { material ->
                li("idea-materials__row") {
                    span("idea-materials__name") { +material.displayName() }
                    span("idea-materials__qty") { +material.quantity.toLong().formatWithSeparators() }
                }
            }
        }
    }
}

/**
 * Catalog names carry a " (Block)" / " (Item)" suffix that extraction adds purely to tell two lang
 * keys apart — `block.minecraft.X` and `item.minecraft.X` both normalise to `minecraft:X`. It is an
 * internal disambiguator, not something a builder reading a shopping list needs, so it is dropped
 * here. Falls back to tidying the raw id when the item is not in the catalog at all.
 */
internal fun IdeaMaterial.displayName(): String =
    name?.removeSuffix(" (Block)")?.removeSuffix(" (Item)") ?: itemId.prettifyId()

/**
 * Digit grouping separator: U+202F NARROW NO-BREAK SPACE. Written as an escape on purpose — as a
 * literal it is a byte you cannot see, indistinguishable from a plain space in every diff and
 * failure message. No-break so a count never wraps across lines mid-number.
 */
internal const val DIGIT_GROUP_SEPARATOR = "\u202F"

/** Material counts run to the millions, where "9389854" is unreadable and "9 389 854" is not. */
internal fun Long.formatWithSeparators(): String =
    toString().reversed().chunked(3).joinToString(DIGIT_GROUP_SEPARATOR).reversed()
