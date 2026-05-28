package app.mcorg.presentation.templated.dsl

import kotlinx.html.*

fun FlowContent.resourceSearch() {
    div("resource-search") {
        id = "resource-search"
        input(type = InputType.search, classes = "resource-search__input") {
            id = "resource-search-input"
            placeholder = "Search items..."
            attributes["aria-label"] = "Search resources"
        }
        div("resource-search__sort") {
            id = "resource-sort"
            button(classes = "btn btn--ghost btn--sm resource-sort__btn resource-sort__btn--active") {
                id = "sort-name"
                type = ButtonType.button
                attributes["data-sort"] = "name"
                +"Name A-Z"
            }
            button(classes = "btn btn--ghost btn--sm resource-sort__btn") {
                id = "sort-progress-asc"
                type = ButtonType.button
                attributes["data-sort"] = "progress-asc"
                +"Progress \u25b2"
            }
            button(classes = "btn btn--ghost btn--sm resource-sort__btn") {
                id = "sort-progress-desc"
                type = ButtonType.button
                attributes["data-sort"] = "progress-desc"
                +"Progress \u25bc"
            }
            button(classes = "btn btn--ghost btn--sm resource-sort__btn") {
                id = "sort-required-desc"
                type = ButtonType.button
                attributes["data-sort"] = "required-desc"
                +"Amount \u25bc"
            }
        }
    }
}
