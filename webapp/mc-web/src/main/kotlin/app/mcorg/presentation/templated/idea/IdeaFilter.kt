package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.*

fun DIV.emptyCategoryFilters() {
    p("ideas-filter__label") { +"Select a category for specific filters" }
}

/**
 * Main filter sidebar component.
 * HTMX target is #ideas-list-container (wraps both cards and pagination).
 */
fun ASIDE.ideaFilter(selectedCategory: IdeaCategory? = null) {
    div("ideas-filter__header") {
        h2("ideas-filter__title") { +"Filters" }
        a(classes = "btn btn--ghost btn--sm") {
            href = Link.Ideas.to
            +"Clear All"
        }
    }

    form {
        id = "idea-filter-form"
        attributes["hx-get"] = "${Link.Ideas.to}/search"
        attributes["hx-target"] = "#ideas-list-container"
        attributes["hx-trigger"] = "change delay:400ms from:#idea-filter-form, submit"
        attributes["hx-swap"] = "outerHTML"

        filterSearchInput()
        filterCategoryRadios(selectedCategory)
        filterDifficulty()
        filterRating()
        filterMinecraftVersion()

        div {
            id = "category-filters"
        }
    }
}

fun FORM.filterSearchInput() {
    div("ideas-filter__group") {
        label {
            htmlFor = "filter-search"
            +"Search"
        }
        input(type = InputType.text, classes = "form-control ideas-filter__input") {
            id = "filter-search"
            name = "query"
            placeholder = "Search ideas…"
            attributes["hx-get"] = "${Link.Ideas.to}/search"
            attributes["hx-target"] = "#ideas-list-container"
            attributes["hx-trigger"] = "input delay:400ms"
            attributes["hx-swap"] = "outerHTML"
            attributes["hx-include"] = "#idea-filter-form"
        }
    }
}

fun FORM.filterCategoryRadios(selectedCategory: IdeaCategory?) {
    div("ideas-filter__group") {
        p("ideas-filter__label") { +"Category" }
        div("ideas-filter__options") {
            label("ideas-filter__radio-label") {
                input(type = InputType.radio, classes = "ideas-filter__radio") {
                    name = "category"
                    value = ""
                    checked = selectedCategory == null
                    attributes["hx-get"] = "${Link.Ideas.to}/filters/clear"
                    attributes["hx-target"] = "#category-filters"
                    attributes["hx-swap"] = "innerHTML"
                }
                +"All Categories"
            }
            IdeaCategory.entries.forEach { category ->
                label("ideas-filter__radio-label") {
                    input(type = InputType.radio, classes = "ideas-filter__radio") {
                        name = "category"
                        value = category.name
                        checked = selectedCategory == category
                        attributes["hx-get"] = "${Link.Ideas.to}/filters/${category.name.lowercase()}"
                        attributes["hx-target"] = "#category-filters"
                        attributes["hx-swap"] = "innerHTML"
                        attributes["hx-include"] = "#idea-filter-form"
                    }
                    +category.toPrettyEnumName()
                }
            }
        }
    }
}

fun FORM.filterDifficulty() {
    div("ideas-filter__group") {
        p("ideas-filter__label") { +"Difficulty" }
        div("ideas-filter__options") {
            IdeaDifficulty.entries.forEach { difficulty ->
                label("ideas-filter__checkbox-label") {
                    input(type = InputType.checkBox, classes = "ideas-filter__checkbox") {
                        name = "difficulty[]"
                        value = difficulty.name
                    }
                    +difficulty.toPrettyEnumName()
                }
            }
        }
    }
}

fun FORM.filterRating() {
    div("ideas-filter__group") {
        label("ideas-filter__label") {
            htmlFor = "filter-min-rating"
            +"Min Rating"
        }
        input(type = InputType.number, classes = "form-control ideas-filter__input") {
            id = "filter-min-rating"
            name = "minRating"
            placeholder = "0 – 5"
            attributes["min"] = "0"
            attributes["max"] = "5"
            attributes["step"] = "0.1"
        }
    }
}

fun FORM.filterMinecraftVersion() {
    div("ideas-filter__group") {
        label("ideas-filter__label") {
            htmlFor = "filter-minecraft-version"
            +"Minecraft Version"
        }
        input(type = InputType.text, classes = "form-control ideas-filter__input") {
            id = "filter-minecraft-version"
            name = "minecraftVersion"
            placeholder = "e.g. 1.20.1"
        }
    }
}
