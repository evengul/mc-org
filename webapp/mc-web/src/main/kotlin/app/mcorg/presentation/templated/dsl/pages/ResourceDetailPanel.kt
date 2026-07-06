package app.mcorg.presentation.templated.dsl.pages

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.presentation.hxDelete
import app.mcorg.presentation.hxDeleteWithConfirm
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTrigger
import app.mcorg.presentation.oobTableRow
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.stream.createHTML

/**
 * Renders the resource detail panel fragment that swaps into #resource-panel-content.
 * Contains a header with close controls, editable required quantity, source section,
 * and a remove-resource footer.
 */
fun FlowContent.resourceDetailPanel(
    worldId: Int,
    projectId: Int,
    resource: ResourceGatheringItem,
    projectsInWorld: List<Pair<Int, String>>,
    variantCandidates: List<Item> = emptyList(),
) {
    div("resource-panel__header") {
        button(classes = "resource-panel__close-btn") {
            type = ButtonType.button
            attributes["data-resource-panel-close"] = "true"
            attributes["aria-label"] = "Back"
            +"\u2190"
        }
        button(classes = "resource-panel__close-btn resource-panel__close-btn--x") {
            type = ButtonType.button
            attributes["data-resource-panel-close"] = "true"
            attributes["aria-label"] = "Close"
            +"\u00d7"
        }
    }
    div("resource-panel__title") {
        h2("resource-panel__item-name") { +resource.name }
        div("resource-panel__qty") {
            attributes["data-resource-id"] = resource.id.toString()
            attributes["data-current-qty"] = resource.required.toString()
            span("resource-panel__qty-label") { +"Required:" }
            span("resource-panel__qty-display") {
                +resource.required.toString()
            }
            input(type = InputType.number, classes = "resource-panel__qty-input") {
                name = "required"
                min = "1"
                max = "2000000000"
                value = resource.required.toString()
                attributes["data-resource-id"] = resource.id.toString()
                hxPatch("/worlds/$worldId/projects/$projectId/resources/gathering/${resource.id}/required")
                hxTarget("#plan-row-${resource.id}")
                hxSwap("outerHTML")
                hxTrigger("change")
            }
        }
    }
    div("resource-panel__divider") { +"Source" }
    div("resource-panel__source") {
        id = "resource-panel-source"
        resourcePanelSourceSection(worldId, projectId, resource, projectsInWorld)
    }
    div("resource-panel__divider") { +"Variant" }
    div("resource-panel__variant") {
        id = "resource-panel-variant"
        resourcePanelVariantSection(worldId, projectId, resource, variantCandidates)
    }
    div("resource-panel__footer") {
        button(classes = "btn btn--danger resource-panel__remove-btn") {
            type = ButtonType.button
            hxDeleteWithConfirm(
                url = "/worlds/$worldId/projects/$projectId/resources/gathering/${resource.id}?context=plan",
                title = "Remove ${resource.name}",
                description = "${resource.name} and its source assignment will be permanently removed.",
            )
            hxTarget("#plan-row-${resource.id}")
            hxSwap("outerHTML")
            attributes["data-resource-panel-remove"] = "true"
            +"Remove resource"
        }
    }
}

/**
 * Inner source section — rendered on initial panel load and re-rendered by PATCH/DELETE /source
 * responses to swap into #resource-panel-source.
 */
