package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.pipeline.project.ImportWarning
import app.mcorg.pipeline.project.ImportWarningKind
import app.mcorg.pipeline.project.ImportWarnings
import app.mcorg.pipeline.project.ResolvedRegion
import app.mcorg.pipeline.project.ReviewedMaterial
import app.mcorg.pipeline.project.ReviewedMaterialsCodec
import app.mcorg.presentation.templated.dsl.appHeader
import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.pageShell
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.hiddenInput
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.main
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.summary
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr

/**
 * The import review screen (MCO-289 phase 1) — what the import *would* create, before it
 * creates it. "Not building the decorative shell" is a decision worth making while the
 * list is still a form, not after it is a hundred resource rows in a project.
 *
 * The whole material list is in this page's form — as a *single* field (MCO-315). It used to
 * be two fields per row, which walked straight into Ktor's 1000-parameter body cap and lost
 * everything past row ~466 of a 560-material schematic without saying so. One field per list
 * is flat in the number of rows; see [ReviewedMaterialsCodec] for the payload and for why
 * raising the cap was the wrong fix. The checkboxes are now pure UI — `import-review.js`
 * folds their state back into the field.
 *
 * Shared by both import doors (MCO-306): the schematic upload posts its parsed list here,
 * and an idea import arrives with the idea's requirements. [action] and [hiddenFields] are
 * the only difference — what the screen *does* is identical either way.
 *
 * **This screen does not swap materials, and should not grow the ability again (MCO-399).**
 * MCO-304 shipped batch substitution by family here and it was removed after one real use:
 * the `.litematic` is what you place blocks from once gathering is done, so rewriting the
 * gathering list without rewriting the file desyncs the two, and you find out at build time.
 * Litematica's own material-swap edits the schematic itself and keeps them in sync by
 * construction. That is the right door; this screen deliberately no longer offers a worse one.
 *
 * [regions] (MCO-398) split the list into collapsible sections, one per Litematica subregion,
 * each with a header checkbox that includes or excludes the whole thing. A 555-row flat table
 * is a screen people skip; sections are what make "I am not building the decorative shell" a
 * single click. Fewer than two regions renders with no group chrome at all — a lone region is
 * named after the schematic or left "Unnamed", so a header would wrap the list in noise.
 *
 * [warnings] (MCO-305) name the painful rows without standing in their way. Advisory only —
 * every warned row arrives checked. Since MCO-397 the strip above the list carries only
 * creative-only rows, the one kind worth interrupting for; everything else is a row chip.
 */
fun importReviewPage(
    user: TokenProfile,
    worldId: Int,
    worldName: String,
    projectName: String,
    requirements: Map<Item, Int>,
    action: String = "/worlds/$worldId/projects/from-schematic",
    hiddenFields: Map<String, String> = emptyMap(),
    placedCounts: Map<String, Int> = emptyMap(),
    regions: List<ResolvedRegion> = emptyList(),
    warnings: ImportWarnings = ImportWarnings(),
    unrecordableProductions: List<String> = emptyList(),
    offerAlreadyBuilt: Boolean = false,
): String = pageShell(
    pageTitle = "Seam — review import",
    user = user,
    stylesheets = listOf(
        "/static/styles/components/btn.css",
        "/static/styles/components/callout.css",
        "/static/styles/components/form.css",
        "/static/styles/pages/import-review.css",
    ),
    scripts = listOf("/static/scripts/import-review.js"),
) {
    appHeader(
        worldName = worldName,
        worldId = worldId,
        user = user,
        breadcrumbBlock = {
            link("Worlds", "/worlds")
                .link(worldName, "/worlds/$worldId/projects")
                .current("Review import")
        }
    )
    main {
        container {
            div("import-review__title") {
                h1("import-review__heading") { +"Review this import" }
                p("import-review__lead") {
                    +"Everything below will become a resource to gather. Uncheck what you are not building — you can add it back later from the project."
                }
            }

            form(classes = "import-review__form") {
                id = "import-review-form"
                method = kotlinx.html.FormMethod.post
                this.action = action

                hiddenFields.forEach { (field, fieldValue) ->
                    hiddenInput {
                        name = field
                        value = fieldValue
                    }
                }

                div("import-review__name-field") {
                    label {
                        htmlFor = "import-review-name"
                        +"Project name"
                    }
                    input(classes = "form-control") {
                        id = "import-review-name"
                        type = InputType.text
                        name = "name"
                        value = projectName
                        maxLength = "100"
                        required = true
                    }
                }

                productionNotice(unrecordableProductions)

                alreadyBuiltControl(offerAlreadyBuilt)

                materialsSection(requirements, emptySet(), placedCounts, regions, warnings)

                div("import-review__actions") {
                    button(classes = "btn btn--primary") {
                        type = ButtonType.submit
                        // Both labels render and CSS shows one, as with the region toggle: the
                        // button is the last thing read before committing, and "Create project"
                        // above a struck-through list would be the wrong promise.
                        span("import-review__submit--planned") { +"Create project" }
                        span("import-review__submit--built") { +"Record as built" }
                    }
                    a(classes = "btn btn--ghost") {
                        href = "/worlds/$worldId/projects"
                        +"Cancel"
                    }
                }
            }
        }
    }
}

