package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.IdeaDraft
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.pipeline.idea.draft.DraftData
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTrigger
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
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
import kotlinx.html.ul
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

fun FlowContent.draftItemRequirementFields(draft: IdeaDraft) {
    val data = runCatching { json.decodeFromString(DraftData.serializer(), draft.data) }.getOrDefault(DraftData())
    val versionRange = data.versionRange ?: MinecraftVersionRange.Unbounded

    // --- Manual item search ---
    // Search, quantity and the action on one line: they are one gesture ("add 64 iron"), and
    // stacking them made a three-step ritual out of it.
    div("wizard-item-add item-add-line") {
        // Ids kept because addItemScript() still addresses these three fields directly; the class
        // hooks are what let the shared selectSearchedItem find them (MCO-417). The DOM is
        // unchanged from before that change — both classes on one element, label inside — so the
        // existing item-search CSS still applies.
        draftItemSearchCombo(
            scope = "requirements",
            versionRange = versionRange,
            inputId = "item-search",
            selectedIdId = "selected-item-id",
            selectedLabelId = "selected-item-label",
            extraClasses = "item-add-line__search",
            labelText = "Add Item",
        )

        div("item-add-line__qty") {
            label { htmlFor = "item-amount"; +"Quantity" }
            input(type = InputType.number, classes = "form-control") {
                id = "item-amount"
                min = "1"
                max = "2000000000"
                value = "1"
            }
        }
        button(classes = "btn btn--secondary item-add-line__action") {
            type = ButtonType.button
            attributes["onclick"] = addItemScript()
            +"Add"
        }
    }

    // --- Litematica upload ---
    div("wizard-litematica-upload") {
        p("form-help-text") { +"Or drop in a .litematic and take the whole material list from it:" }
        // Deliberately NOT a nested <form>. These fields now live inside the single-page create
        // form (MCO-310), and an inner <form> makes the HTML parser close the outer one early —
        // silently orphaning every field and button that follows, including submit. HTMX can post
        // multipart straight from the input instead.
        input(type = InputType.file, classes = "form-control") {
            name = "litematicFile"
            accept = ".litematic"
            // A design that spans dimensions is several files (MCO-414); their material lists
            // are summed into one list here.
            multiple = true
            hxPost("/ideas/create/litematic")
            hxTarget("#draft-item-list")
            hxSwap("beforeend")
            hxTrigger("change")
            attributes["hx-encoding"] = "multipart/form-data"
        }
        p("form-error") { id = "error-litematicFile" }
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

    // selectSearchedItem is no longer defined here — the productions section renders combos of its
    // own into this same <form>, and two definitions of one global mean the later render wins.
    // It now lives once in draftItemSearchScript(), emitted by the form (MCO-417).
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
