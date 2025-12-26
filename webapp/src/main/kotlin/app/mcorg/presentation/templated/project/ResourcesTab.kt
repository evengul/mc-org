package app.mcorg.presentation.templated.project

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.project.Project
import app.mcorg.domain.model.project.ProjectProduction
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.pipeline.resources.FoundIdea
import app.mcorg.presentation.hxDeleteWithConfirm
import app.mcorg.presentation.hxGet
import app.mcorg.presentation.hxPatch
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTargetError
import app.mcorg.presentation.templated.common.button.IconButtonColor
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.button.ghostButton
import app.mcorg.presentation.templated.common.button.iconButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.chip.ChipVariant
import app.mcorg.presentation.templated.common.chip.chipComponent
import app.mcorg.presentation.templated.common.emptystate.EmptyStateVariant
import app.mcorg.presentation.templated.common.emptystate.emptyState
import app.mcorg.presentation.templated.common.form.searchableselect.SearchableSelectOption
import app.mcorg.presentation.templated.common.form.searchableselect.searchableSelect
import app.mcorg.presentation.templated.common.icon.IconSize
import app.mcorg.presentation.templated.common.icon.Icons
import app.mcorg.presentation.templated.common.link.Link
import app.mcorg.presentation.templated.common.progress.progressComponent
import kotlinx.html.DIV
import kotlinx.html.FormEncType
import kotlinx.html.InputType
import kotlinx.html.LI
import kotlinx.html.a
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.li
import kotlinx.html.onSubmit
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.ul
import java.util.*

fun DIV.resourcesTab(
    user: TokenProfile,
    project: Project,
    production: List<ProjectProduction>,
    gathering: List<ResourceGatheringItem>,
    itemNames: List<Item>
) {
    val totalNeeded = gathering.sumOf { it.required }
    val totalCollected = gathering.sumOf { it.collected }

    classes += "project-resources-tab"

    div("project-resources-collection") {
        h2 {
            + "Item Collection Progress"
        }
        div("project-resources-collection-summary") {
            div("project-resources-collection-summary-header") {
                p {
                    + "Total Progress"
                }
                progressComponent {
                    id = "resource-gathering-total-progress"
                    value = totalCollected.toDouble()
                    max = totalNeeded.toDouble()
                    showPercentage = false
                    label = progressText(totalNeeded, totalCollected)
                }
            }
        }
        form {
            id = "project-resource-gathering-form"
            encType = FormEncType.applicationXWwwFormUrlEncoded
            hxTargetError(".validation-error-message")
            if (!user.isDemoUserInProduction) {
                hxPost(Link.Worlds.world(project.worldId).project(project.id).to + "/resources/gathering")
                hxTarget("#project-resource-gathering-list")
                hxSwap("afterbegin")
                attributes["hx-on::after-request"] = "this.reset(); document.getElementById('project-resources-name-input')?.focus();"
            } else {
                onSubmit = "return false;"
            }

            searchableSelect(
                id = "project-resource-gathering-item-select",
                name = "requiredItemId",
                options = itemNames.map { SearchableSelectOption(
                    value = it.id,
                    label = it.name
                ) },
            ) {
                placeholder = "Item name"
                required = true
            }

            input {
                id = "project-resource-gathering-required-input"
                name = "requiredAmount"
                placeholder = "Items needed"
                type = InputType.number
                required = true
                min = "1"
                max = "2000000000"
            }

            neutralButton(if (user.isDemoUserInProduction) "Require Resource (Disabled in Demo)" else "Require Resource") {
                iconLeft = Icons.Menu.CONTRAPTIONS
                iconSize = IconSize.SMALL
            }

            p("validation-error-message") {
                id = "validation-error-requiredItemId"
            }
            p("validation-error-message") {
                id = "validation-error-requiredAmount"
            }
        }
        ul {
            id = "project-resource-gathering-list"
            gathering.forEach {
                li {
                    projectResourceGatheringItem(project.worldId, project.id, it)
                }
            }
        }
    }
    div("project-resources-production") {
        div {
            h2 {
                + "Resource Production"
            }
            p("subtle") {
                + "The resources that this project will produce when complete."
            }
        }
        if (project.stage != ProjectStage.COMPLETED) {
            form {
                id = "project-resources-production-form"
                encType = FormEncType.applicationXWwwFormUrlEncoded
                hxTargetError(".validation-error-message")
                if (!user.isDemoUserInProduction) {
                    hxPost(Link.Worlds.world(project.worldId).project(project.id).to + "/resources/production")
                    hxTarget("#project-resources-production-list")
                    hxSwap("afterbegin")
                    attributes["hx-on::after-request"] = "this.reset(); document.getElementById('project-resources-name-input')?.focus();"
                } else {
                    onSubmit = "return false;"
                }

                searchableSelect(
                    id = "project-resources-item-select",
                    name = "itemId",
                    options = itemNames.map { SearchableSelectOption(
                        value = it.id,
                        label = it.name
                    ) },
                ) {
                    placeholder = "Item name (e.g., Oak Logs, Stone, Diamond)"
                    required = true
                }

                input {
                    id = "project-resources-rate-input"
                    name = "ratePerHour"
                    placeholder = "Production rate per hour (If applicable)"
                    type = InputType.number
                    required = false
                    min = "0"
                    max = "2000000000"
                }

                neutralButton(if (user.isDemoUserInProduction) "Add Resource (Disabled in Demo)" else "Add Resource Production") {
                    iconLeft = Icons.Menu.CONTRAPTIONS
                    iconSize = IconSize.SMALL
                }

                p("validation-error-message") {
                    id = "validation-error-itemId"
                }
                p("validation-error-message") {
                    id = "validation-error-ratePerHour"
                }
            }
        }
        if (production.isEmpty() && project.stage != ProjectStage.COMPLETED) {
            emptyState(
                id = "empty-resource-production-state",
                title = "No Resource Production",
                description = "This project doesn't produce any resources yet. Add resources that this project will generate when complete, like farms or automated systems.",
                icon = Icons.Menu.CONTRAPTIONS,
                variant = EmptyStateVariant.COMPACT,
                useH2 = false
            )
        }
        ul {
            id = "project-resources-production-list"
            production.sortedBy { it.name }.forEach { prod ->
                li {
                    projectResourceProductionItem(project.worldId, prod)
                }
            }
        }
    }
}