/**
 * "This is already built in my world" (MCO-457) — the one control on this screen that changes
 * what the import *is* rather than which rows it carries.
 *
 * Ticked, the project is created operational with its productions and no gathering list, so it
 * supplies the world's other plans immediately (MCO-287's DONE-is-producing rule). That is the
 * same fact MCO-298's "record an existing farm" records for a farm with no idea behind it; this
 * is the door that also keeps the idea's rates and the link back to it.
 *
 * It sits above the material list rather than beside it because it makes the whole list moot,
 * and the list is long. MCO-306's argument for reviewing — "not that part" — has no meaning for
 * a build that already exists, so ticking this strikes the list through wholesale rather than
 * hiding it: what the design cost is still worth seeing, it is just not work any more.
 *
 * No JavaScript. The label, the list and the submit button all key off `:has(:checked)` in CSS,
 * so the screen reads correctly with scripting off — and the server ignores the material field
 * entirely when the box is ticked, so nothing depends on the styling having happened.
 */
private fun FlowContent.alreadyBuiltControl(offer: Boolean) {
    if (!offer) return

    div("import-review__already-built") {
        label("import-review__already-built-label") {
            htmlFor = ALREADY_BUILT_ID
            input(type = InputType.checkBox, classes = "import-review__already-built-box") {
                id = ALREADY_BUILT_ID
                name = "alreadyBuilt"
            }
            span("import-review__already-built-text") {
                span("import-review__already-built-title") { +"This is already built in my world" }
                span("import-review__already-built-hint") {
                    +"Records it as a finished project that produces from now on, with nothing to gather. "
                    +"Use this for a farm that was standing before you found the design."
                }
            }
        }
    }
}

private const val ALREADY_BUILT_ID = "import-review-already-built"

/**
 * One group of rows. [name] is null for a schematic that has nothing worth grouping by, which
 * is the common case and renders exactly as the screen did before MCO-398 — no group chrome.
 */
private data class MaterialGroup(val name: String?, val rows: List<Pair<Item, Int>>)

private fun FlowContent.materialsSection(
    requirements: Map<Item, Int>,
    excluded: Set<String>,
    placedCounts: Map<String, Int>,
    regions: List<ResolvedRegion>,
    warnings: ImportWarnings,
) {
    div("import-review__materials") {
        val groups = groupsFor(requirements, regions)

        // One order for the whole section: the hidden field and the tables must describe the
        // same rows in the same sequence. `import-review.js` rebuilds the field from the
        // checkboxes in DOM order, so the two only agree if this order is the render order.
        val allRows = groups.flatMap { it.rows }

        materialsField(allRows, excluded)
        warningStrip(warnings)
        materialsSummary(groups, allRows)
        sectionsLead(groups, regions.mapNotNull { it.sourceFile }.distinct().size)
        groups.forEachIndexed { index, group ->
            if (group.name == null) {
                materialsTable(index, group.rows, excluded, placedCounts, warnings)
            } else {
                regionGroup(index, group, excluded, placedCounts, warnings)
            }
        }

        // The server refuses an empty list too, but a form with no rows at all should not have
        // reached this page in the first place.
        if (allRows.isEmpty()) {
            p("form-error") { +"This schematic contains no recognisable materials." }
        }
    }
}

