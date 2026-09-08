package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.div
import kotlinx.html.hiddenInput
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.span

/**
 * The item-search combo, scoped to its own container so a page can carry several (MCO-417).
 *
 * `/items/search` renders each option with a fixed `onclick="selectSearchedItem(this)"`. The
 * draft form has two consumers in one `<form>` — item requirements and, since MCO-412, one per
 * production mode — which is what forced the handler to resolve the fields from the clicked
 * option's own combo rather than from ids it knows in advance: two id-addressed globals of one
 * name, and the later definition silently wins. That collision is why productions shipped with a
 * raw text field instead of a search.
 *
 * Since MCO-547 that container-scoped handler is the only one, in `scripts/item-search.js`, and
 * every combo in the app renders these class hooks. Ids remain only where other scripts address
 * a specific field by id.
 */
fun FlowContent.draftItemSearchCombo(
    scope: String,
    versionRange: MinecraftVersionRange,
    placeholder: String = "Search items by name...",
    inputId: String? = null,
    selectedIdId: String? = null,
    selectedLabelId: String? = null,
    /** Extra classes on the combo root, so a caller can size it without adding a wrapper. */
    extraClasses: String = "",
    /** Rendered as the combo's first child, where `.item-search-combo`'s column gap applies to it. */
    labelText: String? = null,
) {
    div(("item-search-combo $extraClasses").trim()) {
        if (labelText != null) {
            label {
                inputId?.let { htmlFor = it }
                +labelText
            }
        }

        div("item-search-field") {
            input(type = InputType.text, classes = "form-control item-search-input") {
                inputId?.let { id = it }
                this.placeholder = placeholder
                autoComplete = "off"
                attributes["hx-get"] = "/items/search"
                attributes["hx-trigger"] = "input changed delay:300ms"
                attributes["hx-target"] = "#item-search-results-$scope"
                attributes["hx-swap"] = "innerHTML"
                attributes["hx-vals"] = versionRange.toSearchVals()
            }
            div("item-search-results") {
                id = "item-search-results-$scope"
            }
        }

        hiddenInput(classes = "item-search-selected-id") {
            selectedIdId?.let { id = it }
        }
        span("item-selected-label item-search-selected-label") {
            selectedLabelId?.let { id = it }
        }
    }
}

/**
 * `hx-vals` carrying the draft's version range, so results match the versions the idea claims.
 *
 * Mirrors what `handleSearchItems` feeds to `ValidateIdeaMinecraftVersionStep`.
 */
fun MinecraftVersionRange.toSearchVals(): String {
    val type = when (this) {
        is MinecraftVersionRange.Bounded -> "bounded"
        is MinecraftVersionRange.LowerBounded -> "lowerBounded"
        is MinecraftVersionRange.UpperBounded -> "upperBounded"
        else -> "unbounded"
    }
    val from = when (this) {
        is MinecraftVersionRange.Bounded -> from.toString()
        is MinecraftVersionRange.LowerBounded -> from.toString()
        else -> ""
    }
    val to = when (this) {
        is MinecraftVersionRange.Bounded -> to.toString()
        is MinecraftVersionRange.UpperBounded -> to.toString()
        else -> ""
    }
    return "js:{q: this.value, versionRangeType: '$type', versionFrom: '$from', versionTo: '$to'}"
}

/**
 * The draft form's combo helpers. Rendered once, by the page shell — not by either field group.
 * `selectSearchedItem` itself is `scripts/item-search.js`'s, shared with every other host.
 */
fun draftItemSearchScript() = """
    /** The id the combo currently holds, or '' — the only way anything should read a picked item. */
    function selectedItemIn(combo) {
        var field = combo && combo.querySelector('.item-search-selected-id');
        return field ? field.value.trim() : '';
    }

    function clearSelectedItem(combo) {
        if (!combo) return;
        combo.querySelector('.item-search-selected-id').value = '';
        combo.querySelector('.item-search-selected-label').textContent = '';
        combo.querySelector('.item-search-input').value = '';
    }
""".trimIndent()
