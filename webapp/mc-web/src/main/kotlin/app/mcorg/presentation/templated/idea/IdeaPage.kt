package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.idea.Comment
import app.mcorg.domain.model.idea.Idea
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.hxDeleteWithConfirm
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxPut
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.dsl.appHeader
import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.pageShell
import app.mcorg.presentation.templated.dsl.progressBar
import app.mcorg.presentation.templated.utils.formatAsDateTime
import app.mcorg.presentation.templated.utils.formatAsRelativeOrDate
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.*
import kotlin.math.min

fun ideaPage(
    user: TokenProfile,
    idea: Idea,
    comments: List<Comment>,
    unreadNotifications: Int = 0
): String = pageShell(
    pageTitle = "MC-ORG — ${idea.name}",
    user = user,
    stylesheets = listOf(
        "/static/styles/components/btn.css",
        "/static/styles/pages/idea-hub.css",
    )
) {
    appHeader(user = user) {
        link("Ideas", "/ideas").current(idea.name)
    }
    main {
        container {
            div("idea-detail") {
                ideaDetailHeader(user.id, idea)
                ideaRatingDistribution(idea, comments)
                ideaCommentsSection(user.id, idea, comments)
            }
        }
    }
}

private fun FlowContent.ideaDetailHeader(userId: Int, idea: Idea) {
    section("idea-detail__header") {
        div("idea-detail__title-row") {
            div {
                h1("idea-detail__name") { +idea.name }
                p("idea-detail__meta") {
                    +"by ${idea.author.name} • ${idea.createdAt.formatAsRelativeOrDate()} • ${"★".repeat(min(idea.rating.average.toInt().coerceAtLeast(0), 5))}"
                }
            }
            if (userId == idea.createdBy) {
                button(classes = "btn btn--danger btn--sm") {
                    hxDeleteWithConfirm(
                        url = Link.Ideas.single(idea.id),
                        title = "Delete Idea",
                        description = "This action cannot be undone. Projects imported from this idea will not be impacted.",
                        warning = "⚠ The idea \"${idea.name}\" along with ratings and comments will be permanently deleted.",
                        confirmText = idea.name
                    )
                    hxSwap("none")
                    +"Delete"
                }
            }
        }

        div("idea-detail__badges") {
            span("badge") { +idea.category.toPrettyEnumName() }
            span("badge") { +idea.difficulty.toPrettyEnumName() }
            span("badge") { +idea.worksInVersionRange.toString() }
            idea.labels.forEach { label ->
                span("badge") { +label }
            }
        }

        p("idea-detail__description") { +idea.description }

        div("idea-detail__actions") {
            button(classes = "btn btn--ghost") {
                id = "idea-favorite-button"
                hxPut(Link.Ideas.single(idea.id) + "/favourite")
                hxTarget("#idea-favorite-button")
                hxSwap("outerHTML")
                +"♥ Favourite (${idea.favouritesCount})"
            }
            button(classes = "btn btn--primary") {
                hxGet(Link.Ideas.single(idea.id) + "/import/select")
                hxSwap("outerHTML")
                type = ButtonType.button
                +"Import to World"
            }
        }
    }
}

private fun FlowContent.ideaRatingDistribution(idea: Idea, comments: List<Comment>) {
    section("idea-detail__ratings") {
        h2("idea-detail__section-title") { +"Rating Distribution" }
        div("idea-ratings-layout") {
            div("idea-ratings__summary") {
                p("idea-ratings__average") { +"${"%.1f".format(idea.rating.average)}" }
                p { +"${"★".repeat(min(idea.rating.average.toInt().coerceAtLeast(0), 5))}" }
                p("idea-ratings__count") { +"${idea.rating.total} rating${if (idea.rating.total != 1) "s" else ""}" }
            }
            div("idea-ratings__bars") {
                val counts = getRatingCounts(comments)
                for (i in 5 downTo 1) {
                    div("idea-ratings__bar-row") {
                        span("idea-ratings__bar-label") { +"$i ★" }
                        val count = counts[i] ?: 0
                        val pct = if (idea.rating.total == 0) 0
                                  else (count * 100) / idea.rating.total
                        progressBar(current = count, total = idea.rating.total)
                        span("idea-ratings__bar-pct") { +"$pct%" }
                    }
                }
            }
        }
    }
}

