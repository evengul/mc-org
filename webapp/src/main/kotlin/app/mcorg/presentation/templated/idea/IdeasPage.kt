package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.idea.Idea
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.templated.common.chip.ChipVariant
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.modal.formModal
import app.mcorg.presentation.templated.common.page.createPage
import app.mcorg.presentation.templated.common.tabs.TabData
import app.mcorg.presentation.templated.common.tabs.tabsComponent
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.ASIDE
import kotlinx.html.DIV
import kotlinx.html.HEADER
import kotlinx.html.LI
import kotlinx.html.UL
import kotlinx.html.aside
import kotlinx.html.classes
import kotlinx.html.code
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.header
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.ul
import java.time.format.DateTimeFormatter

fun ideasPage(
    user: TokenProfile,
    ideas: List<Idea>,
    unreadNotifications: Int
) = createPage(
    user = user,
    pageTitle = "Ideas",
    unreadNotificationCount = unreadNotifications
) {
    id = "ideas-page"
    header {
        ideasHeader(user)
    }
    section {
        id = "ideas-content"
        aside {
            ideaFilter()
        }
        div {
            ideaListContainer(ideas)
        }
    }
}

fun HEADER.ideasHeader(user: TokenProfile) {
    id = "ideas-header"
    div {
        id = "ideas-header-start"
        h1 {
            +"Idea Bank"
        }
        p {
            classes += "subtle"
            + "Browse and share Minecraft project ideas with the community"
        }
    }
    div {
        id = "ideas-header-end"
        if (user.isIdeaCreator) {
            createIdeaModal()
        }
    }
}

fun DIV.createIdeaModal() = formModal(
    modalId = "ideas-create-modal",
    title = "Submit New Idea",
)

fun ASIDE.ideaFilter() {
    id = "ideas-filter"
    h2 {
        + "Filters"
    }
    code {
        + "Coming soon..."
    }
}

fun DIV.ideaListContainer(ideas: List<Idea>) {
    id = "ideas-list-container"
    tabsComponent(
        TabData.create("all", "All"),
        TabData.create("builds", "Builds"),
        TabData.create("farms", "Farms"),
        TabData.create("storage", "Storage"),
        TabData.create("cart_tech", "Cart Tech"),
        TabData.create("tnt", "TNT"),
        TabData.create("slimestone", "Slimestone"),
        TabData.create("other", "Other"),
    ) {
        hxTarget = "#ideas-list"
        activeTab = "all"
    }
    ul {
        ideaList(ideas)
    }
}

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
    div("idea-list-item-header") {
        div("idea-list-item-header-start") {
            h2 {
                + idea.name
            }
            p("subtle") {
                + "by ${idea.author.name} • ${idea.createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}"
            }
        }
        div("idea-list-item-header-end") {
            chipComponent {
                variant = ChipVariant.NEUTRAL
                + idea.category.toPrettyEnumName()
            }
        }
    }
    p("subtle") {
        val maxDescriptionLength = 100
        if (idea.description.length > maxDescriptionLength) {
            val lastPeriodIndex = idea.description.indexOf('.', maxDescriptionLength)
            if (lastPeriodIndex != -1) {
                + idea.description.take(lastPeriodIndex + 1)
                + "."
            } else {
                + idea.description.take(maxDescriptionLength)
                + " "
            }
        } else {
            + idea.description
        }
    }
    ul {
        classes += "idea-list-item-labels"
        idea.labels.forEach { label ->
            li {
                chipComponent {
                    variant = ChipVariant.NEUTRAL
                    + label
                }
            }
        }
    }
    div("idea-list-item-footer") {
        div("idea-list-item-footer-start") {
            p("subtle") {
                + "${idea.favouritesCount} favourites • ${"%.1f".format(idea.rating.average)} ⭐ (${idea.rating.total} ratings)"
            }
        }
        div("idea-list-item-footer-end") {
            chipComponent {
                variant = ChipVariant.NEUTRAL
                + idea.difficulty.toPrettyEnumName()
            }
            chipComponent {
                variant = ChipVariant.NEUTRAL
                + idea.worksInVersionRange.toString()
            }
        }
    }
}


