package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.idea.IdeaProductionMode
import app.mcorg.pipeline.idea.single.IdeaMaterial
import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.h3
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
 *
 * ## One list, or one per variant (MCO-463)
 *
 * A design with build-time variants has no base list at all: each variant owns a complete list,
 * from its own `.litematic`, and they *replace* the base rather than adding to it. So [materials]
 * arrives empty for exactly those designs, and rendering only it would show a farm with fifteen
 * materials as having none — which is what this page did for the first design ever saved with a
 * variant.
 *
 * Both are shown the same way on purpose. The variants exist because they cost different amounts,
 * so the reader's question is "how much more is the 4-module one", and stacked lists with their
 * own totals answer it in one screen.
 */
fun FlowContent.ideaMaterialList(
    materials: List<IdeaMaterial>,
    productionModes: List<IdeaProductionMode> = emptyList(),
) {
    if (materials.isNotEmpty()) {
        section("idea-detail__materials") {
            materialListBody("Materials", materials)
        }
        return
    }

    val variants = productionModes.filter { it.isBuildTime && it.requirements.isNotEmpty() }
    if (variants.isEmpty()) return

    section("idea-detail__materials") {
        h2("idea-detail__section-title") { +"Materials" }
        p("idea-detail__section-subtitle") {
            // One variant is not a choice, so it must not be described as one — "can be built 1
            // ways" was both ungrammatical and untrue. It still gets its own named block, because
            // the author named it and a second one may follow.
            +if (variants.size == 1) {
                "Built one specific way, with its own material list."
            } else {
                "This design can be built ${variants.size} ways, each costing something different."
            }
        }
        variants.forEach { variant ->
            div("idea-materials__variant") {
                materialListBody(
                    variant.name,
                    variant.requirements.map { (itemId, quantity) -> IdeaMaterial(itemId, null, quantity) }
                        .sortedByDescending { it.quantity },
                    headingLevel = 3,
                )
            }
        }
    }
}

/** The heading, the count line and the rows — identical whether it is the base list or a variant's. */
private fun FlowContent.materialListBody(
    title: String,
    materials: List<IdeaMaterial>,
    headingLevel: Int = 2,
) {
    if (headingLevel == 2) {
        h2("idea-detail__section-title") { +title }
    } else {
        h3("idea-materials__variant-title") { +title }
    }
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