/**
 * Says what the sections are before the user meets them.
 *
 * Subregions are a Litematica concept, not a Seam one, and a stack of collapsed bars explains
 * neither what they are nor that they open. One line costs nothing and removes both questions;
 * without it the sections read as an unexplained grouping the screen invented.
 */
private fun FlowContent.sectionsLead(groups: List<MaterialGroup>, fileCount: Int) {
    if (groups.size < 2) return

    p("import-review__sections-lead") {
        // Naming the files matters when there are several: it is the confirmation that both
        // halves of a build that spans dimensions actually arrived (MCO-414). "3 sections" alone
        // would look identical whether the nether file was read or silently dropped.
        if (fileCount > 1) {
            +"These $fileCount files are being imported as one project, in ${groups.size} sections. "
        } else {
            +"This schematic is built from ${groups.size} sections. "
        }
        +"Untick one to leave it out of the import, or open it to choose materials one at a time."
    }
}

/**
 * Splits the list into the groups the screen offers.
 *
 * Grouping needs **more than one** region to be worth anything. Litematica names a lone region
 * after the schematic itself, or leaves it `"Unnamed"` — every real single-region fixture in
 * `mc-nbt/src/test/resources` does one or the other — so a header there would be noise wrapped
 * around the entire list. Those files render exactly as they did before MCO-398.
 */
private fun groupsFor(requirements: Map<Item, Int>, regions: List<ResolvedRegion>): List<MaterialGroup> {
    val byQuantity = compareByDescending<Pair<Item, Int>> { it.second }.thenBy { it.first.name }

    // The mapper already drops regions that resolve to nothing; this keeps the screen honest
    // if one ever arrives anyway, rather than rendering a section with an empty table.
    val populated = regions.filter { it.requirements.isNotEmpty() }

    if (populated.size < 2) {
        return listOf(MaterialGroup(null, requirements.toList().sortedWith(byQuantity)))
    }

    // Largest section first: on a build whose decorative shell is the thing you want to strike,
    // the section worth a decision is usually the big one.
    return populated
        .map { MaterialGroup(groupName(it, populated), it.requirements.sortedWith(byQuantity)) }
        .sortedByDescending { group -> group.rows.sumOf { it.second.toLong() } }
}

/**
 * What a section is called once several files can contribute them (MCO-414).
 *
 * The file is the part the user recognises — they named it, and for a build split by dimension
 * the name usually says which half it is. The region name inside is Litematica's, and for a
 * single-region file it merely repeats the schematic, so showing both would read as
 * "Sorter (nether) — Sorter (nether)".
 *
 * So: the file alone when it contributed one section, and `file — region` when it contributed
 * several and the region name actually adds something. Regions from a single-file import carry
 * no file at all and keep their own names, exactly as before.
 */
private fun groupName(region: ResolvedRegion, all: List<ResolvedRegion>): String {
    val fallback = region.name.ifBlank { "Unnamed section" }
    val file = region.sourceFile?.takeIf { it.isNotBlank() } ?: return fallback

    val siblings = all.count { it.sourceFile == region.sourceFile }
    if (siblings < 2) return file

    val regionName = region.name.trim()
    val addsNothing = regionName.isBlank() ||
        regionName.equals("Unnamed", ignoreCase = true) ||
        regionName.equals(file, ignoreCase = true)

    return if (addsNothing) file else "$file — $regionName"
}

private const val MATERIALS_FIELD_ID = "import-review-materials-field"

/**
 * The list itself — every row, its quantity and whether it is struck — in one field
 * (MCO-315). Rewritten by `import-review.js` whenever a checkbox moves.
 *
 * Struck rows are carried rather than dropped, so the payload always describes the list the
 * server rendered. That is what lets [ReviewedMaterialsCodec]'s declared row count tell a
 * user's exclusions apart from a transport truncating the list — the failure MCO-315 fixed.
 *
 * Without JavaScript this still submits the list exactly as the server rendered it; only the
 * exclusions would be missed, and the payload's declared row count means nothing can be lost
 * without the server saying so.
 */
private fun FlowContent.materialsField(rows: List<Pair<Item, Int>>, excluded: Set<String>) {
    hiddenInput {
        id = MATERIALS_FIELD_ID
        name = ReviewedMaterialsCodec.FIELD
        value = ReviewedMaterialsCodec.encode(
            rows.map { (item, amount) -> ReviewedMaterial(item.id, amount, item.id !in excluded) }
        )
    }
}

