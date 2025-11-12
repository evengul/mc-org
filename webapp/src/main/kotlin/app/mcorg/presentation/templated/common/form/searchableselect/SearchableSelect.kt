package app.mcorg.presentation.templated.common.form.searchableselect

import app.mcorg.presentation.*
import app.mcorg.presentation.templated.common.component.LeafComponent
import app.mcorg.presentation.templated.common.component.addComponent
import kotlinx.html.*

/**
 * Configuration for a searchable select option
 * @param T The type of the option value
 * @property value The actual value of the option
 * @property label The display text for the option
 * @property searchTerms Additional search keywords (defaults to just the label)
 * @property disabled Whether this option is disabled
 * @property metadata Additional data attributes to add to the option element
 */
data class SearchableSelectOption<T>(
    val value: T,
    val label: String,
    val searchTerms: List<String> = listOf(label),
    val disabled: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * HTMX configuration for server-side filtering (optional)
 * @property hxGet The endpoint URL for fetching filtered options
 * @property hxTarget The target element to update with the response
 * @property hxInclude Additional elements to include in the request
 * @property hxSwap The swap strategy (default: innerHTML)
 * @property hxTrigger The trigger event (default: input with 300ms delay)
 */
data class SearchableSelectHxConfig(
    val hxGet: String,
    val hxTarget: String,
    val hxInclude: String? = null,
    val hxSwap: String = "innerHTML",
    val hxTrigger: String = "input changed delay:300ms"
)

/**
 * Size variants for the searchable select component
 */
enum class SearchableSelectSize {
    SMALL,
    MEDIUM,
    LARGE
}

/**
 * Generic searchable select component with client-side search and keyboard navigation.
 *
 * Features:
 * - Type-safe generic options of type T
 * - Client-side search with lazy rendering (shows results only after user types)
 * - Full keyboard navigation (Arrow keys, Enter, Escape, Tab)
 * - ARIA compliant for accessibility
 * - Optional HTMX server-side search integration
 * - Mobile responsive (bottom sheet on small screens)
 *
 * Usage Examples:
 * ```kotlin
 * // Simple string options
 * searchableSelect(
 *     id = "country-select",
 *     name = "country",
 *     options = listOf("USA", "Canada", "Mexico").map {
 *         SearchableSelectOption(it, it)
 *     }
 * ) {
 *     placeholder = "Select a country..."
 *     required = true
 * }
 *
 * // Enum options
 * searchableSelect(
 *     id = "project-type-select",
 *     name = "type",
 *     options = ProjectType.entries.map {
 *         SearchableSelectOption(
 *             value = it,
 *             label = it.displayName,
 *             searchTerms = listOf(it.displayName, it.name)
 *         )
 *     }
 * ) {
 *     valueSerializer = { it.name }
 *     selectedValue = ProjectType.STRUCTURE
 * }
 *
 * // Complex objects with search terms
 * searchableSelect(
 *     id = "item-select",
 *     name = "itemId",
 *     options = minecraftItems.map { item ->
 *         SearchableSelectOption(
 *             value = item.id,
 *             label = item.displayName,
 *             searchTerms = listOf(item.displayName, item.id, item.category),
 *             metadata = mapOf("category" to item.category)
 *         )
 *     }
 * ) {
 *     valueSerializer = { it.toString() }
 *     placeholder = "Search for an item..."
 *     minQueryLength = 2
 *     maxDisplayedResults = 200
 * }
 * ```
 *
 * @param T The type of option values (e.g., String, Int, Enum, custom classes)
 * @property id Unique identifier for the component
 * @property name Form field name for submission
 * @property options List of available options
 * @property selectedValue Currently selected value (optional)
 * @property placeholder Text shown when no value is selected
 * @property searchPlaceholder Placeholder text for the search input
 * @property emptyMessage Message shown when no options match the search
 * @property minQueryMessage Message shown when query is below minimum length
 * @property tooManyResultsMessage Message template shown when results exceed max display (use {count} for result count)
 * @property size Visual size variant
 * @property required Whether the field is required for form submission
 * @property disabled Whether the component is disabled
 * @property hxConfig Optional HTMX configuration for server-side search
 * @property extraClasses Additional CSS classes to apply
 * @property valueSerializer Function to convert T to string for form submission
 * @property minQueryLength Minimum characters before showing results (lazy rendering)
 * @property maxDisplayedResults Maximum number of results to render in DOM
 */
class SearchableSelect<T>(
    val id: String,
    val name: String,
    val options: List<SearchableSelectOption<T>>,
    var selectedValue: T? = null,
    var placeholder: String = "Select an option...",
    var searchPlaceholder: String = "Search...",
    var emptyMessage: String = "No options found",
    var minQueryMessage: String = "Type to search...",
    var tooManyResultsMessage: String = "Showing {count} results. Refine your search for more.",
    var size: SearchableSelectSize = SearchableSelectSize.MEDIUM,
    var required: Boolean = false,
    var disabled: Boolean = false,
    var hxConfig: SearchableSelectHxConfig? = null,
    var extraClasses: Set<String> = setOf(),
    var valueSerializer: (T) -> String = { it.toString() },
    var minQueryLength: Int = 2,
    var maxDisplayedResults: Int = 200
) : LeafComponent() {

    override fun render(container: TagConsumer<*>) {
        container.div("searchable-select ${getSizeClass()} ${extraClasses.joinToString(" ")}".trim()) {
            this.id = this@SearchableSelect.id
            attributes["data-searchable-select"] = this@SearchableSelect.id
            attributes["data-min-query-length"] = minQueryLength.toString()
            attributes["data-max-displayed-results"] = maxDisplayedResults.toString()

            if (disabled) {
                attributes["data-disabled"] = "true"
            }

            // Hidden input for form submission
            hiddenInput {
                this.id = "${this@SearchableSelect.id}-value"
                this.name = this@SearchableSelect.name
                selectedValue?.let {
                    attributes["value"] = valueSerializer(it)
                }
                this.required = this@SearchableSelect.required
            }

            // Display button/trigger
            button(classes = "searchable-select__trigger form-control") {
                type = ButtonType.button
                this.id = "${this@SearchableSelect.id}-trigger"
                disabled = this@SearchableSelect.disabled
                attributes["aria-haspopup"] = "listbox"
                attributes["aria-expanded"] = "false"
                attributes["aria-controls"] = "${this@SearchableSelect.id}-dropdown"

                span("searchable-select__trigger-text") {
                    val selected = options.find { opt ->
                        selectedValue?.let { valueSerializer(it) == valueSerializer(opt.value) } ?: false
                    }
                    if (selected != null) {
                        +selected.label
                    } else {
                        classes = classes + "searchable-select__trigger-text--placeholder"
                        +placeholder
                    }
                }

                // Clear button (shown only when value is selected)
                span("searchable-select__clear") {
                    style = "display: none;"
                    attributes["aria-label"] = "Clear selection"
                    attributes["role"] = "button"
                    attributes["tabindex"] = "0"
                    unsafe { raw("&#10005;") } // × symbol
                }

                span("searchable-select__trigger-icon") {
                    // Chevron down icon
                    unsafe { raw("&#9660;") }
                }
            }

            // Dropdown container
            div("searchable-select__dropdown") {
                attributes["id"] = "${this@SearchableSelect.id}-dropdown"
                attributes["role"] = "listbox"
                attributes["aria-labelledby"] = "${this@SearchableSelect.id}-trigger"
                attributes["hidden"] = ""

                // Search input wrapper
                div("searchable-select__search-wrapper") {
                    input(type = InputType.search, classes = "searchable-select__search form-control form-control--sm") {
                        id = "${this@SearchableSelect.id}-search"
                        placeholder = this@SearchableSelect.searchPlaceholder
                        autoComplete = false
                        attributes["aria-label"] = "Search options"
                        // Apply HTMX config if provided for server-side search
                        hxConfig?.let { config ->
                            hxGet(config.hxGet)
                            hxTarget(config.hxTarget)
                            config.hxInclude?.let { hxInclude(it) }
                            hxSwap(config.hxSwap)
                            hxTrigger(config.hxTrigger)
                        }
                    }
                }

                // Options list
                div("searchable-select__options") {
                    id = "${this@SearchableSelect.id}-options"
                    attributes["data-min-query-message"] = minQueryMessage
                    attributes["data-empty-message"] = emptyMessage
                    attributes["data-too-many-message"] = tooManyResultsMessage

                    // Render all options (initially hidden, shown by JS after search)
                    if (options.isEmpty()) {
                        div("searchable-select__message searchable-select__message--empty") {
                            +emptyMessage
                        }
                    } else {
                        // Min query message (shown initially)
                        div("searchable-select__message searchable-select__message--min-query") {
                            +minQueryMessage
                        }

                        // Render all options
                        options.forEach { option ->
                            renderOption(option)
                        }
                    }
                }
            }
        }
    }

    private fun DIV.renderOption(option: SearchableSelectOption<T>) {
        div("searchable-select__option") {
            role = "option"
            attributes["data-value"] = valueSerializer(option.value)
            attributes["data-search-terms"] = option.searchTerms.joinToString("|").lowercase()
            attributes["data-hidden"] = "true" // Initially hidden (lazy rendering)

            if (option.disabled) {
                attributes["data-disabled"] = "true"
                attributes["aria-disabled"] = "true"
            }

            val isSelected = selectedValue?.let { valueSerializer(it) == valueSerializer(option.value) } ?: false
            if (isSelected) {
                attributes["data-selected"] = "true"
                attributes["aria-selected"] = "true"
            }

            // Add custom metadata as data attributes
            option.metadata.forEach { (key, value) ->
                attributes["data-$key"] = value
            }

            +option.label
        }
    }

    private fun getSizeClass(): String = when (size) {
        SearchableSelectSize.SMALL -> "searchable-select--sm"
        SearchableSelectSize.MEDIUM -> ""
        SearchableSelectSize.LARGE -> "searchable-select--lg"
    }
}

/**
 * Extension function for easy usage in HTML templates with explicit type.
 *
 * Example:
 * ```kotlin
 * div {
 *     searchableSelect<String>(
 *         id = "my-select",
 *         name = "myField",
 *         options = myOptions
 *     ) {
 *         placeholder = "Choose one..."
 *         required = true
 *     }
 * }
 * ```
 */
inline fun <reified V, T : Tag> T.searchableSelect(
    id: String,
    name: String,
    options: List<SearchableSelectOption<V>>,
    noinline block: SearchableSelect<V>.() -> Unit = {}
) {
    val component = SearchableSelect(id, name, options)
    block.invoke(component)
    this.addComponent(component)
}

