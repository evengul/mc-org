package app.mcorg.presentation.templated.common.form.searchableselect

import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Test enum for value serializer tests
enum class TestEnum { OPTION_A, OPTION_B }

/**
 * Tests for SearchableSelect component
 */
class SearchableSelectTest {

    // Helper function to render component to HTML string
    private fun <T> renderComponent(component: SearchableSelect<T>): String {
        return createHTML().div {
            component.render(consumer)
        }
    }

    @Test
    fun `searchable select renders with correct structure`() {
        val options = listOf(
            SearchableSelectOption("value1", "Label 1"),
            SearchableSelectOption("value2", "Label 2"),
            SearchableSelectOption("value3", "Label 3")
        )

        val component = SearchableSelect(
            id = "test-select",
            name = "testField",
            options = options
        )

        val html = renderComponent(component)

        // Verify basic structure
        assertTrue(html.contains("data-searchable-select=\"test-select\""))
        assertTrue(html.contains("searchable-select__trigger"))
        assertTrue(html.contains("searchable-select__dropdown"))
        assertTrue(html.contains("searchable-select__search"))
        assertTrue(html.contains("searchable-select__options"))
    }

    @Test
    fun `searchable select renders hidden input with correct name`() {
        val options = listOf(SearchableSelectOption("value1", "Label 1"))

        val component = SearchableSelect(
            id = "test-select",
            name = "myFieldName",
            options = options
        )

        val html = renderComponent(component)

        assertTrue(html.contains("id=\"test-select-value\""))
        assertTrue(html.contains("name=\"myFieldName\""))
    }

    @Test
    fun `searchable select renders all options with data attributes`() {
        val options = listOf(
            SearchableSelectOption("val1", "Option One", searchTerms = listOf("option", "one", "first")),
            SearchableSelectOption("val2", "Option Two", searchTerms = listOf("option", "two", "second"))
        )

        val component = SearchableSelect(
            id = "test-select",
            name = "test",
            options = options
        )

        val html = renderComponent(component)

        assertTrue(html.contains("data-value=\"val1\""))
        assertTrue(html.contains("data-value=\"val2\""))
        assertTrue(html.contains("data-search-terms=\"option|one|first\""))
        assertTrue(html.contains("data-search-terms=\"option|two|second\""))
        assertTrue(html.contains("Option One"))
        assertTrue(html.contains("Option Two"))
    }

    @Test
    fun `searchable select marks selected option correctly`() {
        val options = listOf(
            SearchableSelectOption("value1", "Label 1"),
            SearchableSelectOption("value2", "Label 2"),
            SearchableSelectOption("value3", "Label 3")
        )

        val component = SearchableSelect(
            id = "test-select",
            name = "test",
            options = options,
            selectedValue = "value2"
        )

        val html = renderComponent(component)

        // Should have selected value in hidden input
        assertTrue(html.contains("value=\"value2\""))

        // Should show selected label in trigger
        assertTrue(html.contains("Label 2"))
    }

    @Test
    fun `searchable select shows placeholder when no value selected`() {
        val options = listOf(SearchableSelectOption("value1", "Label 1"))

        val component = SearchableSelect(
            id = "test-select",
            name = "test",
            options = options,
            placeholder = "Choose an option..."
        )

        val html = renderComponent(component)

        assertTrue(html.contains("Choose an option..."))
        assertTrue(html.contains("searchable-select__trigger-text--placeholder"))
    }

    @Test
    fun `searchable select renders disabled state correctly`() {
        val options = listOf(SearchableSelectOption("value1", "Label 1"))

        val component = SearchableSelect(
            id = "test-select",
            name = "test",
            options = options,
            disabled = true
        )

        val html = renderComponent(component)

        assertTrue(html.contains("data-disabled=\"true\""))
        assertTrue(html.contains("disabled"))
    }

    @Test
    fun `searchable select renders required attribute correctly`() {
        val options = listOf(SearchableSelectOption("value1", "Label 1"))

        val component = SearchableSelect(
            id = "test-select",
            name = "test",
            options = options,
            required = true
        )

        val html = renderComponent(component)

        assertTrue(html.contains("required"))
    }

    @Test
    fun `searchable select handles disabled options`() {
        val options = listOf(
            SearchableSelectOption("value1", "Label 1", disabled = false),
            SearchableSelectOption("value2", "Label 2", disabled = true),
            SearchableSelectOption("value3", "Label 3", disabled = false)
        )

        val component = SearchableSelect(
            id = "test-select",
            name = "test",
            options = options
        )

        val html = renderComponent(component)

        // Should have one disabled option
        assertTrue(html.contains("data-disabled=\"true\""))
        assertTrue(html.contains("aria-disabled=\"true\""))
    }

