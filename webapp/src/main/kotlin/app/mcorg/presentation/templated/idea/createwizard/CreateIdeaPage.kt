package app.mcorg.presentation.templated.idea.createwizard

import app.mcorg.domain.model.idea.IdeaDifficulty
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.presentation.*
import app.mcorg.presentation.templated.common.button.actionButton
import app.mcorg.presentation.templated.common.button.neutralButton
import app.mcorg.presentation.templated.common.page.createPage
import app.mcorg.presentation.templated.utils.toPrettyEnumName
import app.mcorg.presentation.utils.BreadcrumbBuilder
import kotlinx.html.*

enum class CreateIdeaStage(val displayName: String, private val previousStep: String?, private val nextStep: String?) {
    BASIC_INFO("Basic Information", null, "AUTHOR_INFO"),
    AUTHOR_INFO("Author Information", "BASIC_INFO", "VERSION_COMPATIBILITY"),
    VERSION_COMPATIBILITY("Version Compatibility", "AUTHOR_INFO", "CATEGORY_SPECIFIC_FIELDS"),
    CATEGORY_SPECIFIC_FIELDS("Category Specific Fields", "VERSION_COMPATIBILITY", "REVIEW_SUBMIT"),
    REVIEW_SUBMIT("Review & Submit", "CATEGORY_SPECIFIC_FIELDS", null);

    fun getPreviousStep(): CreateIdeaStage? {
        return previousStep?.let { valueOf(it) }
    }

    fun getNextStep(): CreateIdeaStage? {
        return nextStep?.let { valueOf(it) }
    }
}

fun createIdeaPage(
    user: TokenProfile,
    unreadNotifications: Int,
    supportedVersions: List<MinecraftVersion.Release>,
    data: CreateIdeaWizardData
) = createPage(
    pageTitle = "Create Idea",
    user = user,
    unreadNotificationCount = unreadNotifications,
    breadcrumbs = BreadcrumbBuilder.buildForCreateIdea()
) {
    id = "create-idea-page"
    h1 {
        +"Create New Idea"
    }

    form {
        createIdeaStageContent(data, supportedVersions)
    }
}

fun FORM.createIdeaStageContent(data: CreateIdeaWizardData, supportedVersions: List<MinecraftVersion.Release>) {
    id = "create-idea-form"

    hxPost("/app/ideas/create")
    hxSwap("none")

    when(data.stage) {
        CreateIdeaStage.BASIC_INFO -> generalFields(data)
        CreateIdeaStage.AUTHOR_INFO -> authorFields(data)
        CreateIdeaStage.VERSION_COMPATIBILITY -> versionFields(supportedVersions, data.versionRange)
        CreateIdeaStage.CATEGORY_SPECIFIC_FIELDS -> categoryFields(data)
        CreateIdeaStage.REVIEW_SUBMIT -> reviewIdeaFields(data)
    }

    hiddenFields(data)

    span {
        navigationButtons(data.stage)
    }
}

private fun SPAN.navigationButtons(stage: CreateIdeaStage) {
    classes += "form-navigation-buttons"
    stage.getPreviousStep()?.let {
        neutralButton("Previous stage: ${it.displayName}") {
            buttonBlock = {
                type = ButtonType.button
                hxGet("/app/ideas/create?stage=${it.name}")
                hxInclude("#create-idea-form")
                hxTarget("#create-idea-form")
                hxSwap("outerHTML")
                attributes["hx-validate"] = "false"
            }
        }
    }

    if (stage != CreateIdeaStage.REVIEW_SUBMIT) {
        stage.getNextStep()?.let {
            actionButton("Next stage: ${it.displayName}") {
                buttonBlock = {
                    id = "next-stage-button"
                    type = ButtonType.button
                    hxGet("/app/ideas/create?stage=${it.name}")
                    hxInclude("#create-idea-form")
                    hxTarget("#create-idea-form")
                    hxSwap("outerHTML")
                    attributes["hx-validate"] = "true"
                    attributes["hx-on:htmx:validation:halted"] = """
                        const validationMessageElements = document.querySelectorAll('.validation-error-message');
                        validationMessageElements.forEach(function(element) {
                            element.textContent = '';
                        });
                        
                        event.detail.errors.forEach(function(error) { 
                            const validationMessageElement = document.getElementById('validation-error-' + error.elt.name); 
                            if (validationMessageElement) { 
                                validationMessageElement.textContent = error.message; 
                            }
                        });
                    """.trimIndent()
                }
            }
        }
    } else {
        actionButton("Submit Idea") {
            buttonBlock = {
                type = ButtonType.submit
            }
        }
    }
}

private fun FORM.generalFields(data: CreateIdeaWizardData) {
    label {
        htmlFor = "idea-name"
        +"Name"
        span("required-indicator") { +"*" }
    }
    input {
        id = "idea-name"
        name = "name"
        type = InputType.text
        classes += "form-control"
        required = true
        minLength = "3"
        maxLength = "100"
        if (data.name != null) {
            value = data.name
        }
        placeholder = "e.g., High-Speed Sugar Cane Farm"
    }
    p("validation-error-message") {
        id = "validation-error-name"
    }

    label {
        htmlFor = "idea-description"
        +"Description"
        span("required-indicator") { +"*" }
    }
    textArea {
        id = "idea-description"
        name = "description"
        classes += "form-control"
        required = true
        minLength = "20"
        maxLength = "5000"
        rows = "6"
        placeholder = "Describe your contraption, how it works, and what makes it unique..."
        if (data.description != null) {
            + data.description
        }
    }
    p("validation-error-message") {
        id = "validation-error-description"
    }

    label {
        htmlFor = "idea-difficulty"
        +"Difficulty"
        span("required-indicator") { +"*" }
    }
    select {
        id = "idea-difficulty"
        name = "difficulty"
        classes += "form-control"
        required = true
        IdeaDifficulty.entries.forEach { difficulty ->
            option {
                if (data.difficulty != null) {
                    selected = data.difficulty == difficulty
                }
                value = difficulty.name
                selected = difficulty == IdeaDifficulty.MID_GAME
                +difficulty.toPrettyEnumName()
            }
        }
    }
    p("validation-error-message") {
        id = "validation-error-difficulty"
    }
}
