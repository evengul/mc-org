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
 * `/items/search` renders each option with a fixed `onclick="selectSearchedItem(this)"`, so every
 * consumer of that endpoint has to supply a global of that name. The draft form has two consumers
 * in one `<form>` — item requirements and, since MCO-412, one per production mode — and two globals
 * of the same name means the later definition silently wins. That collision is why productions
 * shipped with a raw text field instead of a search.
 *
 * The fix is one definition that works out *which* combo was used from the clicked option's
 * position, rather than from element ids it has to know in advance. Every combo carries the same
 * class hooks; ids remain only where other scripts already address a specific field by id.
 *
 * Other pages (`ProductionPanel`, `ResourceDetailPanel`, the project pages) keep their own
 * `selectSearchedItem`; they render one combo each, on pages this file has nothing to do with.
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
 * The single `selectSearchedItem` for the draft form.
 *
 * Resolves the fields to write from the clicked option's own container rather than by id, so it
 * serves any number of combos on the page. Rendered once, by the page shell — not by either field
 * group, since whichever rendered last would otherwise define the winner.
 */
fun draftItemSearchScript() = """
    function selectSearchedItem(el) {
        var results = el.closest('.item-search-results');
        var combo = el.closest('.item-search-combo');
        if (!combo) return;
        combo.querySelector('.item-search-selected-id').value = el.dataset.itemId;
        combo.querySelector('.item-search-selected-label').textContent = el.dataset.itemName;
        combo.querySelector('.item-search-input').value = el.dataset.itemName;
        if (results) results.innerHTML = '';
    }

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