    @Test
    fun `searchable select handles custom metadata`() {
        val options = listOf(
            SearchableSelectOption(
                value = "diamond",
                label = "Diamond",
                metadata = mapOf("category" to "gems", "rarity" to "rare")
            )
        )

        val component = SearchableSelect(
            id = "test-select",
            name = "test",
            options = options
        )

        val html = renderComponent(component)

        assertTrue(html.contains("data-category=\"gems\""))
        assertTrue(html.contains("data-rarity=\"rare\""))
    }

    @Test
    fun `searchable select applies size variants correctly`() {
        val options = listOf(SearchableSelectOption("value1", "Label 1"))

        val smallComponent = SearchableSelect(
            id = "test-select-sm",
            name = "test",
            options = options,
            size = SearchableSelectSize.SMALL
        )

        val smallHtml = renderComponent(smallComponent)

        assertTrue(smallHtml.contains("searchable-select--sm"))

        val largeComponent = SearchableSelect(
            id = "test-select-lg",
            name = "test",
            options = options,
            size = SearchableSelectSize.LARGE
        )

        val largeHtml = renderComponent(largeComponent)

        assertTrue(largeHtml.contains("searchable-select--lg"))
    }

    @Test
    fun `searchable select handles custom value serializer`() {
        val options = listOf(
            SearchableSelectOption(TestEnum.OPTION_A, "Option A"),
            SearchableSelectOption(TestEnum.OPTION_B, "Option B")
        )

        val component = SearchableSelect(
            id = "test-select",
            name = "test",
            options = options,
            selectedValue = TestEnum.OPTION_A,
            valueSerializer = { it.name }
        )

        val html = renderComponent(component)

        assertTrue(html.contains("value=\"OPTION_A\""))
        assertTrue(html.contains("data-value=\"OPTION_A\""))
        assertTrue(html.contains("data-value=\"OPTION_B\""))
    }

    @Test
    fun `searchable select renders configuration data attributes`() {
        val options = listOf(SearchableSelectOption("value1", "Label 1"))

        val component = SearchableSelect(
            id = "test-select",
            name = "test",
            options = options,
            minQueryLength = 3,
            maxDisplayedResults = 150
        )

        val html = renderComponent(component)

        assertTrue(html.contains("data-min-query-length=\"3\""))
        assertTrue(html.contains("data-max-displayed-results=\"150\""))
    }

    @Test
    fun `searchable select renders custom messages in data attributes`() {
        val options = listOf(SearchableSelectOption("value1", "Label 1"))

        val component = SearchableSelect(
            id = "test-select",
            name = "test",
            options = options,
            minQueryMessage = "Start typing to search items...",
            emptyMessage = "No items match your search",
            tooManyResultsMessage = "Showing {count} items. Please refine your search."
        )

        val html = renderComponent(component)

        assertTrue(html.contains("data-min-query-message=\"Start typing to search items...\""))
        assertTrue(html.contains("data-empty-message=\"No items match your search\""))
        assertTrue(html.contains("data-too-many-message=\"Showing {count} items. Please refine your search.\""))
    }

    @Test
    fun `searchable select initially hides all options for lazy rendering`() {
        val options = listOf(
            SearchableSelectOption("value1", "Label 1"),
            SearchableSelectOption("value2", "Label 2"),
            SearchableSelectOption("value3", "Label 3")
        )

        val component = SearchableSelect(
            id = "test-select",
            name = "test",
            options = options
        )

        val html = renderComponent(component)

        // All options should have data-hidden="true" for lazy rendering
        val hiddenCount = html.split("data-hidden=\"true\"").size - 1
        assertEquals(hiddenCount, options.size, "Expected ${options.size} hidden options, but found $hiddenCount")
    }

    @Test
    fun `searchable select renders min query message by default`() {
        val options = listOf(SearchableSelectOption("value1", "Label 1"))

        val component = SearchableSelect(
            id = "test-select",
            name = "test",
            options = options,
            minQueryMessage = "Type to search..."
        )

        val html = renderComponent(component)

        assertTrue(html.contains("searchable-select__message--min-query"))
        assertTrue(html.contains("Type to search..."))
    }
}
