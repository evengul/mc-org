package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.IdeaDraft
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.idea.draft.name
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.idea.draft.toMessage
import app.mcorg.presentation.hxDeleteWithConfirm
import app.mcorg.presentation.hxInclude
import app.mcorg.presentation.hxPost
import app.mcorg.presentation.hxSwap
import app.mcorg.presentation.hxTarget
import app.mcorg.presentation.hxTargetError
import app.mcorg.presentation.templated.dsl.appHeader
import app.mcorg.presentation.templated.dsl.container
import app.mcorg.presentation.templated.dsl.pageShell
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.id
import kotlinx.html.main
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.DIV
import kotlinx.html.stream.createHTML

enum class DraftWizardStage(val displayName: String) {
    BASIC_INFO("Basic Info"),
    AUTHOR_INFO("Author"),
    VERSION_COMPATIBILITY("Version"),
    ITEM_REQUIREMENTS("Items"),
    CATEGORY_FIELDS("Category"),
    REVIEW("Review");

    fun next(): DraftWizardStage? = when (this) {
        BASIC_INFO -> AUTHOR_INFO
        AUTHOR_INFO -> VERSION_COMPATIBILITY
        VERSION_COMPATIBILITY -> ITEM_REQUIREMENTS
        ITEM_REQUIREMENTS -> CATEGORY_FIELDS
        CATEGORY_FIELDS -> REVIEW
        REVIEW -> null
    }

    fun previous(): DraftWizardStage? = when (this) {
        BASIC_INFO -> null
        AUTHOR_INFO -> BASIC_INFO
        VERSION_COMPATIBILITY -> AUTHOR_INFO
        ITEM_REQUIREMENTS -> VERSION_COMPATIBILITY
        CATEGORY_FIELDS -> ITEM_REQUIREMENTS
        REVIEW -> CATEGORY_FIELDS
    }
}

fun draftWizardPage(
    user: TokenProfile,
    draft: IdeaDraft,
    stage: DraftWizardStage,
    supportedVersions: List<MinecraftVersion.Release>
): String = pageShell(
    pageTitle = "MC-ORG — Edit Draft",
    user = user,
    stylesheets = listOf(
        "/static/styles/components/btn.css",
        "/static/styles/components/callout.css",
        "/static/styles/components/form.css",
        "/static/styles/pages/idea-wizard.css",
    )
) {
    appHeader(
        user = user,
        breadcrumbBlock = {
            link("Ideas", "/ideas").link("Drafts", "/ideas/create").current(stage.displayName)
        }
    )
    main {
        container {
            h1("wizard-title") {
                id = "wizard-title"
                +"${if (draft.name != null) "\"${draft.name}\"" else "New Draft"}"
            }
            wizardProgress(draft.id, stage)
            div {
                id = "wizard-stage"
                wizardStageContent(draft, stage, supportedVersions)
            }
        }
    }
}

fun FlowContent.wizardStageContent(
    draft: IdeaDraft,
    stage: DraftWizardStage,
    supportedVersions: List<MinecraftVersion.Release>,
    errors: List<ValidationFailure> = emptyList()
) {
    div("wizard-stage-container") {
        div("wizard-stage-form") {
            id = "wizard-form-inner"
            p("section-label") { +stage.displayName.uppercase() }
            if (errors.isNotEmpty()) {
                div("callout callout--error") {
                    span("callout__icon") { +"⚠" }
                    div("callout__body") {
                        errors.forEach { error -> p { +error.toMessage() } }
                    }
                }
            }
            div("wizard-fields") {
                id = "wizard-fields-content"
                when (stage) {
                    DraftWizardStage.BASIC_INFO -> draftBasicInfoFields(draft)
                    DraftWizardStage.AUTHOR_INFO -> draftAuthorFields(draft)
                    DraftWizardStage.VERSION_COMPATIBILITY -> draftVersionFields(draft, supportedVersions)
                    DraftWizardStage.ITEM_REQUIREMENTS -> draftItemRequirementFields(draft)
                    DraftWizardStage.CATEGORY_FIELDS -> draftCategoryFields(draft)
                    DraftWizardStage.REVIEW -> draftReviewFields(draft)
                }
            }
        }
        wizardNavigation(draft, stage)
    }
}

