package app.mcorg.presentation.templated.dsl

import app.mcorg.domain.model.project.ProjectListItem
import app.mcorg.domain.model.project.ProjectResourceEdge
import app.mcorg.domain.model.resources.ResourceGatheringItem
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.input
import kotlinx.html.span
import kotlinx.html.strong

/**
 * The expand-in-place "smart slice" of a Field Log row — a window into the
 * project, never a dump of its full item list. Blocked projects get the rich
 * slice (blocker callout, filter, next-to-gather counters); unblocked projects
 * get a light "Next up" line.
 */

const val SLICE_MAX_ROWS = 5

/** Items still wanted and not waiting on an upstream project. */
fun sliceGatherable(
    items: List<ResourceGatheringItem>,
    blockedProducerIds: Set<Int>,
): List<ResourceGatheringItem> = items.filter { item ->
    item.collected < item.required &&
        (item.solvedByProject == null || item.solvedByProject!!.first !in blockedProducerIds)
}

/** Top gatherable items, closest to done first, optionally filtered by name. */
fun sliceNextToGather(
    items: List<ResourceGatheringItem>,
    blockedProducerIds: Set<Int>,
    query: String? = null,
    limit: Int = SLICE_MAX_ROWS,
): List<ResourceGatheringItem> = sliceGatherable(items, blockedProducerIds)
    .filter { query.isNullOrBlank() || it.name.contains(query, ignoreCase = true) }
    .sortedWith(
        compareByDescending<ResourceGatheringItem> {
            if (it.required > 0) it.collected.toDouble() / it.required else 0.0
        }.thenBy { it.name }
    )
    .take(limit)

fun FlowContent.fieldLogSlice(
    worldId: Int,
    project: ProjectListItem,
    items: List<ResourceGatheringItem>,
    blockedBy: List<ProjectResourceEdge>,
) {
    val blockedProducerIds = blockedBy.map { it.producerId }.toSet()

    div("fl-slice") {
        attributes["id"] = "fl-slice-${project.id}"

        if (blockedBy.isNotEmpty()) {
            fieldLogBlockerCallout(items, blockedBy)
            val gatherable = sliceGatherable(items, blockedProducerIds)
            val shown = sliceNextToGather(items, blockedProducerIds)
            div("fl-slice__toolbar") {
                span("section-label") { +"Next to gather · ${shown.size} of ${gatherable.size}" }
                input(classes = "fl-slice__filter") {
                    type = InputType.text
                    name = "query"
                    placeholder = "filter…"
                    attributes["hx-get"] = "/worlds/$worldId/projects/${project.id}/field-log-slice-items"
                    attributes["hx-trigger"] = "keyup changed delay:300ms"
                    attributes["hx-target"] = "#fl-slice-rows-${project.id}"
                    attributes["hx-swap"] = "outerHTML"
                }
            }
            fieldLogSliceRows(worldId, project.id, shown)
            div("fl-slice__footer") {
                span("fl-hero__hint") { +"gather the upstream farms first, or open the project —" }
                a(classes = "btn btn--secondary btn--sm") {
                    href = "/worlds/$worldId/projects/${project.id}"
                    +"open project page →"
                }
            }
        } else {
            val next = sliceNextToGather(items, blockedProducerIds, limit = 1).firstOrNull()
            div("fl-slice__footer") {
                if (next != null) {
                    span("fl-slice__next") {
                        +"Next up · "
                        strong { +next.name }
                        +" ${formatItemCount(next.collected)} / ${formatItemCount(next.required)}"
                    }
                } else {
                    span("fl-hero__hint") { +"Nothing left to gather here." }
                }
                a(classes = "btn btn--secondary btn--sm") {
                    href = "/worlds/$worldId/projects/${project.id}"
                    +"open project page →"
                }
            }
        }
    }
}

private fun FlowContent.fieldLogBlockerCallout(
    items: List<ResourceGatheringItem>,
    blockedBy: List<ProjectResourceEdge>,
) {
    val requiredByName = items.associateBy({ it.name }, { it.required })
    val parts = blockedBy
        .filter { it.itemName != null }
        .joinToString(" and ") { edge ->
            val qty = requiredByName[edge.itemName]?.let { "${"%,d".format(it)}× " } ?: ""
            "$qty${edge.itemName} waits on ${edge.producerName}"
        }
        .ifEmpty { blockedBy.joinToString(" and ") { "waiting on ${it.producerName}" } }

    div("callout callout--warning fl-slice__callout") {
        span("callout__icon") { +"⚠" }
        div("callout__body") {
            +"Partially blocked — "
            strong { +parts }
            +". Everything below is gatherable now."
        }
    }
}

fun FlowContent.fieldLogSliceRows(worldId: Int, projectId: Int, rows: List<ResourceGatheringItem>) {
    div("fl-slice__rows") {
        attributes["id"] = "fl-slice-rows-$projectId"
        rows.forEach { item ->
            resourceRow(
                id = item.id,
                worldId = worldId,
                projectId = projectId,
                itemName = item.name,
                current = item.collected,
                required = item.required,
                source = item.solvedByProject?.second
            )
        }
        if (rows.isEmpty()) {
            span("fl-hero__hint") { +"No matching items." }
        }
    }
}

fun fieldLogSliceRowsFragment(worldId: Int, projectId: Int, rows: List<ResourceGatheringItem>): String =
    kotlinx.html.stream.createHTML().div {
        fieldLogSliceRows(worldId, projectId, rows)
    }.removePrefix("<div>").removeSuffix("</div>")
