package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.idea.Comment
import app.mcorg.domain.model.idea.Idea
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.hxPut
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.button.backButton
import app.mcorg.presentation.templated.common.button.ghostButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.chip.ChipVariant
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.page.createPage
import app.mcorg.presentation.templated.common.progress.progressComponent
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.ButtonType
import kotlinx.html.MAIN
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.footer
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.header
import kotlinx.html.id
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.onClick
import kotlinx.html.p
import kotlinx.html.radioInput
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.textArea
import kotlinx.html.ul
import java.time.format.DateTimeFormatter

fun ideaPage(
    user: TokenProfile,
    idea: Idea,
    comments: List<Comment>,
    unreadNotifications: Int
) = createPage(
    user = user,
    pageTitle = idea.name,
    unreadNotificationCount = unreadNotifications
) {
    id = "idea-page"
    backButton("Back to ideas", Link.Ideas)
    ideaContent(idea, comments)
}

fun MAIN.ideaContent(idea: Idea, comments: List<Comment>) {
    section {
        id = "idea-content"
        header {
            id = "idea-header"
            div {
                id = "idea-header-start"
                h2 {
                    + idea.name
                }
                p("subtle") {
                    + "by ${idea.author.name} • ${idea.createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} • ${idea.rating.average} stars"
                }
            }
            div {
                id = "idea-header-end"
                chipComponent {
                    variant = ChipVariant.NEUTRAL
                    + idea.category.toPrettyEnumName()
                }
            }
        }
        span {
            id = "idea-labels"
            chipComponent {
                variant = ChipVariant.NEUTRAL
                + idea.difficulty.toPrettyEnumName()
            }
            chipComponent {
                variant = ChipVariant.NEUTRAL
                + idea.worksInVersionRange.toString()
            }
            idea.labels.forEach {
                chipComponent {
                    variant = ChipVariant.NEUTRAL
                    + it
                }
            }
        }
        p("subtle") {
            id = "idea-description"
            + idea.description
        }
        footer {
            id = "idea-footer"
            neutralButton("Favorite (${idea.favouritesCount})") {
                buttonBlock = {
                    id = "idea-favorite-button"
                    hxPut(Link.Ideas.single(idea.id) + "/favourite")
                    hxTarget("#idea-favorite-button")
                    hxSwap("innerHTML")
                }
            }
            actionButton("Import Idea")
        }
    }
    section {
        id = "idea-ratings-summary"
        h3 {
            + "Rating Distribution"
        }
        div {
            id = "idea-ratings-summary-container"
            div {
                id = "idea-ratings-summary-start"
                p {
                    id = "idea-ratings-summary-header"
                    + "${idea.rating.average}"
                }
                p("subtle") {
                    + "${idea.rating.total} ratings"
                }
            }
            div {
                id = "idea-ratings-summary-end"
                val counts = getRatingCountByValues(comments)
                for (i in 5 downTo 1) {
                    div {
                        classes += "idea-rating-summary-row"
                        span {
                            classes += "idea-rating-summary-row-label"
                            + "$i stars"
                        }
                        val percentage = if (idea.rating.total == 0) 0 else ((counts[i] ?: 0) * 100) / idea.rating.total
                        progressComponent {
                            value = percentage.toDouble()
                            max = 100.0
                        }
                        span {
                            classes += "idea-rating-summary-row-percentage"
                            val percentage = if (idea.rating.total == 0) 0 else ((counts[i] ?: 0) * 100) / idea.rating.total
                            + "$percentage%"
                        }
                    }
                }
            }
        }
    }
    section {
        id = "idea-comments-section"
        h3 {
            + "Comments and ratings"
        }
        p("subtle") {
            + "Share your thoughts and rate this idea"
        }
        form {
            id = "idea-comment-form"
            textArea {
                id = "idea-comment-input-textarea"
                placeholder = "Add a comment..."
                name = "comment"
            }
            span {
                id = "idea-comment-input-end"
                span {
                    id = "idea-comment-rating-inputs"
                    label {
                        htmlFor = "idea-comment-input-1"
                        + "Optional rating:"
                    }
                    for (i in 1..5) {
                        radioInput {
                            id = "idea-rating-input-$i"
                            classes += "idea-rating-input"
                            name = "rating"
                            value = "$i"
                            onClick = "document.getElementById('idea-comment-reset-rating-button').style.display = 'inline-block';"
                        }
                    }
                    ghostButton("Reset rating") {
                        buttonBlock = {
                            id = "idea-comment-reset-rating-button"
                            style = "display: none;"
                            type = ButtonType.button
                            onClick = "document.querySelectorAll('.idea-rating-input').forEach(input => input.checked = false); this.style.display = 'none';"
                        }
                    }
                }
                actionButton("Comment") {
                    buttonBlock = {
                        id = "idea-comment-submit-button"
                    }
                }
            }
        }

        ul {
            id = "idea-comments-list"
            comments.forEach {
                li {
                    div {
                        classes += "idea-comment-header"
                        div {
                            classes += "idea-comment-header-content"
                            div {
                                classes += "idea-comment-author"
                                +it.commenterName
                                when (it) {
                                    is Comment.RatedTextComment -> it.rating
                                    is Comment.RatingComment -> it.rating
                                    is Comment.TextComment -> null
                                }?.let { rating ->
                                    span {
                                        classes += "idea-comment-rating"
                                        + "• Rated: $rating stars"
                                    }
                                }
                            }
                            div {
                                classes += "idea-comment-date"
                                +it.createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                            }
                        }
                        div {
                            classes += "idea-comment-actions"
                            if (it.youLiked) {
                                ghostButton("Unlike (${it.likes})")
                            } else {
                                ghostButton("Like (${it.likes})")
                            }
                        }
                    }

                    when (it) {
                        is Comment.TextComment -> it.content
                        is Comment.RatingComment -> null
                        is Comment.RatedTextComment -> it.content
                    }?.let { content ->
                        p {
                            classes += "idea-comment-content"
                            + content
                        }
                    }
                }
            }
        }
    }
}


private fun getRatingCountByValues(comments: List<Comment>): Map<Int, Int> {
    val ratingCounts = mutableMapOf<Int, Int>()
    for (i in 1..5) {
        ratingCounts[i] = 0
    }
    comments.forEach {
        when (it) {
            is Comment.RatingComment -> it.rating
            is Comment.RatedTextComment -> it.rating
            is Comment.TextComment -> null
        }?.let { rating ->
            if (rating in 1..5) rating else null
        }?.let { rating ->
            ratingCounts[rating] = ratingCounts.getOrDefault(rating, 0) + 1
        }
    }
    return ratingCounts
}