/**
 * The strip carries **creative-only rows and nothing else** (MCO-397).
 *
 * MCO-305 gave every warning kind a paragraph here, which put a `!` above the fold of every
 * import. Two of the three did not earn it. "Not really materials" is gone entirely (MCO-396
 * — those rows no longer exist). "Slow to gather" is now the row chip alone: a wither
 * skeleton skull *is* a grind, but the user chose the build knowing that, and interrupting
 * them with it reads as an error where there is none.
 *
 * What is left is the one kind that asks for a decision *now*: a creative-only row is one to
 * strike before it becomes a permanently blocked plan node. A `!` is proportionate for that.
 *
 * Long lists are truncated — the per-row chips carry the full detail.
 */
private fun FlowContent.warningStrip(warnings: ImportWarnings) {
    val blocked = warnings.of(ImportWarningKind.UNOBTAINABLE)
    if (blocked.isEmpty()) return

    div("callout import-review__warnings") {
        span("callout__icon") {
            attributes["aria-hidden"] = "true"
            +"!"
        }
        div("callout__body") {
            p("import-review__warning") {
                span("import-review__warning-heading") { +"${ImportWarningKind.UNOBTAINABLE.heading}: " }
                +namesOf(blocked)
                +". ${ImportWarningKind.UNOBTAINABLE.explanation}"
            }
        }
    }
}

/**
 * What the idea claims to produce that this world cannot record (MCO-456).
 *
 * Productions are not reviewable — they are the author's statement about their farm, not work
 * this world is agreeing to do (MCO-306) — so this is a notice and not a control. It exists
 * because the alternative is silence: the import succeeds, the farm supplies less than the idea
 * page said it would, and nothing anywhere connects the two.
 *
 * Info rather than warning. Nothing is wrong with the import and nothing is lost that this
 * version could have used; a full-weight warning here would outrank the creative-only strip,
 * which is about materials the user is about to go and gather.
 */
private fun FlowContent.productionNotice(unrecordable: List<String>) {
    if (unrecordable.isEmpty()) return

    div("callout callout--info import-review__warnings") {
        span("callout__icon") {
            attributes["aria-hidden"] = "true"
            +"i"
        }
        div("callout__body") {
            p("import-review__warning") {
                span("import-review__warning-heading") { +"Not recorded as production: " }
                +itemNamesFromIds(unrecordable)
                +". This idea says it produces these, but this world's Minecraft version has no "
                +"such item — the project is created without them."
            }
        }
    }
}

/**
 * A readable name for an id the catalog cannot name.
 *
 * These ids resolve to nothing in this world's version, so there is no stored display name to
 * look up — `minecraft:sculk_shrieker` becomes `Sculk Shrieker` here rather than being shown
 * raw. Same four-then-a-count cutoff as [namesOf].
 */
private fun itemNamesFromIds(ids: List<String>): String {
    val shown = ids.take(4).joinToString(", ") { id ->
        id.substringAfter(':').split('_').joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
    }
    val rest = ids.size - 4
    return if (rest > 0) "$shown and $rest more" else shown
}

/** Up to four names, then a count — a strip that lists thirty items is a wall, not a warning. */
private fun namesOf(flagged: List<ImportWarning>): String {
    val shown = flagged.take(4).joinToString(", ") { "${it.item.name} (${it.amount})" }
    val rest = flagged.size - 4
    return if (rest > 0) "$shown and $rest more" else shown
}

/**
 * The count above the list. With sections it reports **distinct materials**, not rows: an item
 * used in two sections is two rows but one thing to gather, and "580 items" for a 555-material
 * build would be a quiet lie.
 */
private fun FlowContent.materialsSummary(groups: List<MaterialGroup>, allRows: List<Pair<Item, Int>>) {
    val distinct = allRows.distinctBy { it.first.id }.size
    div("import-review__summary") {
        span("section-label") { +"Materials" }
        span("import-review__count") {
            +"$distinct ${if (distinct == 1) "item" else "items"}"
            if (groups.size > 1) +" in ${groups.size} sections"
        }
    }
}

