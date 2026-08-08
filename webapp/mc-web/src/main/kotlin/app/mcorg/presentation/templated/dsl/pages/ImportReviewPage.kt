package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.user.TokenProfile
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
 * The whole material list is in this page's form: a checkbox per row (checked = keep) and
 * a hidden quantity beside it. An unchecked box is simply not submitted, so excluding is
 * exclusion with no server-side concept behind it. Nothing is persisted until Create.
 *
 * Shared by both import doors (MCO-306): the schematic upload posts its parsed list here,
 * and an idea import arrives with the idea's requirements. [action] and [hiddenFields] are
 * the only difference — what the screen *does* is identical either way.
 *
 * Batch substitution (MCO-304) rewrites the list in place: the swap controls post the
 * current form back and swap [importReviewMaterials] for a rewritten copy, so a swap is
 * just another edit to an unsaved form. The unobtainable/expensive warning strip (MCO-305)
 * lands on this same screen.
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
): String = pageShell(
    pageTitle = "Seam — review import",
    user = user,
    stylesheets = listOf(
        "/static/styles/components/btn.css",
        "/static/styles/components/form.css",
        "/static/styles/pages/import-review.css",
    ),
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

                materialsSection(worldId, requirements, excluded = emptySet(), families = families)

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
 * (MCO-304). Everything a swap can change lives in here — the controls, the row set and the
 * quantities; the project name and the hidden carry-through fields sit outside and survive
 * untouched.
 */
fun importReviewMaterials(
    worldId: Int,
    requirements: Map<Item, Int>,
    excluded: Set<String>,
    families: List<SubstitutionFamily>,
): String = createHTML().div {
    materialsSectionBody(worldId, requirements, excluded, families)
}

private fun FlowContent.materialsSection(
    worldId: Int,
    requirements: Map<Item, Int>,
    excluded: Set<String>,
    families: List<SubstitutionFamily>,
) {
    div {
        materialsSectionBody(worldId, requirements, excluded, families)
    }
}

private fun DIV.materialsSectionBody(
    worldId: Int,
    requirements: Map<Item, Int>,
    excluded: Set<String>,
    families: List<SubstitutionFamily>,
) {
    id = MATERIALS_ID
    classes = setOf("import-review__materials")
    substitutionControls(worldId, families)
    materialsTable(requirements, excluded)
}

private const val MATERIALS_ID = "import-review-materials"

/**
 * One control per family found in the list: "all Oak -> [ Spruce ]".
 *
 * The swap posts the *whole* form and swaps this section for the rewritten one, which is why
 * the rows carry a hidden `row[...]` mirror of their quantity — an unchecked checkbox submits
 * nothing, and a swap must not silently resurrect or delete the rows you struck.
 *
 * The target select is named per family (`to[<token>]`) so several controls can coexist in
 * one form; the button says which family it is via `hx-vals`.
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
                    name = "to[${family.token}]"
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
                    attributes["hx-vals"] = """{"from": "${family.token}"}"""
                    +"Swap"
                }
            }
        }
    }
}

private fun FlowContent.materialsTable(requirements: Map<Item, Int>, excluded: Set<String>) {
    val rows = requirements.entries.sortedWith(
        compareByDescending<Map.Entry<Item, Int>> { it.value }.thenBy { it.key.name }
    )

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
                            input(type = InputType.checkBox, classes = "import-review__include") {
                                id = "include-$rowId"
                                // Unchecked boxes are not submitted — that *is* the exclusion.
                                name = "qty[${item.id}]"
                                value = amount.toString()
                                checked = item.id !in excluded
                                attributes["aria-label"] = "Include ${item.name}"
                            }
                            // The row's quantity again, always submitted. Create ignores it;
                            // a substitution needs it, because the rows you struck are absent
                            // from `qty[...]` and would otherwise vanish across the swap.
                            hiddenInput {
                                name = "row[${item.id}]"
                                value = amount.toString()
                            }
                        }
                        td {
                            attributes["data-label"] = "Item"
                            label("import-review__item-name") {
                                htmlFor = "include-$rowId"
                                +item.name
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

    // Nothing to submit when every row is unchecked; the server says so too, but a form
    // with no rows at all should not have reached this page in the first place.
    if (rows.isEmpty()) {
        hiddenInput { name = "empty"; value = "true" }
        p("form-error") { +"This schematic contains no recognisable materials." }
    }
}
