package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.engine.plan.GatheringPlan
import app.mcorg.presentation.hxDelete
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTrigger
import app.mcorg.presentation.templated.dsl.formatPlainCount
import app.mcorg.presentation.templated.dsl.itemGlyph
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.TABLE
import kotlinx.html.TR
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.summary
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr

fun TR.planResourceRow(worldId: Int, projectId: Int, item: ResourceGatheringItem) {
    id = "plan-row-${item.id}"
    attributes["data-resource-id"] = item.id.toString()

    val dotModifier = if (item.sourceType != null) "status-dot--set" else "status-dot--unset"
    td("plan-resource-table__status") {
        span("status-dot $dotModifier") {}
    }
    td("plan-resource-table__item") {
        // One of the two glyph surfaces chosen for MCO-499 (the other is the /items/search
        // option). Row level, not group level: a group header names an ActivityGroup —
        // "Craft", "Loot" — which is not an item and has no glyph to draw.
        itemGlyph(item.itemId)
        +item.name
    }
    td("plan-resource-table__qty") {
        attributes["data-resource-id"] = item.id.toString()
        attributes["data-current-qty"] = item.required.toString()
        span("plan-resource-table__qty-display") {
            +item.required.toString()
        }
        input(type = InputType.number, classes = "plan-resource-table__qty-input") {
            name = "required"
            min = "1"
            max = "2000000000"
            value = item.required.toString()
            attributes["data-resource-id"] = item.id.toString()
            hxPatch("/worlds/$worldId/projects/$projectId/resources/gathering/${item.id}/required")
            hxTarget("#plan-row-${item.id}")
            hxSwap("outerHTML")
            hxTrigger("change")
        }
    }
    td("plan-resource-table__action") {
        div("plan-resource-table__action-group") {
            button(classes = "btn btn--ghost btn--sm plan-resource-table__ignore-btn") {
                type = ButtonType.button
                attributes["title"] = "Ignore — exclude from the material list and gathering plan"
                attributes["aria-label"] = "Ignore ${item.name}"
                hxPatch("/worlds/$worldId/projects/$projectId/resources/gathering/${item.id}/ignore")
                hxTarget("#plan-resources-area")
                hxSwap("outerHTML")
                +"⊘"
            }
            button(classes = "btn btn--ghost btn--sm plan-resource-table__delete-btn") {
                type = ButtonType.button
                hxDelete("/worlds/$worldId/projects/$projectId/resources/gathering/${item.id}?context=plan")
                hxTarget("#plan-row-${item.id}")
                hxSwap("outerHTML")
                +"×"
            }
        }
    }
}

/** Un-ignore action row: shown in the ignored section (MCO-247), reverses the ignore toggle. */
fun TR.ignoredResourceRow(worldId: Int, projectId: Int, item: ResourceGatheringItem) {
    id = "plan-ignored-row-${item.id}"
    attributes["data-resource-id"] = item.id.toString()

    td("plan-resource-table__status") {
        span("status-dot status-dot--unset") {}
    }
    td("plan-resource-table__item") {
        itemGlyph(item.itemId)
        +item.name
    }
    td("plan-resource-table__qty") {
        span("plan-resource-table__qty-display") {
            +item.required.toString()
        }
    }
    td("plan-resource-table__action") {
        button(classes = "btn btn--ghost btn--sm plan-resource-table__unignore-btn") {
            type = ButtonType.button
            attributes["title"] = "Un-ignore — include back in the material list and gathering plan"
            hxPatch("/worlds/$worldId/projects/$projectId/resources/gathering/${item.id}/ignore")
            hxTarget("#plan-resources-area")
            hxSwap("outerHTML")
            +"Un-ignore"
        }
    }
}

/**
 * The plan-view resource table. Rendered both inline and as the HTMX swap target for
 * the schematic-upload flow (`outerHTML` swap of `#plan-resource-table`).
 */
/**
 * The resource table, grouped by how you get each item and with its single-item tail folded
 * away (MCO-478). See [ResourceListLayout] for the arithmetic and why the thresholds are what
 * they are.
 *
 * One `<table>` with a `<tbody>` per group, rather than a table per group, so the columns stay
 * aligned down the whole list.
 */
fun FlowContent.planResourceTable(
    worldId: Int,
    projectId: Int,
    resources: List<ResourceGatheringItem>,
    plan: GatheringPlan? = null,
) {
    val layout = ResourceListLayout.of(resources, plan)
    table("data-table plan-resource-table") {
        id = "plan-resource-table"
        if (layout.visibleCount > 0) planResourceTableHead()
        planResourceGroups(worldId, projectId, layout)
    }
    planFoldedTail(worldId, projectId, layout)
}

/** Renders the full plan-view resource table as a standalone HTML fragment (HTMX swap response). */
fun planResourceTableFragment(
    worldId: Int,
    projectId: Int,
    resources: List<ResourceGatheringItem>,
    plan: GatheringPlan? = null,
): String = createHTML().div {
    val layout = ResourceListLayout.of(resources, plan)
    table("data-table plan-resource-table") {
        id = "plan-resource-table"
        if (layout.visibleCount > 0) planResourceTableHead()
        planResourceGroups(worldId, projectId, layout)
    }
    planFoldedTail(worldId, projectId, layout)
}

