package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.pipeline.project.ImportWarning
import app.mcorg.pipeline.project.ImportWarningKind
import app.mcorg.pipeline.project.ImportWarnings
import app.mcorg.pipeline.project.ReviewedMaterial
import app.mcorg.pipeline.project.ReviewedMaterialsCodec
import app.mcorg.pipeline.resources.SubstitutionFamily
import app.mcorg.presentation.hxInclude
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.templated.dsl.appHeader
import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.pageShell
import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.hiddenInput
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.main
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.stream.createHTML
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
 * Batch substitution (MCO-304) rewrites the list in place: the swap controls post the
 * current form back and swap [importReviewMaterials] for a rewritten copy, so a swap is
 * just another edit to an unsaved form.
 *
 * [warnings] (MCO-305) name the painful rows without standing in their way: a strip above
 * the list and a chip on the row itself. Advisory only — every warned row arrives checked.
 * They live *inside* the swappable section, because a swap changes which rows are painful.
 */
fun importReviewPage(
    user: TokenProfile,
    worldId: Int,
    worldName: String,
    projectName: String,
    requirements: Map<Item, Int>,
    action: String = "/worlds/$worldId/projects/from-schematic",
    hiddenFields: Map<String, String> = emptyMap(),
    families: List<SubstitutionFamily> = emptyList(),
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

                materialsSection(
                    worldId,
                    requirements,
                    excluded = emptySet(),
                    families = families,
                    warnings = warnings,
                )

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

/**
 * The swappable part of the screen, rendered standalone so a substitution can replace it
 * (MCO-304). Everything a swap can change lives in here — the controls, the row set, the
 * quantities and the warnings (MCO-305), since swapping oak for crimson changes which rows
 * are painful. The project name and the hidden carry-through fields sit outside and survive
 * untouched.
 */
fun importReviewMaterials(
    worldId: Int,
    requirements: Map<Item, Int>,
    excluded: Set<String>,
    families: List<SubstitutionFamily>,
    warnings: ImportWarnings,
): String = createHTML().div {
    materialsSectionBody(worldId, requirements, excluded, families, warnings)
}

private fun FlowContent.materialsSection(
    worldId: Int,
    requirements: Map<Item, Int>,
    excluded: Set<String>,
    families: List<SubstitutionFamily>,
    warnings: ImportWarnings,
) {
    div {
        materialsSectionBody(worldId, requirements, excluded, families, warnings)
    }
}

private fun DIV.materialsSectionBody(
    worldId: Int,
    requirements: Map<Item, Int>,
    excluded: Set<String>,
    families: List<SubstitutionFamily>,
    warnings: ImportWarnings,
) {
    id = MATERIALS_ID
    classes = setOf("import-review__materials")

    // One order for the whole section: the hidden field and the table must describe the same
    // list in the same sequence, or a swap would reorder the rows under the user.
    val rows = requirements.entries.sortedWith(
        compareByDescending<Map.Entry<Item, Int>> { it.value }.thenBy { it.key.name }
    )

    materialsField(rows, excluded)
    warningStrip(warnings)
    substitutionControls(worldId, families)
    materialsTable(rows, excluded, warnings)
}

private const val MATERIALS_ID = "import-review-materials"
private const val MATERIALS_FIELD_ID = "import-review-materials-field"

/**
 * The list itself — every row, its quantity and whether it is struck — in one field
 * (MCO-315). Rendered first inside the swappable section so it is among the very first
 * parameters in the body, and rewritten by `import-review.js` whenever a checkbox moves.
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
 * One control per family found in the list: "all Oak -> [ Spruce ]".
 *
 * The swap posts the form back and replaces this section with the rewritten one, which is why
 * the single materials field carries the struck rows too — a swap must not silently resurrect
 * or delete the rows you struck.
 *
 * The selects are deliberately *not* form controls: they carry no `name`, so a create submit
 * never drags a parameter per family along with it, and the swap button reads the one it needs
 * by id through `hx-vals`. `hx-vals` also says which family is being swapped.
 */
private fun FlowContent.substitutionControls(worldId: Int, families: List<SubstitutionFamily>) {
    if (families.isEmpty()) return

    div("import-review__swaps") {
        span("section-label") { +"Swap a family" }
        p("import-review__swaps-lead") {
            +"Change your mind about a material before it becomes a gathering list. Only targets that can take every row are offered."
        }
        families.forEach { family ->
            div("import-review__swap") {
                span("import-review__swap-from") {
                    +"All ${family.label} (${family.itemIds.size} ${if (family.itemIds.size == 1) "row" else "rows"})"
                }
                span("import-review__swap-arrow") {
                    attributes["aria-hidden"] = "true"
                    +"→"
                }
                select("form-control import-review__swap-to") {
                    id = "swap-to-${family.token}"
                    attributes["aria-label"] = "Swap all ${family.label} to"
                    family.targets.forEach { target ->
                        option {
                            value = target.token
                            +target.label
                        }
                    }
                }
                button(classes = "btn btn--ghost import-review__swap-apply") {
                    // Not a submit button — this edits the form, it does not send it.
                    type = ButtonType.button
                    hxPost("/worlds/$worldId/projects/import-review/substitute")
                    hxInclude("#import-review-form")
                    hxTarget("#$MATERIALS_ID")
                    hxSwap("outerHTML")
                    attributes["hx-vals"] =
                        """js:{"from": "${family.token}", "to": document.getElementById("swap-to-${family.token}").value}"""
                    +"Swap"
                }
            }
        }
    }
}

/**
 * One low-weight strip, one paragraph per kind of pain (MCO-305). It never blocks the import
 * and it never unchecks anything — it exists so that striking a row is a decision rather than
 * a discovery. Long lists are truncated: the per-row chips carry the full detail.
 */
private fun FlowContent.warningStrip(warnings: ImportWarnings) {
    if (warnings.isEmpty) return

    div("callout import-review__warnings") {
        span("callout__icon") {
            attributes["aria-hidden"] = "true"
            +"!"
        }
        div("callout__body") {
            ImportWarningKind.entries.forEach { kind ->
                val flagged = warnings.of(kind)
                if (flagged.isEmpty()) return@forEach
                p("import-review__warning") {
                    span("import-review__warning-heading") { +"${kind.heading}: " }
                    +namesOf(flagged)
                    +". ${kind.explanation}"
                }
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