/**
 * One section of a multi-region schematic (MCO-398), collapsed by default.
 *
 * Collapsed is the point: 555 rows in one flat table is a screen people skip, and the thing
 * that makes it skippable is that nothing tells you where one part of the build ends and the
 * next begins. The section header carries enough — name, material count, block total — to
 * decide "not building that" without expanding it.
 *
 * The checkbox in the header includes or excludes everything inside. It is deliberately
 * nameless like every other box on this screen: `import-review.js` folds it into the single
 * materials field, and it submits nothing of its own.
 *
 * The disclosure is a **worded** control rather than a bare chevron. A small glyph reads as
 * decoration next to a checkbox that is plainly interactive, so the section looks like
 * something you tick rather than something you open — and the material list inside stays
 * hidden from anyone who does not already know it is there. Both labels are rendered and CSS
 * shows one, so the control announces itself rather than relying on generated content.
 */
private fun FlowContent.regionGroup(
    index: Int,
    group: MaterialGroup,
    excluded: Set<String>,
    placedCounts: Map<String, Int>,
    warnings: ImportWarnings,
) {
    val blocks = group.rows.sumOf { it.second.toLong() }
    details("import-review__region") {
        summary("import-review__region-summary") {
            input(type = InputType.checkBox, classes = "import-review__region-include") {
                id = "region-include-$index"
                checked = true
                attributes["aria-label"] = "Include everything in ${group.name}"
            }
            span("import-review__region-name") { +(group.name ?: "") }
            span("import-review__region-meta") {
                +"${group.rows.size} ${if (group.rows.size == 1) "material" else "materials"}"
                +" · ${"%,d".format(blocks)} blocks"
            }
            span("btn btn--ghost btn--sm import-review__region-toggle") {
                span("import-review__region-toggle--closed") { +"Show materials ▾" }
                span("import-review__region-toggle--open") { +"Hide materials ▴" }
            }
        }
        materialsTable(index, group.rows, excluded, placedCounts, warnings)
    }
}

private fun FlowContent.materialsTable(
    groupIndex: Int,
    rows: List<Pair<Item, Int>>,
    excluded: Set<String>,
    placedCounts: Map<String, Int>,
    warnings: ImportWarnings,
) {
    div("import-review__table-wrap") {
        table(classes = "data-table import-review__table") {
            id = "import-review-table-$groupIndex"
            thead {
                tr {
                    th { +"Include" }
                    th { +"Item" }
                    th { +"Quantity" }
                }
            }
            tbody {
                rows.forEach { (item, amount) ->
                    // Scoped by group: the same item can legitimately appear in two sections,
                    // and a duplicate DOM id would point every label at the first one's box.
                    val rowId = "$groupIndex-" + item.id.replace(Regex("[^a-zA-Z0-9]"), "-")
                    tr("import-review__row") {
                        td {
                            attributes["data-label"] = "Include"
                            // Deliberately nameless: the box submits nothing of its own. It
                            // describes one row of the single materials field, which
                            // `import-review.js` rebuilds from these boxes (MCO-315). Two
                            // fields per row is what overflowed the body's parameter cap.
                            input(type = InputType.checkBox, classes = "import-review__include") {
                                id = "include-$rowId"
                                checked = item.id !in excluded
                                attributes["data-item-id"] = item.id
                                attributes["data-amount"] = amount.toString()
                                attributes["aria-label"] = "Include ${item.name}"
                            }
                        }
                        td {
                            attributes["data-label"] = "Item"
                            label("import-review__item-name") {
                                htmlFor = "include-$rowId"
                                +item.name
                            }
                            warnings.forItem(item.id)?.let { warning ->
                                span("import-review__flag") {
                                    attributes["title"] = warning.kind.explanation
                                    +warning.kind.chip
                                }
                            }
                            // One bucket, and the cell count it stands for (MCO-396). The
                            // amount column is what you gather; this is what the schematic
                            // holds, so the row still reconciles against Litematica's list.
                            placedCounts[item.id]?.let { cells ->
                                span("import-review__placed") {
                                    attributes["title"] =
                                        "The schematic places $cells of these. A bucket is reusable, so you only need to carry one."
                                    +"placed ${"%,d".format(cells)}×"
                                }
                            }
                        }
                        td {
                            attributes["data-label"] = "Quantity"
                            +amount.toString()
                        }
                    }
                }
            }
        }
    }
}