private fun TABLE.planResourceTableHead() {
    thead {
        tr {
            th { classes = setOf("plan-resource-table__col-status") }
            th { classes = setOf("plan-resource-table__col-item"); +"Item" }
            th { classes = setOf("plan-resource-table__col-qty"); +"Qty" }
            th { classes = setOf("plan-resource-table__col-action") }
        }
    }
}

/**
 * A `<tbody>` per group, each with a header row naming it.
 *
 * `#plan-resource-table-body` — which plan-view.js appends a newly added resource to — lands on
 * the "not in the plan yet" group, which is exactly where a resource added by hand belongs
 * until the plan is next derived. That group's tbody is therefore rendered even when empty, so
 * the append target always exists.
 */
private fun TABLE.planResourceGroups(
    worldId: Int,
    projectId: Int,
    layout: ResourceListLayout.Layout,
) {
    layout.groups.forEach { group ->
        val isUnplanned = group.group == null
        tbody {
            if (isUnplanned) id = "plan-resource-table-body"
            // With no plan there is nothing to group *by*, and a single "not in the plan yet"
            // heading over the whole list would be noise rather than information.
            if (layout.isGrouped) {
                tr("plan-resource-table__group") {
                    th {
                        attributes["colspan"] = "4"
                        // Two inline spans, laid out by a float rather than flex. The cell
                        // has to keep `display: table-cell` or colspan stops applying and the
                        // heading band ends at the first column instead of spanning the table.
                        span("plan-resource-table__group-name") {
                            // The same namer the breakdown resolution uses, so one group is
                            // never called two things on one page.
                            +(group.group?.let { groupLabel(it) } ?: ResourceListLayout.UNPLANNED_LABEL)
                        }
                        span("plan-resource-table__group-count") {
                            +"${group.rows.size} · ${formatPlainCount(group.items)} items"
                        }
                    }
                }
            }
            group.rows.forEach { item ->
                tr {
                    planResourceRow(worldId, projectId, item)
                }
            }
        }
    }
    if (layout.groups.none { it.group == null }) {
        tbody { id = "plan-resource-table-body" }
    }
}

/**
 * The folded tail. A `<details>` cannot live inside a table, so this is a second table beside
 * the first — the same shape [ignoredResourcesSection] already uses.
 */
private fun FlowContent.planFoldedTail(
    worldId: Int,
    projectId: Int,
    layout: ResourceListLayout.Layout,
) {
    if (layout.folded.isEmpty()) return
    details("plan-resource-fold") {
        summary("plan-resource-fold__summary") {
            span("plan-resource-fold__label") {
                +"${layout.folded.size} single-item odds and ends"
            }
            span("plan-resource-fold__note") {
                +"${formatPlainCount(layout.foldedItems)} of ${formatPlainCount(layout.totalItems)} items"
            }
        }
        table("data-table plan-resource-table plan-resource-table--folded") {
            tbody {
                id = "plan-resource-folded-body"
                layout.folded.forEach { item ->
                    tr {
                        planResourceRow(worldId, projectId, item)
                    }
                }
            }
        }
    }
}

/**
 * Wraps the active resource table and the ignored-items section (MCO-247) in a single
 * HTMX swap target — an ignore/un-ignore toggle moves a row between the two, so both
 * are re-rendered together.
 */
fun FlowContent.planResourcesArea(
    worldId: Int,
    projectId: Int,
    resources: List<ResourceGatheringItem>,
    plan: GatheringPlan? = null,
) {
    div {
        id = "plan-resources-area"
        planResourceTable(worldId, projectId, resources, plan)
        ignoredResourcesSection(worldId, projectId, resources)
    }
}

/**
 * Standalone HTML fragment version of [planResourcesArea] (HTMX swap response for the ignore
 * toggle). Takes the plan for the same reason the page does: replacing this fragment without
 * one would silently drop the grouping the reader is looking at.
 */
fun planResourcesAreaFragment(
    worldId: Int,
    projectId: Int,
    resources: List<ResourceGatheringItem>,
    plan: GatheringPlan? = null,
): String =
    createHTML().div {
        id = "plan-resources-area"
        planResourceTable(worldId, projectId, resources, plan)
        ignoredResourcesSection(worldId, projectId, resources)
    }

/**
 * Ignored-items section (MCO-247): items the user excluded from the material list and
 * gathering plan, kept visible and reversible via an "Un-ignore" action. Renders nothing
 * when there are no ignored items, so it doesn't clutter the page for the common case.
 */
fun FlowContent.ignoredResourcesSection(worldId: Int, projectId: Int, resources: List<ResourceGatheringItem>) {
    val ignoredResources = resources.filter { it.ignored }
    if (ignoredResources.isEmpty()) return

    div("project-detail__section plan-ignored-section") {
        id = "plan-ignored-section"
        div("project-detail__section-header") {
            span("project-detail__section-title section-label") { +"Ignored (${ignoredResources.size})" }
        }
        table("data-table plan-resource-table plan-resource-table--ignored") {
            id = "plan-ignored-table"
            tbody {
                id = "plan-ignored-table-body"
                ignoredResources.forEach { item ->
                    tr {
                        ignoredResourceRow(worldId, projectId, item)
                    }
                }
            }
        }
    }
}
