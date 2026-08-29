package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.IdeaDraft
import app.mcorg.domain.model.idea.IdeaModeKind
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.pipeline.idea.draft.DraftData
import app.mcorg.pipeline.idea.draft.DraftProductionMode
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTrigger
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.details
import kotlinx.html.summary
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.hiddenInput
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.ul
import kotlinx.html.unsafe
import kotlinx.serialization.json.Json

private val productionJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Stands in for the mode index inside `#production-mode-template`, replaced when a mode is added.
 *
 * Chosen to survive HTML attribute escaping and to be findable — it appears in element ids, field
 * names and the combo's scope, so a change here has to match [productionScript]'s substitution.
 */
private const val MODE_INDEX_TOKEN = "__MODE_INDEX__"

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
    val versionRange = data.versionRange ?: MinecraftVersionRange.Unbounded

    val isFarm = data.category == IdeaCategory.FARM
    val hasRates = modes.any { it.rates.isNotEmpty() }

    div("wizard-section") {
        id = "draft-productions"
        attributes["data-mode-count"] = modes.size.toString()

        label { +"Produces" }
        p("form-help") {
            +"What this design makes, per hour. Leave empty for anything that produces nothing — "
            +"a storage system, a base, a decorative build."
        }

        // Recommended, not required (MCO-412). Requiring it would undo MCO-310, whose whole point
        // was making capture fast enough that the bank fills up — and an author who has watched a
        // video but not built the farm honestly does not know the rate yet. A gate would either
        // block that capture or teach people to type a number they made up, and an invented rate
        // is worse than a missing one.
        //
        // So state the consequence instead of nagging. It is a real one and it is specific: farm
        // suggestions match demand against produced items (MCO-294), so a farm with nothing here
        // is invisible to exactly the moment it was worth capturing for.
        div("callout callout--info production-recommendation") {
            id = "production-recommendation"
            if (!isFarm || hasRates) classes = classes + "production-recommendation--hidden"
            span("callout__body") {
                +"Worth filling in for a farm: Seam suggests farms by what they produce, so one "
                +"with no output here will not come up when a world needs that item."
            }
        }

        div {
            id = "production-modes"
            modes.forEachIndexed { index, mode ->
                productionModeBlock(index.toString(), mode, showName = modes.size > 1, versionRange = versionRange)
            }
        }

        // The block a new mode is cloned from, rendered by the server so the search combo inside it
        // is the real thing — same hx-vals, same version range. Building it in JS instead would mean
        // maintaining the markup twice and interpolating server state into a string (MCO-417).
        // MODE_INDEX_TOKEN is substituted for the real index on insert.
        //
        // Raw <template> because kotlinx.html only offers `template` on PhrasingContent, and this
        // sits in flow content. The rendered fragment carries its own wrapping <div>, which is why
        // the script picks the block out by class rather than taking firstElementChild.
        // One template per kind, both server-rendered, because they differ by more than a flag: the
        // build-time block carries a whole materials upload whose hx-* attributes have to be the
        // real ones. Building either in JS would mean maintaining the markup twice.
        IdeaModeKind.entries.forEach { kind ->
            unsafe {
                raw(
                    "<template id=\"production-mode-template-${kind.name}\">" +
                        createHTML().div {
                            productionModeBlock(
                                index = MODE_INDEX_TOKEN,
                                mode = DraftProductionMode(kind = kind),
                                showName = true,
                                versionRange = versionRange,
                            )
                        } +
                        "</template>"
                )
            }
        }

        div("production-mode-actions") {
            button(classes = "btn btn--ghost btn--sm") {
                type = ButtonType.button
                id = "add-production-mode"
                attributes["onclick"] = "addProductionMode('RUNTIME')"
                +"This farm can be run another way"
            }
            // The second door, and the one MCO-463 exists for. Phrased as a question about the
            // design rather than about the schema — an author knows whether their farm has a
            // bigger version; nobody knows what a "build-time mode" is until they are told.
            button(classes = "btn btn--ghost btn--sm") {
                type = ButtonType.button
                id = "add-build-time-mode"
                attributes["onclick"] = "addProductionMode('BUILD_TIME')"
                +"…or built another way"
            }
        }
        p("form-help") {
            +"Built another way means it costs different materials — a 4-module farm against a "
            +"single one. Each gets its own .litematic, and you pick one when you import it."
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
private fun FlowContent.productionModeBlock(
    index: String,
    mode: DraftProductionMode,
    showName: Boolean,
    versionRange: MinecraftVersionRange,
) {
    val isBuildTime = mode.kind == IdeaModeKind.BUILD_TIME
    div("production-mode") {
        attributes["data-mode-index"] = index
        attributes["data-mode-kind"] = mode.kind.name

        // Which kind this is, carried explicitly rather than inferred from whether a material list
        // happens to be present. A build-time variant whose .litematic has not been dropped yet is
        // still build-time, and inferring would silently demote it on save.
        hiddenInput {
            name = "productionMode[$index][kind]"
            value = mode.kind.name
        }

        if (isBuildTime) {
            // Only ever shown on build-time blocks. Runtime is the unmarked default — labelling
            // both would put mode vocabulary in front of the single-mode author that MCO-204
            // removed the form's nesting to protect.
            span("badge badge--neutral production-mode__kind") { +"Built this way" }
        }

        if (showName) {
            input(type = InputType.text, classes = "form-control production-mode__name") {
                name = "productionMode[$index][name]"
                value = mode.name
                placeholder = if (isBuildTime) {
                    "How it is built — \"4 modules\", \"With storage\""
                } else {
                    "How it is run — \"Max speed\", \"Skeletons only\""
                }
            }
        } else {
            // Carries the existing name rather than blanking it. A mode can be named and then
            // become the only one — delete the rates from "Slowed" and "Max speed" is alone — and
            // writing "" here would rename it to Default on the next save, silently discarding a
            // name the author chose.
            hiddenInput {
                name = "productionMode[$index][name]"
                value = mode.name
            }
        }

        div("production-mode__add") {
            // A real catalog search, one per mode, scoped by the mode index (MCO-417). Before this
            // the field took a raw id and the script prepended "minecraft:" to it, so "Blue Ice"
            // became minecraft:Blue Ice — accepted here, matched nothing in MCO-294, and failed the
            // whole import of the idea into every world.
            draftItemSearchCombo(
                scope = "production-$index",
                versionRange = versionRange,
                placeholder = "Search items by name...",
            )
            input(type = InputType.number, classes = "form-control production-item-rate") {
                // Optional on purpose: knowing a farm makes bamboo is worth recording even when
                // nobody has timed it, and an invented rate would be worse than none.
                placeholder = "Per hour (optional)"
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
            mode.rates.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int?>> { it.value ?: -1 }.thenBy { it.key })
                .forEach { (itemId, rate) ->
                    li("item-req") {
                        // The add-a-rate script de-duplicates on this attribute. Without it a row
                        // that came back from a saved draft is invisible to that lookup, so re-adding
                        // the item appends a second hidden input with the same name and the *old*
                        // value wins on parse — the correction disappears with no feedback.
                        // Scoped per mode by the enclosing <ul>, since one item can appear in several.
                        attributes["data-item-id"] = itemId
                        span { +if (rate == null) "$itemId — rate unmeasured" else "$itemId × $rate/h" }
                        hiddenInput {
                            name = "productionRate[$index][$itemId]"
                            value = rate?.toString() ?: ""
                        }
                        button(classes = "btn btn--ghost btn--sm") {
                            type = ButtonType.button
                            attributes["onclick"] = "this.closest('li').remove()"
                            +"Remove"
                        }
                    }
                }
        }

        // A build-time variant costs different materials from its siblings, which is the whole
        // reason it is a separate mode (MCO-463). Its list comes from its own `.litematic` — the
        // 4-module farm is its own download — so the upload is per block rather than one batch at
        // submit: each request stays inside ReceiveSchematicStep's whole-upload file and byte
        // budget instead of multiplying it by the number of variants, and one unparseable file
        // costs that variant rather than the whole design.
        if (isBuildTime) {
            div("production-mode__materials") {
                p("form-help-text") { +"What building it this way costs — drop this variant's .litematic:" }
                input(type = InputType.file, classes = "form-control") {
                    // Named, and named the same as the base list's input. A control with no `name`
                    // is not submitted at all, so HTMX posted an empty multipart and the endpoint
                    // answered 422 — caught in a browser, invisible to a render test asserting the
                    // hx-post URL. Sharing the name is safe because these are separate requests:
                    // this input posts only itself, on change, and never rides the outer form.
                    name = "litematicFile"
                    accept = ".litematic"
                    // Several, for the same reason the base list takes several: a design that
                    // spans dimensions is more than one file (MCO-414), and every one of them is
                    // part of what *this* variant costs.
                    multiple = true
                    hxPost("/ideas/create/litematic?mode=$index")
                    hxTarget("#mode-materials-$index")
                    hxSwap("beforeend")
                    hxTrigger("change")
                    attributes["hx-encoding"] = "multipart/form-data"
                }

                // Collapsed by default, and it has to be: four variants of a large farm is four
                // three-hundred-row lists, and the comparison worth seeing — 400 cobblestone
                // against 1,600 — is in the summary rather than the rows.
                details("production-mode__materials-disclosure") {
                    summary {
                        span("production-mode__materials-count") {
                            id = "mode-materials-summary-$index"
                            +materialsSummary(mode.requirements)
                        }
                    }
                    ul("wizard-item-list production-mode__materials-list") {
                        id = "mode-materials-$index"
                        mode.requirements.entries
                            .sortedByDescending { it.value }
                            .forEach { (itemId, quantity) ->
                                li("item-req") {
                                    id = "item-req-$index-$itemId"
                                    attributes["data-item-id"] = itemId
                                    +"$itemId × $quantity"
                                    hiddenInput {
                                        name = "modeRequirements[$index][$itemId]"
                                        value = quantity.toString()
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
        }
    }
}

/**
 * The one line an author reads to tell two variants apart without expanding either.
 *
 * Deliberately leads with the total rather than the item count: "1,600 cobblestone" next to
 * "400 cobblestone" is the check that catches the likely mistake here, which is dropping the same
 * file into two variants when four downloads have near-identical names.
 */
private fun materialsSummary(requirements: Map<String, Int>): String {
    if (requirements.isEmpty()) return "No materials yet"
    val largest = requirements.maxByOrNull { it.value }!!
    val name = largest.key.substringAfter(':').replace('_', ' ')
    val rest = requirements.size - 1
    val total = "%,d".format(largest.value)
    return if (rest == 0) "$total $name" else "$total $name, and $rest more"
}

/**
 * Adding a rate row and adding a mode, client-side.
 *
 * The item comes from a real catalog search now (MCO-417), one combo per mode, so an id that is not
 * an item cannot be entered rather than being caught at publish. `ValidateIdeaProductionsStep`
 * still checks ids server-side — drafts saved before this could carry anything.
 */
private fun productionScript() = """
    function addProductionRate(btn) {
        var block = btn.closest('.production-mode');
        var index = block.dataset.modeIndex;
        var combo = block.querySelector('.item-search-combo');
        var rateInput = block.querySelector('.production-item-rate');
        var itemId = selectedItemIn(combo);
        var rawRate = rateInput.value.trim();
        var rate = rawRate === '' ? null : parseInt(rawRate, 10);
        // Nothing picked from the results yet — typing a name into the box is not a choice.
        if (!itemId) return;
        if (rate !== null && (isNaN(rate) || rate < 0)) return;

        var list = document.getElementById('production-rates-' + index);
        var existing = list.querySelector('[data-item-id="' + itemId + '"]');
        if (existing) existing.remove();

        var li = document.createElement('li');
        li.className = 'item-req';
        li.dataset.itemId = itemId;
        var label = rate === null ? itemId + ' — rate unmeasured' : itemId + ' × ' + rate + '/h';
        li.innerHTML = '<span>' + label + '</span>' +
            '<input type="hidden" name="productionRate[' + index + '][' + itemId + ']" value="' + (rate === null ? '' : rate) + '">' +
            '<button type="button" class="btn btn--ghost btn--sm" onclick="this.closest(\'li\').remove()">Remove</button>';
        list.appendChild(li);
        clearSelectedItem(combo);
        rateInput.value = '';
        refreshProductionRecommendation();
    }

    function refreshProductionRecommendation() {
        var note = document.getElementById('production-recommendation');
        if (!note) return;
        var farm = document.querySelector('.category-radio[value="FARM"]');
        var isFarm = farm && farm.checked;
        var hasRates = document.querySelectorAll('#production-modes input[name^="productionRate["]').length > 0;
        note.classList.toggle('production-recommendation--hidden', !isFarm || hasRates);
    }

    document.addEventListener('change', function (event) {
        if (event.target && event.target.classList.contains('category-radio')) {
            refreshProductionRecommendation();
        }
    });

    document.addEventListener('click', function (event) {
        // Removing the last rate should bring the note back.
        if (event.target && event.target.tagName === 'BUTTON') setTimeout(refreshProductionRecommendation, 0);
    });

    /**
     * Recomputes a build-time variant's one-line summary from the rows currently in its list.
     *
     * Runs after an upload swaps rows in and after a Remove, because the summary is the only part
     * of a collapsed list anyone reads — a stale one would say 400 cobblestone next to a list
     * holding 1,600, which is exactly the comparison it exists to support.
     */
    function refreshModeMaterials(index) {
        var list = document.getElementById('mode-materials-' + index);
        var summary = document.getElementById('mode-materials-summary-' + index);
        if (!list || !summary) return;

        var rows = list.querySelectorAll('input[type=hidden][name^="modeRequirements["]');
        if (rows.length === 0) { summary.textContent = 'No materials yet'; return; }

        var largestName = '';
        var largest = -1;
        rows.forEach(function (input) {
            var qty = parseInt(input.value, 10);
            if (isNaN(qty) || qty <= largest) return;
            largest = qty;
            var match = input.name.match(/\[([^\]]+)\]$/);
            largestName = match ? match[1].split(':').pop().replace(/_/g, ' ') : '';
        });
        if (largest < 0) { summary.textContent = 'No materials yet'; return; }

        var total = largest.toLocaleString('en-US');
        var rest = rows.length - 1;
        summary.textContent = rest === 0
            ? total + ' ' + largestName
            : total + ' ' + largestName + ', and ' + rest + ' more';
    }

    // An upload swaps rows into one variant's list; htmx tells us which.
    document.body.addEventListener('htmx:afterSwap', function (event) {
        var id = event.target && event.target.id;
        if (id && id.indexOf('mode-materials-') === 0) {
            refreshModeMaterials(id.substring('mode-materials-'.length));
        }
    });

    document.addEventListener('click', function (event) {
        if (!event.target || event.target.tagName !== 'BUTTON') return;
        var block = event.target.closest('.production-mode');
        if (!block) return;
        // After the row is actually gone, not before.
        setTimeout(function () { refreshModeMaterials(block.dataset.modeIndex); }, 0);
    });

    function addProductionMode(kind) {
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
                // Keep whatever the hidden input held — a mode that already has a name keeps it
                // when it grows a visible field.
                named.value = hidden.value;
                named.placeholder = 'How it is run — "Max speed", "Skeletons only"';
                hidden.replaceWith(named);
            }
        });

        // Cloned from the server-rendered template so the new mode's search combo is identical to
        // the existing ones — including the hx-vals carrying this draft's version range. One
        // template per kind: the build-time one brings its own materials upload.
        var template = document.getElementById('production-mode-template-' + (kind || 'RUNTIME'));
        if (!template) return;
        var markup = template.innerHTML.split('$MODE_INDEX_TOKEN').join(index);
        var holder = document.createElement('div');
        holder.innerHTML = markup;
        var block = holder.querySelector('.production-mode');
        if (!block) return;
        container.appendChild(block);

        // The combo's hx-get is inert until htmx is told about the new nodes.
        if (window.htmx) htmx.process(block);
    }
""".trimIndent()
