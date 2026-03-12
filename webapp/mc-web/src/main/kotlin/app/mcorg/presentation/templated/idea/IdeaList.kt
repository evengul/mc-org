package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.idea.Idea
import app.mcorg.pipeline.idea.IdeaSearchFilters
import app.mcorg.pipeline.idea.PaginatedResult
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxInclude
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.utils.formatAsRelativeOrDate
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.*
import kotlin.math.min
import java.net.URLEncoder

fun FlowContent.ideasListContainerContent(result: PaginatedResult<Idea>, filters: IdeaSearchFilters) {
    if (result.items.isEmpty()) {
        div("empty-state") {
            p("empty-state__heading") { +"No ideas match your filters" }
        }
    } else {
        div("ideas-card-grid") {
            result.items.forEach { idea ->
                ideaCard(idea)
            }
        }
    }
    ideaPaginationControls(result, filters)
}

fun FlowContent.ideaCard(idea: Idea) {
    a(classes = "idea-card") {
        href = Link.Ideas.single(idea.id)
        div("idea-card__header") {
            h2("idea-card__name") { +idea.name }
            span("badge") { +idea.category.toPrettyEnumName() }
        }
        p("idea-card__meta") {
            +"by ${idea.author.name} • ${idea.createdAt.formatAsRelativeOrDate()}"
        }
        p("idea-card__description") {
            val maxLen = 150
            if (idea.description.length > maxLen) {
                +"${idea.description.take(maxLen)}…"
            } else {
                +idea.description
            }
        }
        div("idea-card__footer") {
            div("idea-card__badges") {
                span("badge") { +idea.difficulty.toPrettyEnumName() }
                span("badge") { +idea.worksInVersionRange.toString() }
            }
            div("idea-card__stats") {
                span("idea-card__stat") { +"♥ ${idea.favouritesCount}" }
                span("idea-card__stat") {
                    val avg = "%.1f".format(idea.rating.average)
                    val stars = "★".repeat(min(idea.rating.average.toInt().coerceAtLeast(0), 5))
                    +"$avg $stars (${idea.rating.total})"
                }
            }
        }
    }
}

fun FlowContent.ideaPaginationControls(result: PaginatedResult<Idea>, filters: IdeaSearchFilters) {
    if (result.totalPages <= 1) return

    nav("ideas-pagination") {
        attributes["aria-label"] = "Pagination"

        if (result.hasPreviousPage) {
            val prevPage = result.page - 1
            a(classes = "btn btn--ghost btn--sm") {
                href = "/ideas/search?${filtersToQueryString(filters, prevPage)}"
                hxGet("/ideas/search?page=$prevPage")
                hxTarget("#ideas-list-container")
                hxSwap("outerHTML")
                hxInclude("#idea-filter-form")
                +"← Previous"
            }
        }

        for (pageNum in 1..result.totalPages) {
            if (pageNum == result.page) {
                span("ideas-pagination__page ideas-pagination__page--current") { +"$pageNum" }
            } else {
                a(classes = "ideas-pagination__page") {
                    href = "/ideas/search?${filtersToQueryString(filters, pageNum)}"
                    hxGet("/ideas/search?page=$pageNum")
                    hxTarget("#ideas-list-container")
                    hxSwap("outerHTML")
                    hxInclude("#idea-filter-form")
                    +"$pageNum"
                }
            }
        }

        if (result.hasNextPage) {
            val nextPage = result.page + 1
            a(classes = "btn btn--ghost btn--sm") {
                href = "/ideas/search?${filtersToQueryString(filters, nextPage)}"
                hxGet("/ideas/search?page=$nextPage")
                hxTarget("#ideas-list-container")
                hxSwap("outerHTML")
                hxInclude("#idea-filter-form")
                +"Next →"
            }
        }
    }
}

private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

fun filtersToQueryString(filters: IdeaSearchFilters, page: Int): String {
    val params = mutableListOf<String>()
    filters.query?.let { params.add("query=${encode(it)}") }
    filters.category?.let { params.add("category=${it.name}") }
    filters.difficulties.forEach { params.add("difficulty[]=${it.name}") }
    filters.minRating?.let { params.add("minRating=$it") }
    filters.minecraftVersion?.let { params.add("minecraftVersion=${encode(it)}") }
    params.add("page=$page")
    return params.joinToString("&")
}