fun FlowContent.resourcePanelSourceSection(
    worldId: Int,
    projectId: Int,
    resource: ResourceGatheringItem,
    projectsInWorld: List<Pair<Int, String>>,
) {
    val sourceUrl = "/worlds/$worldId/projects/$projectId/resources/gathering/${resource.id}/source"
    when (resource.sourceType) {
        null -> {
            p("resource-panel__source-empty") { +"No source selected" }
            button(classes = "btn btn--secondary resource-panel__source-btn") {
                type = ButtonType.button
                hxPatch(sourceUrl)
                attributes["hx-vals"] = """{"type":"manual"}"""
                hxTarget("#resource-panel-source")
                hxSwap("innerHTML")
                +"Manual gather"
            }
            if (projectsInWorld.isNotEmpty()) {
                div("resource-panel__project-picker") {
                    label("resource-panel__project-picker-label") {
                        htmlFor = "resource-panel-project-select"
                        +"Use from existing project"
                    }
                    select(classes = "resource-panel__project-select") {
                        id = "resource-panel-project-select"
                        name = "projectId"
                        hxPatch(sourceUrl)
                        attributes["hx-vals"] = """js:{type:"project",projectId:event.target.value}"""
                        hxTarget("#resource-panel-source")
                        hxSwap("innerHTML")
                        hxTrigger("change")
                        option {
                            value = ""
                            disabled = true
                            selected = true
                            +"Select a project..."
                        }
                        projectsInWorld.forEach { (id, name) ->
                            option {
                                value = id.toString()
                                +name
                            }
                        }
                    }
                }
            }
        }
        "manual" -> {
            div("resource-panel__source-set") {
                span("status-dot status-dot--set") {}
                span("resource-panel__source-label") { +"Manual gather" }
                button(classes = "btn btn--ghost btn--sm resource-panel__change-btn") {
                    type = ButtonType.button
                    hxDelete(sourceUrl)
                    hxTarget("#resource-panel-source")
                    hxSwap("innerHTML")
                    +"Change"
                }
            }
        }
        "project" -> {
            val label = resource.solvedByProject?.second ?: "Unknown project"
            div("resource-panel__source-set") {
                span("status-dot status-dot--set") {}
                span("resource-panel__source-label") { +label }
                button(classes = "btn btn--ghost btn--sm resource-panel__change-btn") {
                    type = ButtonType.button
                    hxDelete(sourceUrl)
                    hxTarget("#resource-panel-source")
                    hxSwap("innerHTML")
                    +"Change"
                }
            }
        }
        else -> {
            p("resource-panel__source-empty") { +"No source selected" }
        }
    }
}

/**
 * Variant section (MCO-246): lists other members of any tag family [resource]'s item belongs
 * to (see [app.mcorg.pipeline.resources.findVariantCandidates]), each a clickable option that
 * PATCHes `/variant` to swap the target's item in place. Empty [variantCandidates] renders an
 * explanatory empty state rather than hiding the section, since coverage is data-dependent
 * (only tag families the recipe/loot data actually references are known) and a user shouldn't
 * be left wondering whether the control is broken.
 */
fun FlowContent.resourcePanelVariantSection(
    worldId: Int,
    projectId: Int,
    resource: ResourceGatheringItem,
    variantCandidates: List<Item>,
) {
    if (variantCandidates.isEmpty()) {
        p("resource-panel__source-empty") { +"No known variants for this item." }
        return
    }
    div("stack--xs") {
        for (candidate in variantCandidates) {
            div("picker-opt") {
                attributes["hx-patch"] = "/worlds/$worldId/projects/$projectId/resources/gathering/${resource.id}/variant"
                attributes["hx-vals"] = """{"itemId":"${candidate.id}"}"""
                attributes["hx-target"] = "#plan-resources-area"
                attributes["hx-swap"] = "outerHTML"
                span("picker-opt__name") { +candidate.name }
            }
        }
    }
}

/**
 * Full-fragment response for GET .../detail-panel — returns the inner content of #resource-panel-content.
 */
fun resourceDetailPanelFragment(
    worldId: Int,
    projectId: Int,
    resource: ResourceGatheringItem,
    projectsInWorld: List<Pair<Int, String>>,
    variantCandidates: List<Item> = emptyList(),
): String = createHTML().div {
    resourceDetailPanel(worldId, projectId, resource, projectsInWorld, variantCandidates)
}

/**
 * Out-of-band refresh of the whole panel (MCO-246) — used by the `/variant` swap response so an
 * open resource-detail panel reflects the new item/name/variant list without needing to be
 * reopened. The main response target for a swap is `#plan-resources-area` (the row's name/qty
 * live there); this is the sidecar that keeps the panel in sync alongside it.
 */
fun resourceDetailPanelOobFragment(
    worldId: Int,
    projectId: Int,
    resource: ResourceGatheringItem,
    projectsInWorld: List<Pair<Int, String>>,
    variantCandidates: List<Item> = emptyList(),
): String = createHTML().div {
    hxOutOfBands("innerHTML:#resource-panel-content")
    resourceDetailPanel(worldId, projectId, resource, projectsInWorld, variantCandidates)
}

/**
 * Source-section fragment + OOB row refresh — used by PATCH and DELETE /source responses.
 * Main target: #resource-panel-source (innerHTML). OOB: #plan-row-{id} (outerHTML) via
 * `<template>`-wrapped row so the browser parser does not strip the orphan `<tr>`.
 */
fun resourcePanelSourceWithRowOob(
    worldId: Int,
    projectId: Int,
    resource: ResourceGatheringItem,
    projectsInWorld: List<Pair<Int, String>>,
): String = createHTML().div {
    resourcePanelSourceSection(worldId, projectId, resource, projectsInWorld)
    oobTableRow(targetId = "plan-row-${resource.id}") {
        planResourceRow(worldId, projectId, resource)
    }
}