fun LI.projectResourceGatheringItem(worldId: Int, projectId: Int, item: ResourceGatheringItem) {
    id = "project-resource-gathering-${item.id}"
    classes += "project-resources-collection-summary"
    div("project-resources-collection-summary-header") {
        p {
            + item.name
        }
        resourceGatheringProgress("resource-gathering-item-${item.id}-progress", item.collected, item.required)
    }

    div("project-resources-collection-summary-actions") {
        if (item.solvedByProject != null) {
            chipComponent {
                icon = Icons.Menu.PROJECTS
                variant = ChipVariant.SUCCESS
                href = Link.Worlds.world(worldId).project(item.solvedByProject.first).to
                + "Farmable with ${item.solvedByProject.second}"
            }
        } else {
            ghostButton("Find Farm Ideas") {
                iconLeft = Icons.Search
                iconSize = IconSize.SMALL
                buttonBlock = {
                    hxGet("/app/worlds/$worldId/projects/$projectId/resources/gathering/${item.id}/matching-ideas")
                    hxTarget("#found-ideas-for-task-${item.id}")
                    hxSwap("innerHTML")
                }
            }
        }
        addResourcesActions(worldId, projectId, item.id)
        iconButton(Icons.DELETE, "Delete item requirement", color = IconButtonColor.DANGER, iconSize = IconSize.SMALL) {
            buttonBlock = {
                hxDeleteWithConfirm(
                    url = Link.Worlds.world(worldId).project(projectId).to + "/resources/gathering/${item.id}",
                    title = "Delete Item Requirement",
                    description = "Do you no longer require this item?"
                )
                hxTarget("#project-resource-gathering-${item.id}")
                hxSwap("delete")
            }
        }
    }

    div("project-resources-collection-found-ideas") {
        foundIdeas(worldId, item.id to item.name)
    }
}

fun DIV.resourceGatheringProgress(divId: String, collected: Int, required: Int) {
    progressComponent {
        id = divId
        value = collected.toDouble()
        max = required.toDouble()
        showPercentage = false
        label = progressText(required, collected)
    }
}

