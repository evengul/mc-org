package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.idea.Comment
import app.mcorg.domain.model.idea.Idea
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.hxDeleteWithConfirm
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxPut
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import kotlinx.html.stream.createHTML
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
        "/static/styles/components/form.css",
        "/static/styles/pages/idea-hub.css",
    )
) {
    appHeader(user = user) {
        link("Ideas", "/ideas").current(idea.name)
    }
    main {
        container {
            div("idea-detail") {
                ideaDetailHeader(user, idea)
                ideaRatingDistribution(idea, comments)
                ideaCommentsSection(user.id, idea, comments)
            }
        }
    }
}

private fun FlowContent.ideaDetailHeader(user: TokenProfile, idea: Idea) {
    section("idea-detail__header") {
        div("idea-detail__title-row") {
            div {
                h1("idea-detail__name") { +idea.name }
                p("idea-detail__meta") {
                    +"by ${idea.author.name} • ${idea.createdAt.formatAsRelativeOrDate()} • ${"★".repeat(min(idea.rating.average.toInt().coerceAtLeast(0), 5))}"
                }
            }
            if (user.id == idea.createdBy || user.isSuperAdmin) {
                div(classes = "idea-detail__header-actions") {
                    button(classes = "btn btn--ghost btn--sm") {
                        hxPost(Link.Ideas.single(idea.id) + "/revert")
                        hxSwap("none")
                        attributes["hx-confirm"] = "Edit this Idea"
                        attributes["data-hx-delete-confirm"] = "true"
                        attributes["data-hx-delete-confirm-title"] = "Edit this Idea"
                        attributes["data-hx-delete-confirm-description"] = "Do you want to edit this idea? It will be reverted to a draft, and you'll have to republish it to make it visible to others again."
                        +"Edit"
                    }
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
        id = "idea-rating-distribution"
        ratingDistributionContent(idea.rating.total, idea.rating.average, getRatingCounts(comments))
    }
}

fun ideaRatingDistributionOob(total: Int, average: Double, countPerStar: Map<Int, Int>): String =
    createHTML().section("idea-detail__ratings") {
        id = "idea-rating-distribution"
        hxOutOfBands("outerHTML")
        ratingDistributionContent(total, average, countPerStar)
    }

private fun SECTION.ratingDistributionContent(total: Int, average: Double, countPerStar: Map<Int, Int>) {
    h2("idea-detail__section-title") { +"Rating Distribution" }
    if (total == 0) {
        p("idea-detail__section-subtitle") { +"Be the first to rate this idea" }
    } else {
        div("idea-ratings-layout") {
            div("idea-ratings__summary") {
                p("idea-ratings__average") { +"${"%.1f".format(average)}" }
                p { +"${"★".repeat(min(average.toInt().coerceAtLeast(0), 5))}" }
                p("idea-ratings__count") { +"$total rating${if (total != 1) "s" else ""}" }
            }
            div("idea-ratings__bars") {
                for (i in 5 downTo 1) {
                    div("idea-ratings__bar-row") {
                        span("idea-ratings__bar-label") { +"$i ★" }
                        val count = countPerStar[i] ?: 0
                        val pct = (count * 100) / total
                        progressBar(current = count, total = total)
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

        div {
            id = "idea-comment-input-section"
            if (comments.any { it.commenterId == userId }) {
                p("idea-detail__section-subtitle") { +"You have already commented on this idea." }
            } else {
                ideaCommentForm(idea.id)
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

private fun FlowContent.ideaCommentForm(ideaId: Int) {
    form {
        classes = setOf("idea-comment-form")
        encType = FormEncType.applicationXWwwFormUrlEncoded
        hxPost(Link.Ideas.single(ideaId) + "/comments")
        hxTarget("#idea-comments-list")
        hxSwap("afterbegin")
        attributes["hx-on::after-request"] =
            "this.reset(); document.getElementById('idea-comment-reset-rating-button').classList.add('idea-comment-reset--hidden');"

        div("idea-comment-form__rating") {
            span("idea-detail__section-subtitle") { +"Rating (optional)" }
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

        textArea(classes = "form-control idea-comment-form__textarea") {
            id = "idea-comment-input-textarea"
            placeholder = "Add a comment… (optional)"
            name = "content"
        }

        div("idea-comment-form__footer") {
            button(classes = "btn btn--primary") {
                type = ButtonType.submit
                +"Post"
            }
        }
    }
}

fun ideaCommentFormOob(ideaId: Int): String =
    createHTML().div {
        id = "idea-comment-input-section"
        hxOutOfBands("outerHTML")
        ideaCommentForm(ideaId)
    }

fun ideaAlreadyCommentedOob(): String =
    createHTML().div {
        id = "idea-comment-input-section"
        hxOutOfBands("outerHTML")
        p("idea-detail__section-subtitle") { +"You have already commented on this idea." }
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
                hxSwap("delete")
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
