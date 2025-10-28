package app.mcorg.presentation.templated.idea

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.searchField.SearchFieldHxValues
import app.mcorg.presentation.templated.common.searchField.searchField
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import kotlinx.html.*

/**
 * Main filter sidebar component.
 * Renders base filters (always visible) and dynamic category-specific filters (loaded via HTMX).
 */
fun ASIDE.ideaFilter(selectedCategory: IdeaCategory? = null) {
    id = "ideas-filter"
    classes += "stack stack--md"

    // Filter header with title and clear button
    filterHeader()

    // Filter form that triggers HTMX updates
    form {
        id = "idea-filter-form"
        attributes["hx-get"] = Link.Ideas.to + "/search"
        attributes["hx-target"] = "#ideas-list"
        attributes["hx-trigger"] = """
            change delay:400ms
            from:#idea-filter-form,
            submit
        """.trimIndent()
        attributes["hx-swap"] = "outerHTML"

        // Text search (debounced)
        filterSearchInput()

        // Base filters
        filterCategoryRadios(selectedCategory)
        filterDifficulty()
        filterRating()
        filterMinecraftVersion()

        // Dynamic category-specific filters (loaded via HTMX)
        div {
            id = "category-filters"
            classes += "stack stack--sm"
            // Will be populated by HTMX when category is selected
        }
    }
}

/**
 * Filter header with title and clear button
 */
fun ASIDE.filterHeader() {
    classes += "filter-header"
    h2 {
        +"Filters"
    }
    neutralButton("Clear All") {
        href = Link.Ideas.to
    }
}

/**
 * Text search input with debounced HTMX trigger
 */
fun FORM.filterSearchInput() {
    div("filter-group") {
        label {
            htmlFor = "filter-search"
            +"Search Ideas"
        }
        searchField("filter-search") {
            placeHolder = "Search..."
            extraClasses = setOf("form-control--sm", "filter-field")
            hxValues = SearchFieldHxValues(
                hxGet = Link.Ideas.to + "/search",
                hxTarget = "#ideas-list",
                hxInclude = "#idea-filter-form",
                hxTrigger = "keyup changed delay:500ms",
            )
        }
    }
}

/**
 * Category filter using radio buttons (exclusive selection)
 */
fun FORM.filterCategoryRadios(selectedCategory: IdeaCategory?) {
    div("filter-group") {
        label {
            +"Category"
        }
        div("stack stack--xs") {
            // "All" option (no category filter)
            label("filter-radio-label") {
                input(type = InputType.radio, classes = "filter-field") {
                    name = "category"
                    value = ""
                    if (selectedCategory == null) {
                        checked = true
                    }
                    attributes["hx-get"] = Link.Ideas.to + "/filters/clear"
                    attributes["hx-target"] = "#category-filters"
                    attributes["hx-swap"] = "innerHTML"
                }
                +"All Categories"
            }

            // Individual categories
            IdeaCategory.entries.forEach { category ->
                label("filter-radio-label") {
                    input(type = InputType.radio, classes = "filter-field") {
                        name = "category"
                        value = category.name
                        if (selectedCategory == category) {
                            checked = true
                        }
                        attributes["hx-get"] = Link.Ideas.to + "/filters/${category.name.lowercase()}"
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

/**
 * Difficulty filter with checkboxes
 */
fun FORM.filterDifficulty() {
    div("filter-group") {
        label {
            +"Difficulty"
        }
        div("stack stack--xs") {
            IdeaDifficulty.entries.forEach { difficulty ->
                label("filter-checkbox-label") {
                    input(type = InputType.checkBox, classes = "filter-field") {
                        name = "difficulty[]"
                        value = difficulty.name
                    }
                    +difficulty.toPrettyEnumName()
                }
            }
        }
    }
}

/**
 * Minimum rating filter
 */
fun FORM.filterRating() {
    div("filter-group") {
        label {
            htmlFor = "filter-min-rating"
            +"Minimum Rating"
        }
        input(type = InputType.number, classes = "form-control form-control--sm filter-field") {
            id = "filter-min-rating"
            name = "minRating"
            placeholder = "e.g., 4.0"
            attributes["min"] = "0"
            attributes["max"] = "5"
            attributes["step"] = "0.1"
        }
    }
}

/**
 * Minecraft version filter
 */
fun FORM.filterMinecraftVersion() {
    div("filter-group") {
        label {
            htmlFor = "filter-minecraft-version"
            +"Minecraft Version"
        }
        input(type = InputType.text, classes = "form-control form-control--sm filter-field") {
            id = "filter-minecraft-version"
            name = "minecraftVersion"
            placeholder = "e.g., 1.20.1"
        }
    }
}

/**
 * Renders an empty state when "All Categories" is selected
 */
fun DIV.emptyCategoryFilters() {
    p("subtle") {
        style = "text-align: center; padding: var(--spacing-sm);"
        +"Select a category to see specific filters"
    }
}