private fun FlowContent.ideaCommentsSection(userId: Int, idea: Idea, comments: List<Comment>) {
    section("idea-detail__comments") {
        h2("idea-detail__section-title") { +"Comments and ratings" }
        p("idea-detail__section-subtitle") { +"Share your thoughts and rate this idea" }

        form("idea-comment-form") {
            encType = FormEncType.applicationXWwwFormUrlEncoded
            hxPost(Link.Ideas.single(idea.id) + "/comments")
            hxTarget("#idea-comments-list")
            hxSwap("afterbegin")
            attributes["hx-on::after-request"] =
                "this.reset(); document.getElementById('idea-comment-reset-rating-button').style.display = 'none';"

            textArea(classes = "form-control idea-comment-form__textarea") {
                id = "idea-comment-input-textarea"
                placeholder = "Add a comment…"
                name = "content"
                required = true
            }

            div("idea-comment-form__footer") {
                div("idea-comment-form__rating") {
                    span("idea-detail__section-subtitle") { +"Optional Rating:" }
                    div("idea-rating-stars") {
                        for (i in 5 downTo 1) {
                            radioInput {
                                id = "idea-rating-input-$i"
                                classes += "idea-rating-input"
                                name = "rating"
                                value = "$i"
                                onClick = "document.getElementById('idea-comment-reset-rating-button').classList.remove('idea-comment-reset--hidden');"
                            }
                            label {
                                classes += "idea-rating-label"
                                htmlFor = "idea-rating-input-$i"
                                +"★"
                            }
                        }
                    }
                    button(classes = "btn btn--ghost btn--sm idea-comment-reset--hidden") {
                        id = "idea-comment-reset-rating-button"
                        type = ButtonType.button
                        onClick = "document.querySelectorAll('.idea-rating-input').forEach(i => i.checked = false); this.classList.add('idea-comment-reset--hidden');"
                        +"Reset"
                    }
                }
                button(classes = "btn btn--primary") {
                    type = ButtonType.submit
                    +"Comment"
                }
            }
        }

        ul("idea-comments-list") {
            id = "idea-comments-list"
            comments.forEach { comment ->
                li {
                    ideaCommentItem(userId, comment)
                }
            }
        }
    }
}

fun LI.ideaCommentItem(userId: Int, comment: Comment) {
    id = "idea-comment-${comment.id}"
    classes += "idea-comment"
    div("idea-comment__header") {
        div("idea-comment__author-row") {
            span("idea-comment__author") { +comment.commenterName }
            when (comment) {
                is Comment.RatedTextComment -> comment.rating
                is Comment.RatingComment -> comment.rating
                is Comment.TextComment -> null
            }?.let { rating ->
                span("idea-comment__rating") { +" • $rating ★" }
            }
        }
        p("idea-comment__date") { +comment.createdAt.formatAsDateTime() }
    }
    when (comment) {
        is Comment.TextComment -> comment.content
        is Comment.RatingComment -> null
        is Comment.RatedTextComment -> comment.content
    }?.let { content ->
        p("idea-comment__content") { +content }
    }
    div("idea-comment__actions") {
        if (comment.commenterId == userId) {
            button(classes = "btn btn--ghost btn--sm") {
                hxDeleteWithConfirm(
                    url = Link.Ideas.single(comment.ideaId) + "/comments/${comment.id}",
                    title = "Delete Comment",
                    description = "This action cannot be undone.",
                    warning = ""
                )
                hxTarget("#idea-comment-${comment.id}")
                hxSwap("outerHTML")
                +"Delete"
            }
        }
    }
}

private fun getRatingCounts(comments: List<Comment>): Map<Int, Int> {
    val counts = (1..5).associateWith { 0 }.toMutableMap()
    comments.forEach { comment ->
        val rating = when (comment) {
            is Comment.RatingComment -> comment.rating
            is Comment.RatedTextComment -> comment.rating
            is Comment.TextComment -> null
        }
        if (rating != null && rating in 1..5) {
            counts[rating] = (counts[rating] ?: 0) + 1
        }
    }
    return counts
}
