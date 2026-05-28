package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.IdeaDraft
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.pipeline.idea.draft.DraftData
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTrigger
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.FormEncType
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.hiddenInput
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.ul
import kotlinx.html.unsafe
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

fun FlowContent.draftItemRequirementFields(draft: IdeaDraft) {
    val data = runCatching { json.decodeFromString(DraftData.serializer(), draft.data) }.getOrDefault(DraftData())
    val versionRange = data.versionRange ?: MinecraftVersionRange.Unbounded

    val versionRangeType = when (versionRange) {
        is MinecraftVersionRange.Bounded -> "bounded"
        is MinecraftVersionRange.LowerBounded -> "lowerBounded"
        is MinecraftVersionRange.UpperBounded -> "upperBounded"
        else -> "unbounded"
    }
    val versionFrom = when (versionRange) {
        is MinecraftVersionRange.Bounded -> versionRange.from.toString()
        is MinecraftVersionRange.LowerBounded -> versionRange.from.toString()
        else -> ""
    }
    val versionTo = when (versionRange) {
        is MinecraftVersionRange.Bounded -> versionRange.to.toString()
        is MinecraftVersionRange.UpperBounded -> versionRange.to.toString()
        else -> ""
    }

    // --- Manual item search ---
    div("wizard-item-add") {
        div("item-search-combo") {
            label { htmlFor = "item-search"; +"Add Item" }
            div("item-search-field") {
                input(type = InputType.text, classes = "form-control") {
                    id = "item-search"
                    placeholder = "Search items by name..."
                    autoComplete = false
                    hxGet("/items/search")
                    hxTrigger("input changed delay:300ms")
                    hxTarget("#item-search-results")
                    hxSwap("innerHTML")
                    attributes["hx-vals"] = "js:{q: this.value, versionRangeType: '$versionRangeType', versionFrom: '$versionFrom', versionTo: '$versionTo'}"
                }
                div("item-search-results") {
                    id = "item-search-results"
                }
            }
            hiddenInput { id = "selected-item-id" }
            span("item-selected-label") {
                id = "selected-item-label"
            }
        }

        div("item-add-row") {
            div {
                label { htmlFor = "item-amount"; +"Quantity" }
                input(type = InputType.number, classes = "form-control") {
                    id = "item-amount"
                    min = "1"
                    max = "2000000000"
                    value = "1"
                }
            }
            button(classes = "btn btn--secondary btn--sm") {
                type = ButtonType.button
                attributes["onclick"] = addItemScript()
                +"Add Item"
            }
        }
    }

    // --- Litematica upload ---
    div("wizard-litematica-upload") {
        p("form-help-text") { +"Or import items from a .litematic schematic file:" }
        form {
            encType = FormEncType.multipartFormData
            hxPost("/ideas/create/litematic")
            hxTarget("#draft-item-list")
            hxSwap("beforeend")
            attributes["hx-encoding"] = "multipart/form-data"
            input(type = InputType.file, classes = "form-control") {
                name = "litematicFile"
                accept = ".litematic"
                attributes["onchange"] = "this.closest('form').requestSubmit()"
            }
            p("form-error") { id = "error-litematicFile" }
        }
    }

    // --- Item list ---
    ul("wizard-item-list") {
        id = "draft-item-list"
        data.itemRequirements?.entries
            ?.sortedByDescending { it.value }
            ?.forEach { (itemId, qty) ->
                li("item-req") {
                    id = "item-req-$itemId"
                    +"$itemId \u00d7 $qty"
                    hiddenInput {
                        name = "itemRequirements[$itemId]"
                        value = qty.toString()
                    }
                    button(classes = "btn btn--ghost btn--sm") {
                        type = ButtonType.button
                        attributes["onclick"] = "this.closest('li').remove()"
                        +"Remove"
                    }
                }
            }
    }

    script {
        unsafe {
            raw("""
                function selectSearchedItem(el) {
                    document.getElementById('selected-item-id').value = el.dataset.itemId;
                    document.getElementById('selected-item-label').textContent = el.dataset.itemName;
                    document.getElementById('item-search').value = el.dataset.itemName;
                    document.getElementById('item-search-results').innerHTML = '';
                }
            """.trimIndent())
        }
    }
}

private fun addItemScript() = """
    var itemId = document.getElementById('selected-item-id').value.trim();
    var itemLabel = document.getElementById('selected-item-label').textContent.trim() || itemId;
    var amount = parseInt(document.getElementById('item-amount').value) || 1;
    if (!itemId) return;
    var existing = document.getElementById('item-req-' + itemId);
    if (existing) existing.remove();
    var li = document.createElement('li');
    li.id = 'item-req-' + itemId;
    li.className = 'item-req';
    li.innerHTML = itemLabel + ' \u00d7 ' + amount +
        '<input type="hidden" name="itemRequirements[' + itemId + ']" value="' + amount + '">' +
        '<button type="button" class="btn btn--ghost btn--sm" onclick="this.closest(\'li\').remove()">Remove</button>';
    document.getElementById('draft-item-list').appendChild(li);
    document.getElementById('selected-item-id').value = '';
    document.getElementById('selected-item-label').textContent = '';
    document.getElementById('item-search').value = '';
    document.getElementById('item-amount').value = '1';
""".trimIndent()
