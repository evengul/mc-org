package app.mcorg.presentation.templated.dsl

import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectResourceEdge
import app.mcorg.domain.model.resources.ResourceGatheringItem
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.span

/**
 * Resume hero — the newest active project pinned on top of the Field Log with
 * live resource counters, so the gathering work is zero clicks away. Rows
 * reuse the project page's resourceRow (same component at two zooms).
 */

const val RESUME_HERO_MAX_ROWS = 6

enum class ResumeSort(val param: String, val label: String) {
    NEEDED("needed", "Needed first"),
    AZ("az", "A–Z"),
    PROGRESS("progress", "Progress");

    companion object {
        fun fromParam(value: String?): ResumeSort = entries.firstOrNull { it.param == value } ?: NEEDED
    }
}

fun sortResumeResources(items: List<ResourceGatheringItem>, sort: ResumeSort): List<ResourceGatheringItem> {
    fun pct(item: ResourceGatheringItem): Double =
        if (item.required > 0) item.collected.toDouble() / item.required else 0.0

    return when (sort) {
        ResumeSort.NEEDED -> items.sortedWith(
            compareBy<ResourceGatheringItem> { it.collected >= it.required }
                .thenByDescending { pct(it) }
                .thenBy { it.name }
        )
        ResumeSort.AZ -> items.sortedBy { it.name }
        ResumeSort.PROGRESS -> items.sortedWith(compareBy({ pct(it) }, { it.name }))
    }
}

data class ResumeHeroData(
    val project: Project,
    val resources: List<ResourceGatheringItem>,
)

fun FlowContent.resumeHero(
    worldId: Int,
    data: ResumeHeroData,
    feeds: List<ProjectResourceEdge> = emptyList(),
    sort: ResumeSort = ResumeSort.NEEDED,
) {
    val project = data.project
    div("fl-hero") {
        attributes["id"] = "fl-resume-hero"

        div("fl-hero__head") {
            div("fl-hero__title") {
                span("section-label") { +"Resume" }
                a(classes = "fl-hero__name") {
                    href = "/worlds/$worldId/projects/${project.id}"
                    +project.name
                }
                projectStateBadge(project.id, project.state)
                project.location?.let { location ->
                    span("fl-hero__coords") { +"X:${location.x} · Z:${location.z}" }
                }
            }
            if (feeds.isNotEmpty()) {
                span("fl-row__caption") {
                    +"Feeds → ${feeds.toFeedsCaption()}"
                }
            }
        }

        if (data.resources.isNotEmpty()) {
            div("fl-hero__toolbar") {
                span("section-label") { +"Resources" }
                div("fl-sort-pills") {
                    ResumeSort.entries.forEach { option ->
                        button(classes = if (option == sort) "fl-sort-pill fl-sort-pill--active" else "fl-sort-pill") {
                            attributes["hx-get"] = "/worlds/$worldId/projects/resume-rows?sort=${option.param}"
                            attributes["hx-target"] = "#fl-resume-rows"
                            attributes["hx-swap"] = "outerHTML"
                            +option.label
                        }
                    }
                }
            }

            resumeHeroRows(worldId, data, sort)

            div("fl-hero__footer") {
                span("fl-hero__hint") { +"…or just keep counting right here." }
                a(classes = "btn btn--secondary btn--sm") {
                    href = "/worlds/$worldId/projects/${project.id}"
                    +"Open project page →"
                }
            }
        } else {
            div("fl-hero__footer") {
                span("fl-hero__hint") { +"No resources defined yet." }
                a(classes = "btn btn--secondary btn--sm") {
                    href = "/worlds/$worldId/projects/${project.id}"
                    +"Open project page →"
                }
            }
        }
    }
}

fun FlowContent.resumeHeroRows(worldId: Int, data: ResumeHeroData, sort: ResumeSort) {
    val sorted = sortResumeResources(data.resources, sort)
    div("fl-hero__rows") {
        attributes["id"] = "fl-resume-rows"
        sorted.take(RESUME_HERO_MAX_ROWS).forEach { item ->
            resourceRow(
                id = item.id,
                worldId = worldId,
                projectId = data.project.id,
                itemName = item.name,
                current = item.collected,
                required = item.required,
                source = item.solvedByProject?.second
            )
        }
        if (sorted.size > RESUME_HERO_MAX_ROWS) {
            span("fl-hero__more") { +"+ ${sorted.size - RESUME_HERO_MAX_ROWS} more on the project page" }
        }
    }
}

fun resumeHeroRowsFragment(worldId: Int, data: ResumeHeroData, sort: ResumeSort): String =
    kotlinx.html.stream.createHTML().div {
        resumeHeroRows(worldId, data, sort)
    }.removePrefix("<div>").removeSuffix("</div>")

private fun List<ProjectResourceEdge>.toFeedsCaption(): String =
    groupBy { it.consumerName }
        .entries
        .joinToString("  ·  ") { (name, projectEdges) ->
            val items = projectEdges.mapNotNull { it.itemName }.distinct()
            if (items.isEmpty()) name else "$name · ${items.joinToString(", ")}"
        }