private fun progressText(required: Int, collected: Int): String {
    // Special case: single item
    if (required == 1) {
        return if (collected >= 1) "Collected" else "Not Collected"
    }

    // Determine the best unit based on required amount
    return when {
        required >= 1728 -> {
            // Use shulker boxes (1728 items per shulker box)
            val requiredShulkers = required / 1728
            val requiredRemainder = required % 1728
            val collectedShulkers = collected / 1728
            val collectedRemainder = collected % 1728

            buildString {
                // Collected portion
                if (collectedShulkers > 0) {
                    append(collectedShulkers)
                    if (collectedRemainder > 0) {
                        val collectedStacks = collectedRemainder / 64
                        val collectedItems = collectedRemainder % 64
                        if (collectedStacks > 0) {
                            append(" shulker boxes + $collectedStacks stacks")
                            if (collectedItems > 0) append(" + $collectedItems items")
                        } else if (collectedItems > 0) {
                            append(" shulker boxes + $collectedItems items")
                        } else {
                            append(" shulker boxes")
                        }
                    } else {
                        append(" shulker boxes")
                    }
                } else {
                    // Less than 1 shulker box collected - just show stacks/items
                    val collectedStacks = collectedRemainder / 64
                    val collectedItems = collectedRemainder % 64
                    if (collectedStacks > 0) {
                        append(collectedStacks)
                        append(" stacks")
                        if (collectedItems > 0) append(" + $collectedItems items")
                    } else {
                        append(collectedItems)
                        append(" items")
                    }
                }

                append(" / ")

                // Required portion
                append(requiredShulkers)
                if (requiredRemainder > 0) {
                    val requiredStacks = requiredRemainder / 64
                    val requiredItems = requiredRemainder % 64
                    if (requiredStacks > 0) {
                        append(" shulker boxes + $requiredStacks stacks")
                        if (requiredItems > 0) append(" + $requiredItems items")
                    } else if (requiredItems > 0) {
                        append(" shulker boxes + $requiredItems items")
                    } else {
                        append(" shulker boxes")
                    }
                } else {
                    append(" shulker boxes")
                }
                append(" collected")
            }
        }
        required >= 64 -> {
            // Use stacks (64 items per stack)
            val requiredStacks = required / 64
            val requiredRemainder = required % 64
            val collectedStacks = collected / 64
            val collectedRemainder = collected % 64

            buildString {
                if (collectedStacks > 0) {
                    append(collectedStacks)
                    if (collectedRemainder > 0) {
                        append(" stacks + $collectedRemainder")
                    } else {
                        append(" stacks")
                    }
                } else {
                    // Less than 1 stack collected - just show items
                    append(collectedRemainder)
                }

                append(" / $requiredStacks")
                if (requiredRemainder > 0) {
                    append(" stacks + $requiredRemainder")
                } else {
                    append(" stacks")
                }
                append(" collected")
            }
        }
        else -> {
            // Use individual items
            "$collected / $required collected"
        }
    }
}

private fun DIV.addResourcesActions(worldId: Int, projectId: Int, gatheringId: Int) {
    div("item-requirement-actions") {
        intArrayOf(1, 64, 1728, 3456).forEach { amount ->
            ghostButton("+$amount") {
                buttonBlock = {
                    attributes["hx-vals"] = """{"amount": $amount}"""
                    hxPatch("/app/worlds/$worldId/projects/$projectId/resources/gathering/$gatheringId/done-more")
                    hxTarget("#resource-gathering-item-${gatheringId}-progress")
                    hxSwap("outerHTML")
                }
            }
        }
    }
}

fun DIV.foundIdeas(worldId: Int, task: Pair<Int, String>, ideas: List<FoundIdea>? = null) {
    id = "found-ideas-for-task-${task.first}"
    if (ideas != null) {
        p {
            + "Ideas that produce ${task.second}: "
        }
        if (ideas.isEmpty()) {
            p("subtle") {
                + "No ideas found."
            }
        } else {
            ul {
                ideas.forEach { idea ->
                    li {
                        if (idea.alreadyImported) {
                            span {
                                + "${idea.name} (Already imported)"
                            }
                        } else {
                            a(href = Link.Ideas.single(idea.id)) {
                                attributes["target"] = "_blank"
                                attributes["rel"] = "noopener noreferrer"
                                + idea.name
                            }
                            + "Rate: ${idea.rate} / hour"
                            actionButton("Import ${idea.name} into this world") {
                                buttonBlock = {
                                    hxPost(Link.Ideas.single(idea.id) + "/import?worldId=$worldId&forTask=${task.first}")
                                    hxSwap("none")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun LI.projectResourceProductionItem(worldId: Int, production: ProjectProduction) {
    id = "project-resource-production-${production.id}"
    span {
        classes = setOf("production-item-start")
        p {
            + production.name
        }
        p("small") {
            if (production.ratePerHour > 0) {
                + "${production.ratePerHour.toRate()} per hour"
            } else {
                + "No hourly production rate set."
            }
        }
    }
    span {
        classes = setOf("production-item-end")
        iconButton(Icons.DELETE, "Delete project production value", color = IconButtonColor.DANGER, iconSize = IconSize.SMALL) {
            buttonBlock = {
                hxDeleteWithConfirm(
                    url = Link.Worlds.world(worldId).project(production.projectId).to + "/resources/production/${production.id}",
                    title = "Delete Production Item",
                    description = "Are you sure you want to delete this production item?"
                )
                hxTarget("#project-resource-production-${production.id}")
                hxSwap("delete")
            }
        }
    }
}

private fun Int.toRate(): String {
    if (this < 1000) {
        return this.toString()
    }
    if (this < 1_000_000) {
        val value = this / 1000.0
        return if (value % 1.0 == 0.0) {
            "${value.toInt()}k"
        } else {
            "${String.format(Locale.US, "%.1f", value)}k"
        }
    }
    val value = this / 1_000_000.0
    return if (value % 1.0 == 0.0) {
        "${value.toInt()}M"
    } else {
        "${String.format(Locale.US, "%.2f", value)}M"
    }
}