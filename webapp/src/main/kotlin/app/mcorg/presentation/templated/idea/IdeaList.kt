package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.idea.Idea
import app.mcorg.presentation.templated.common.chip.ChipSize
import app.mcorg.presentation.templated.common.chip.ChipVariant
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.utils.formatAsRelativeOrDate
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.LI
import kotlinx.html.UL
import kotlinx.html.a
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.ul

fun UL.ideaList(ideas: List<Idea>) {
    id = "ideas-list"
    ideas.forEach { idea ->
        li {
            ideaListItem(idea)
        }
    }
}

fun LI.ideaListItem(idea: Idea) {
    classes += "idea-list-item"
    a {
        href = Link.Ideas.single(idea.id)
        div("idea-list-item-header") {
            div("idea-list-item-header-start") {
                h2 {
                    +idea.name
                }
                p("subtle") {
                    +"by ${idea.author.name} • ${idea.createdAt.formatAsRelativeOrDate()}"
                }
            }
            div("idea-list-item-header-end") {
                chipComponent {
                    variant = ChipVariant.NEUTRAL
                    +idea.category.toPrettyEnumName()
                }
            }
        }
        p("subtle") {
            val maxDescriptionLength = 100
            if (idea.description.length > maxDescriptionLength) {
                val lastPeriodIndex = idea.description.indexOf('.', maxDescriptionLength)
                if (lastPeriodIndex != -1) {
                    +idea.description.take(lastPeriodIndex + 1)
                    +"."
                } else {
                    +idea.description.take(maxDescriptionLength)
                    +" "
                }
            } else {
                +idea.description
            }
        }
        ul {
            classes += "idea-list-item-labels"
            idea.labels.forEach { label ->
                li {
                    chipComponent {
                        size = ChipSize.SMALL
                        variant = ChipVariant.NEUTRAL
                        +label
                    }
                }
            }
        }
        div("idea-list-item-footer") {
            div("idea-list-item-footer-start") {
                p("subtle") {
                    +"${idea.favouritesCount} favourites • ${"%.1f".format(idea.rating.average)} ⭐ (${idea.rating.total} ratings)"
                }
            }
            div("idea-list-item-footer-end") {
                chipComponent {
                    size = ChipSize.SMALL
                    variant = ChipVariant.NEUTRAL
                    +idea.difficulty.toPrettyEnumName()
                }
                chipComponent {
                    size = ChipSize.SMALL
                    variant = ChipVariant.NEUTRAL
                    +idea.worksInVersionRange.toString()
                }
            }
        }
    }
}
