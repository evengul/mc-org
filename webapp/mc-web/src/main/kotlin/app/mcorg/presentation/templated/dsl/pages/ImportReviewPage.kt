package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.pipeline.project.ImportWarning
import app.mcorg.pipeline.project.ImportWarningKind
import app.mcorg.pipeline.project.ImportWarnings
import app.mcorg.pipeline.project.ImportWizardStep
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
 *
 * [wizard] (MCO-459) makes this one step of a batch started from a plan's suggestion list.
 * The screen itself is unchanged — the review is the point, and MCO-306 put it here on
 * purpose — so the wizard adds only a position line, a Skip, a way out, and a submit that
 * promises the next step rather than the end. Null for every other door in, which is what
 * keeps a single import exactly what it was.
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
    containerCounts: Map<String, Int> = emptyMap(),
    warnings: ImportWarnings = ImportWarnings(),
    unrecordableProductions: List<String> = emptyList(),
    offerAlreadyBuilt: Boolean = false,
    wizard: ImportWizardStep? = null,
    /**
     * The ways this design can be built, when there is more than one (MCO-463). Empty for every
     * idea with a single material list, and for the schematic door, which has no modes at all.
     */
    buildTimeModes: List<String> = emptyList(),
    chosenBuildTimeMode: String? = null,
    /** The review URL for another variant — see `reviewHrefFor`. */
    reviewHref: (String) -> String = { "" },
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
                .link(worldName, "/worlds/$worldId/roadmap")
                .current("Review import")
        }
    )
    main {
        container {
            div("import-review__title") {
                wizardProgress(wizard)
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

                buildTimeChoice(buildTimeModes, chosenBuildTimeMode, reviewHref)

                productionNotice(unrecordableProductions)

                alreadyBuiltControl(offerAlreadyBuilt)

                materialsSection(requirements, emptySet(), placedCounts, regions, containerCounts, warnings)

                div("import-review__actions") {
                    button(classes = "btn btn--primary") {
                        type = ButtonType.submit
                        // Both labels render and CSS shows one, as with the region toggle: the
                        // button is the last thing read before committing, and "Create project"
                        // above a struck-through list would be the wrong promise. Mid-batch
                        // both gain "& next", for the same reason — the button should say where
                        // it puts you (MCO-459).
                        val next = if (wizard != null && !wizard.isLast) " & next" else ""
                        span("import-review__submit--planned") { +"Create project$next" }
                        span("import-review__submit--built") { +"Record as built$next" }
                    }
                    if (wizard != null) {
                        // Skip is a link, not a form control: it must not submit. Passing on a
                        // design is a real answer — the batch was selected against a plan, and
                        // reading the list is when you find out one of them is wrong.
                        wizard.nextHref?.let { href ->
                            a(classes = "btn btn--ghost") {
                                this.href = href
                                +"Skip this one"
                            }
                        }
                        a(classes = "btn btn--ghost") {
                            href = wizard.returnHref
                            +"Done, back to plan"
                        }
                    } else {
                        a(classes = "btn btn--ghost") {
                            href = "/worlds/$worldId/projects"
                            +"Cancel"
                        }
                    }
                }
            }
        }
    }
}

/**
 * "Review 2 of 3", with a dot per design (MCO-459).
 *
 * The count is the whole point of the component: the complaint that opened MCO-459 was not
 * that reviewing is slow, it was not knowing how much of it was left or how to get back. A
 * bare review screen answers neither.
 */
