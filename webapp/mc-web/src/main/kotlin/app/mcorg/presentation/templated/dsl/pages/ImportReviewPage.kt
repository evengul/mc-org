package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.user.TokenProfile
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
 * The whole material list is in this page's form: a checkbox per row (checked = keep) and
 * a hidden quantity beside it. An unchecked box is simply not submitted, so excluding is
 * exclusion with no server-side concept behind it. Nothing is persisted until Create.
 *
 * Substitution (MCO-304) and the unobtainable/expensive warning strip (MCO-305) land on
 * this same screen.
 */
fun importReviewPage(
    user: TokenProfile,
    worldId: Int,
    worldName: String,
    projectName: String,
    requirements: Map<Item, Int>,
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
                action = "/worlds/$worldId/projects/from-schematic"

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

                materialsTable(requirements)

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

private fun FlowContent.materialsTable(requirements: Map<Item, Int>) {
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
                                checked = true
                                attributes["aria-label"] = "Include ${item.name}"
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
