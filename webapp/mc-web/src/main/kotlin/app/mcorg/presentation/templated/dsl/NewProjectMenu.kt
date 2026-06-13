package app.mcorg.presentation.templated.dsl

import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.span
import kotlinx.html.summary

/**
 * "+ New project" — a dropdown gathering every way to start a project
 * ("pick a door"). A fourth door (describe a build) slots in later.
 * Outside-click/Escape dismissal lives in /static/scripts/np-menu.js.
 */
fun FlowContent.newProjectMenu(worldId: Int) {
    details("np-menu") {
        attributes["id"] = "new-project-menu"
        summary("btn btn--primary np-menu__trigger") { +"+ New project" }
        div("np-menu__panel") {
            div("np-menu__header") {
                span("section-label") { +"New project · pick a door" }
            }
            button(classes = "np-menu__door") {
                type = ButtonType.button
                attributes["onclick"] =
                    "document.getElementById('new-project-menu')?.removeAttribute('open'); document.getElementById('schematic-project-modal')?.showModal()"
                span("np-menu__door-glyph") { +"⤓" }
                span("np-menu__door-text") {
                    span("np-menu__door-title") { +"From a schematic" }
                    span("np-menu__door-sub") { +".litematic" }
                }
            }
            a(classes = "np-menu__door") {
                href = "/ideas"
                span("np-menu__door-glyph") { +"◆" }
                span("np-menu__door-text") {
                    span("np-menu__door-title") { +"From an idea" }
                    span("np-menu__door-sub") { +"browse the bank" }
                }
            }
            button(classes = "np-menu__door") {
                type = ButtonType.button
                attributes["onclick"] =
                    "document.getElementById('new-project-menu')?.removeAttribute('open'); document.getElementById('first-project-flag').value=''; document.getElementById('create-project-modal')?.showModal()"
                span("np-menu__door-glyph") { +"+" }
                span("np-menu__door-text") {
                    span("np-menu__door-title") { +"Blank project" }
                    span("np-menu__door-sub") { +"name it, fill it later" }
                }
            }
        }
    }
}
