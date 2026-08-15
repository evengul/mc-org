package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.pipeline.project.ImportWarning
import app.mcorg.pipeline.project.ImportWarningKind
import app.mcorg.pipeline.project.ImportWarnings
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
    warnings: ImportWarnings = ImportWarnings(),
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

                materialsSection(requirements, emptySet(), placedCounts, warnings)

                div("import-review__actions") {
                    button(classes = "btn btn--primary") {
                        type = ButtonType.submit
                        +"Create project"
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

private fun FlowContent.materialsSection(
    requirements: Map<Item, Int>,
    excluded: Set<String>,
    placedCounts: Map<String, Int>,
    warnings: ImportWarnings,
) {
    div("import-review__materials") {
        // One order for the whole section: the hidden field and the table must describe the
        // same list in the same sequence, since `import-review.js` folds the table's checkbox
        // state back into the field by position-independent id but the two must agree on which
        // rows exist at all.
        val rows = requirements.entries.sortedWith(
            compareByDescending<Map.Entry<Item, Int>> { it.value }.thenBy { it.key.name }
        )

        materialsField(rows, excluded)
        warningStrip(warnings)
        materialsTable(rows, excluded, placedCounts, warnings)
    }
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
private fun FlowContent.materialsField(rows: List<Map.Entry<Item, Int>>, excluded: Set<String>) {
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

/** Up to four names, then a count — a strip that lists thirty items is a wall, not a warning. */
private fun namesOf(flagged: List<ImportWarning>): String {
    val shown = flagged.take(4).joinToString(", ") { "${it.item.name} (${it.amount})" }
    val rest = flagged.size - 4
    return if (rest > 0) "$shown and $rest more" else shown
}

private fun FlowContent.materialsTable(
    rows: List<Map.Entry<Item, Int>>,
    excluded: Set<String>,
    placedCounts: Map<String, Int>,
    warnings: ImportWarnings,
) {
    div("import-review__summary") {
        span("section-label") { +"Materials" }
        span("import-review__count") {
            +"${rows.size} ${if (rows.size == 1) "item" else "items"}"
        }
    }

    div("import-review__table-wrap") {
        table(classes = "data-table import-review__table") {
            id = "import-review-table"
            thead {
                tr {
                    th { +"Include" }
                    th { +"Item" }
                    th { +"Quantity" }
                }
            }
            tbody {
                rows.forEach { (item, amount) ->
                    val rowId = item.id.replace(Regex("[^a-zA-Z0-9]"), "-")
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

    // The server refuses an empty list too, but a form with no rows at all should not have
    // reached this page in the first place.
    if (rows.isEmpty()) {
        p("form-error") { +"This schematic contains no recognisable materials." }
    }
}