private fun FlowContent.wizardProgress(wizard: ImportWizardStep?) {
    if (wizard == null) return

    div("import-review__wizard") {
        span("import-review__wizard-count") { +"Review ${wizard.position} of ${wizard.total}" }
        div("import-review__wizard-dots") {
            repeat(wizard.total) { index ->
                val state = when {
                    index + 1 < wizard.position -> " import-review__wizard-dot--done"
                    index + 1 == wizard.position -> " import-review__wizard-dot--current"
                    else -> ""
                }
                span("import-review__wizard-dot$state") {}
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
    containerCounts: Map<String, Int>,
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
        stockedLead(allRows, containerCounts)
        groups.forEachIndexed { index, group ->
            if (group.name == null) {
                materialsTable(index, group.rows, excluded, placedCounts, containerCounts, warnings)
            } else {
                regionGroup(index, group, excluded, placedCounts, containerCounts, warnings)
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
 * Says, once and above the rows, how much of this list is stock rather than structure.
 *
 * The per-row chip is the detail; this is the part that is actually readable. The reported case
 * was a 519,844-unit import whose largest row — 362,706 carved pumpkins, 70% of the total — was
 * what that wither farm consumes, and a chip on one row of hundreds is not how anyone finds
 * that out. The claim worth making up front is *most of this is not the building*.
 *
 * Deliberately not phrased as a problem. Stocked containers are normal and usually intended —
 * the same fixture set has a shulker loader that is 96% redstone, which is simply what a shulker
 * loader is for. The line reports a proportion and stops; the reader decides whether they want
 * that much of it.
 *
 * A line, not the `!` strip. MCO-397 cut that strip back to creative-only rows — the one kind
 * that asks for a decision before the import lands. Nothing here is wrong, so a warning icon
 * would repeat exactly the mistake MCO-397 undid.
 *
 * Silent below a fifth of the list. A sorter's filter items are container contents too, and
 * every redstone build has some; announcing a 2% share would turn a real distinction into
 * chrome that appears on every import and is read on none.
 */
private fun FlowContent.stockedLead(allRows: List<Pair<Item, Int>>, containerCounts: Map<String, Int>) {
    if (containerCounts.isEmpty()) return

    val total = allRows.sumOf { it.second.toLong() }
    if (total <= 0) return

    // Against the rows actually on screen, so the share and the list agree. A container count
    // for a row that resolution dropped is not part of what the user is being shown.
    val shown = allRows.map { it.first.id }.toSet()
    val stocked = containerCounts.filterKeys { it in shown }.values.sumOf { it.toLong() }
    if (stocked <= 0) return

    val percent = (stocked * 100 / total).toInt()
    if (percent < 20) return

    p("import-review__stocked-lead") {
        // "units", not "items" — [materialsSummary] renders "13 items" a line above this,
        // meaning distinct materials. Using the same word for a quantity sum would put two
        // different counts of "items" an inch apart on the same screen.
        +"$percent% of this list (${"%,d".format(stocked)} units) is what the build is stocked "
        +"with — filter items, fuel, whatever it consumes — rather than blocks it places. That is "
        +"normal for a farm or a sorter, but the amount is the original builder's, so it is worth "
        +"a look before you gather it."
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
/**
 * Which way this design is being built (MCO-463), when its author recorded more than one.
 *
 * ## Why it is on this screen at all
 *
 * A build-time variant changes *what the project costs*: the 4-module cobblestone farm needs about
 * four times the materials of the single-module one, and they are different `.litematic` files.
 * That is the same class of decision as unchecking the decorative shell, so it belongs to the
 * screen whose whole job is showing what the import will cost — made deliberately, not defaulted
 * silently in a pipeline the user never sees.
 *
 * ## Links, not radios
 *
 * Choosing re-runs the review GET rather than toggling anything client-side. Everything on this
 * page is derived from the chosen variant — the material list, MCO-305's warnings, the totals, the
 * creative-only strip — so re-deriving all of it server-side is the only way they cannot disagree.
 * A radio that swapped the table alone would leave the warnings describing a different build,
 * which is exactly the two-answers-to-one-question shape that MCO-458/460/461 were all instances
 * of. The chosen name also rides the form as a hidden field, so the POST imports what was on
 * screen.
 *
 * Renders nothing at all below two variants, which is every idea that has no build-time axis.
 */
private fun FlowContent.buildTimeChoice(
    available: List<String>,
    chosen: String?,
    reviewHref: (String) -> String,
) {
    if (available.size < 2) return

    div("import-review__build-time") {
        p("import-review__build-time-label") { +"This design can be built more than one way" }
        p("import-review__build-time-hint") {
            +"Each way costs different materials. The list below is for the one selected."
        }
        div("import-review__build-time-options") {
            available.forEach { mode ->
                val isChosen = mode == chosen
                if (isChosen) {
                    // The current one is not a link: it goes nowhere, and making it clickable
                    // invites a reload that changes nothing.
                    span("import-review__build-time-option import-review__build-time-option--chosen") {
                        +mode
                    }
                } else {
                    a(classes = "import-review__build-time-option") {
                        href = reviewHref(mode)
                        +mode
                    }
                }
            }
        }
    }
}

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
    containerCounts: Map<String, Int>,
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
        materialsTable(index, group.rows, excluded, placedCounts, containerCounts, warnings)
    }
}

/**
 * Says how much of a row is stock the build carries rather than blocks it places (MCO-322).
 *
 * Litematica saves chest, hopper, dispenser and dropper contents alongside the blocks, and the
 * material list has always merged the two. What is in those containers is normally deliberate —
 * the filter items a sorter needs, the redstone a shulker loader loads, the carved pumpkins that
 * keep wither skeletons from despawning. This does not mark a mistake, and must not read as
 * though it does.
 *
 * It is worth marking because the two are different kinds of ask. Placed blocks are structure
 * and their count follows from its shape; stock is consumable, occupies no volume, and its
 * quantity is whatever scale the original builder worked at — a fine thing to want less of. A
 * real perimeter farm's list was 70% carved pumpkins and a shulker loader's 96% redstone, both
 * entirely correct and both easy to mistake for structure at a glance.
 *
 * **Marked, never excluded.** A chip in the same register as MCO-396's `placed N×`: a fact about
 * where a number came from, sitting next to the number, with no control attached and nothing
 * unchecked on its behalf.
 *
 * Two shapes, because "all of it" and "some of it" are different facts. A row that is entirely
 * stock says so plainly; a partly-stocked row gives the count, since "12 in containers" against
 * a quantity of 40 is what tells you the other 28 are structure.
 */
private fun FlowContent.containerMarker(item: Item, amount: Int, fromContainers: Int?) {
    if (fromContainers == null || fromContainers <= 0) return

    val all = fromContainers >= amount
    span("import-review__stocked") {
        attributes["title"] = if (all) {
            "Every ${item.name} here is container contents — what the build is stocked with " +
                "rather than a block it places. Filter items, fuel and consumables normally " +
                "arrive this way."
        } else {
            "${"%,d".format(fromContainers)} of these were in containers when the schematic was " +
                "saved; the rest are placed blocks."
        }
        +if (all) "in containers" else "${"%,d".format(fromContainers)} in containers"
    }
}

private fun FlowContent.materialsTable(
    groupIndex: Int,
    rows: List<Pair<Item, Int>>,
    excluded: Set<String>,
    placedCounts: Map<String, Int>,
    containerCounts: Map<String, Int>,
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
                            containerMarker(item, amount, containerCounts[item.id])
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