fun wizardProgressHtml(draftId: Int, currentStage: DraftWizardStage, oob: Boolean = false): String =
    createHTML().div("wizard-progress") {
        id = "wizard-progress"
        if (oob) attributes["hx-swap-oob"] = "true"
        renderProgressSteps(draftId, currentStage)
    }

fun FlowContent.wizardProgress(draftId: Int, currentStage: DraftWizardStage) {
    div("wizard-progress") {
        id = "wizard-progress"
        renderProgressSteps(draftId, currentStage)
    }
}

private fun DIV.renderProgressSteps(draftId: Int, currentStage: DraftWizardStage) {
    DraftWizardStage.entries.forEachIndexed { index, stage ->
        val isActive = stage == currentStage
        val isDone = stage.ordinal < currentStage.ordinal
        val stageClass = when {
            isActive -> "wizard-progress__step wizard-progress__step--active"
            isDone -> "wizard-progress__step wizard-progress__step--done"
            else -> "wizard-progress__step"
        }
        a(classes = stageClass) {
            href = "/ideas/drafts/$draftId/edit?stage=${stage.name}"
            hxPost("/ideas/drafts/$draftId/stage")
            hxTarget("#wizard-stage")
            hxSwap("outerHTML")
            hxInclude("#wizard-fields-content")
            hxTargetError("#wizard-stage")
            attributes["hx-vals"] = """{"currentStage":"${currentStage.name}","targetStage":"${stage.name}"}"""
            span("wizard-progress__num") { +"${index + 1}" }
            span("wizard-progress__label") { +stage.displayName }
        }
    }
}

private fun FlowContent.wizardNavigation(draft: IdeaDraft, stage: DraftWizardStage) {
    val draftId = draft.id
    div("wizard-nav") {
        val prev = stage.previous()
        val next = stage.next()

        if (prev != null) {
            a(classes = "btn btn--ghost") {
                href = "/ideas/drafts/$draftId/edit?stage=${prev.name}"
                hxPost("/ideas/drafts/$draftId/stage")
                hxTarget("#wizard-stage")
                hxSwap("outerHTML")
                hxInclude("#wizard-fields-content")
                hxTargetError("#wizard-stage")
                attributes["hx-vals"] = """{"currentStage":"${stage.name}","targetStage":"${prev.name}"}"""
                +"Back"
            }
        } else {
            span {}
        }

        if (draft.sourceIdeaId != null) {
            button(classes = "btn btn--ghost") {
                type = ButtonType.button
                hxDeleteWithConfirm(
                    url = "/ideas/drafts/$draftId",
                    title = "Cancel editing",
                    description = "The idea will be restored and made visible again. Your changes will be lost."
                )
                hxSwap("none")
                +"Cancel editing"
            }
        }

        if (next != null) {
            button(classes = "btn btn--primary") {
                type = ButtonType.button
                hxPost("/ideas/drafts/$draftId/stage")
                hxTarget("#wizard-stage")
                hxSwap("outerHTML")
                hxTargetError("#wizard-stage")
                attributes["hx-include"] = "#wizard-fields-content"
                attributes["hx-vals"] = """{"currentStage":"${stage.name}"}"""
                +"Save & Continue"
            }
        } else {
            button(classes = "btn btn--primary") {
                type = ButtonType.button
                hxPost("/ideas/drafts/$draftId/publish")
                hxTarget("#wizard-stage")
                hxSwap("outerHTML")
                attributes["hx-include"] = "#wizard-fields-content"
                +"Publish Idea"
            }
        }
    }
}
