package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.IdeaDraft
import app.mcorg.pipeline.idea.draft.DraftData
import app.mcorg.pipeline.idea.draft.DraftProductionMode
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.div
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

private val productionJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * What this idea produces (MCO-412).
 *
 * ## Why modes are almost invisible here
 *
 * Almost every farm has one mode with one set of items, and this exact field carried a
 * Mode → (Item → Rate) nesting once before: MCO-204 tore it out as "the single most tedious thing
 * in the form", and it "never got filled". The model needs modes back — an ice farm runs full
 * speed or slowed, a nether fortress farm has six ways to run — but the form must not charge the
 * common case for the rare one.
 *
 * So the first mode has no name field and is never called a mode. You add items and rates. Only
 * when you press "This farm can be run another way" does a second block appear, and only then do
 * names show up at all — including on the first block, which has to be nameable once there is
 * something to distinguish it from.
 */
fun FlowContent.draftProductionFields(draft: IdeaDraft) {
    val data = runCatching { productionJson.decodeFromString(DraftData.serializer(), draft.data) }
        .getOrDefault(DraftData())
    val modes = data.productionModes.orEmpty().ifEmpty { listOf(DraftProductionMode()) }

    div("wizard-section") {
        id = "draft-productions"
        attributes["data-mode-count"] = modes.size.toString()

        label { +"Produces" }
        p("form-help") {
            +"What this design makes, per hour. Leave empty for anything that produces nothing — "
            +"a storage system, a base, a decorative build."
        }

        div {
            id = "production-modes"
            modes.forEachIndexed { index, mode ->
                productionModeBlock(index, mode, showName = modes.size > 1)
            }
        }

        button(classes = "btn btn--ghost btn--sm") {
            type = ButtonType.button
            id = "add-production-mode"
            attributes["onclick"] = "addProductionMode()"
            +"This farm can be run another way"
        }

        script {
            unsafe { raw(productionScript()) }
        }
    }
}

/**
 * One mode: an optional name and its item/rate rows.
 *
 * [showName] is false while there is only one mode — the name is stored as blank and becomes
 * "Default" on save, because an author who was never asked about modes has not chosen one.
 */
private fun FlowContent.productionModeBlock(index: Int, mode: DraftProductionMode, showName: Boolean) {
    div("production-mode") {
        attributes["data-mode-index"] = index.toString()

        if (showName) {
            input(type = InputType.text, classes = "form-control production-mode__name") {
                name = "productionMode[$index][name]"
                value = mode.name
                placeholder = "How it is run — \"Max speed\", \"Skeletons only\""
            }
        } else {
            hiddenInput {
                name = "productionMode[$index][name]"
                value = ""
            }
        }

        div("production-mode__add") {
            input(type = InputType.text, classes = "form-control production-item-search") {
                placeholder = "Item"
                attributes["data-mode-index"] = index.toString()
            }
            input(type = InputType.number, classes = "form-control production-item-rate") {
                placeholder = "Per hour"
                min = "0"
                attributes["data-mode-index"] = index.toString()
            }
            button(classes = "btn btn--secondary btn--sm") {
                type = ButtonType.button
                attributes["onclick"] = "addProductionRate(this)"
                +"Add"
            }
        }

        ul("wizard-item-list production-mode__rates") {
            id = "production-rates-$index"
            mode.rates.entries.sortedByDescending { it.value }.forEach { (itemId, rate) ->
                li("item-req") {
                    span { +"$itemId × $rate/h" }
                    hiddenInput {
                        name = "productionRate[$index][$itemId]"
                        value = rate.toString()
                    }
                    button(classes = "btn btn--ghost btn--sm") {
                        type = ButtonType.button
                        attributes["onclick"] = "this.closest('li').remove()"
                        +"Remove"
                    }
                }
            }
        }
    }
}

/**
 * Adding a rate row and adding a mode, client-side.
 *
 * The item field takes a raw id rather than reusing the requirement search: that search posts to a
 * fragment endpoint and owns a single set of element ids, so it cannot be repeated per mode
 * without reworking it. Deliberate V1 limit — noted on the issue, and the ids are validated on
 * import against the world's catalog either way.
 */
private fun productionScript() = """
    function addProductionRate(btn) {
        var block = btn.closest('.production-mode');
        var index = block.dataset.modeIndex;
        var itemInput = block.querySelector('.production-item-search');
        var rateInput = block.querySelector('.production-item-rate');
        var itemId = itemInput.value.trim();
        var rate = parseInt(rateInput.value, 10);
        if (!itemId || isNaN(rate) || rate < 0) return;
        if (itemId.indexOf(':') === -1) itemId = 'minecraft:' + itemId;

        var list = document.getElementById('production-rates-' + index);
        var existing = list.querySelector('[data-item-id="' + itemId + '"]');
        if (existing) existing.remove();

        var li = document.createElement('li');
        li.className = 'item-req';
        li.dataset.itemId = itemId;
        li.innerHTML = '<span>' + itemId + ' × ' + rate + '/h</span>' +
            '<input type="hidden" name="productionRate[' + index + '][' + itemId + ']" value="' + rate + '">' +
            '<button type="button" class="btn btn--ghost btn--sm" onclick="this.closest(\'li\').remove()">Remove</button>';
        list.appendChild(li);
        itemInput.value = '';
        rateInput.value = '';
    }

    function addProductionMode() {
        var container = document.getElementById('production-modes');
        var section = document.getElementById('draft-productions');
        var index = parseInt(section.dataset.modeCount, 10);
        section.dataset.modeCount = index + 1;

        // The first mode only grows a name field once there is a second one to tell it apart from.
        container.querySelectorAll('.production-mode').forEach(function (block) {
            var hidden = block.querySelector('input[type=hidden][name$="][name]"]');
            if (hidden) {
                var named = document.createElement('input');
                named.type = 'text';
                named.className = 'form-control production-mode__name';
                named.name = hidden.name;
                named.value = '';
                named.placeholder = 'How it is run — "Max speed", "Skeletons only"';
                hidden.replaceWith(named);
            }
        });

        var block = document.createElement('div');
        block.className = 'production-mode';
        block.dataset.modeIndex = index;
        block.innerHTML =
            '<input type="text" class="form-control production-mode__name" name="productionMode[' + index + '][name]" placeholder="How it is run — &quot;Max speed&quot;, &quot;Skeletons only&quot;">' +
            '<div class="production-mode__add">' +
            '<input type="text" class="form-control production-item-search" placeholder="Item" data-mode-index="' + index + '">' +
            '<input type="number" class="form-control production-item-rate" placeholder="Per hour" min="0" data-mode-index="' + index + '">' +
            '<button type="button" class="btn btn--secondary btn--sm" onclick="addProductionRate(this)">Add</button>' +
            '</div>' +
            '<ul class="wizard-item-list production-mode__rates" id="production-rates-' + index + '"></ul>';
        container.appendChild(block);
    }
""".trimIndent()
