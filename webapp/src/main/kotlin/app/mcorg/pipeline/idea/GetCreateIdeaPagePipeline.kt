package app.mcorg.pipeline.idea

import app.mcorg.pipeline.idea.createsession.CreateIdeaWizardSession
import app.mcorg.pipeline.idea.createsession.clearWizardSession
import app.mcorg.pipeline.idea.createsession.getWizardSession
import app.mcorg.pipeline.idea.createsession.updateWizardSession
import app.mcorg.pipeline.idea.validators.ValidateAllItemRequirementsStep
import app.mcorg.pipeline.idea.validators.ValidateIdeaAuthorStep
import app.mcorg.pipeline.idea.validators.ValidateIdeaCategoryDataStep
import app.mcorg.pipeline.idea.validators.ValidateIdeaCategoryStep
import app.mcorg.pipeline.idea.validators.ValidateIdeaDescriptionStep
import app.mcorg.pipeline.idea.validators.ValidateIdeaDifficultyStep
import app.mcorg.pipeline.idea.validators.ValidateIdeaMinecraftVersionStep
import app.mcorg.pipeline.idea.validators.ValidateIdeaNameStep
import app.mcorg.pipeline.minecraft.GetSupportedVersionsStep
import app.mcorg.pipeline.notification.getUnreadNotificationsOrZero
import app.mcorg.presentation.templated.idea.createwizard.CreateIdeaStage
import app.mcorg.presentation.templated.idea.createwizard.createIdeaPage
import app.mcorg.presentation.templated.idea.createwizard.createIdeaStageContent
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.hxTarget
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import kotlinx.html.form
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleGetCreateIdeaPage() {
    val user = this.getUser()
    val notifications = getUnreadNotificationsOrZero(user.id)
    val supportedVersions = GetSupportedVersionsStep.getSupportedVersions()

    val data = getWizardSession().withNewData(this.parameters)

    updateWizardSession { data }

    if (request.headers["HX-Request"] == "true") {
        hxTarget("#create-idea-form")
        respondHtml(createHTML().form {
            createIdeaStageContent(data, supportedVersions)
        })
        return
    }

    clearWizardSession()

    respondHtml(createIdeaPage(
        user,
        notifications,
        supportedVersions,
        data
    ))
}

private suspend fun CreateIdeaWizardSession.withNewData(queryParameters: Parameters): CreateIdeaWizardSession {
    return withBasicInfo(queryParameters)
        .withAuthorInfo(queryParameters)
        .withVersion(queryParameters)
        .withItemRequirements(queryParameters)
        .withCategoryData(queryParameters)
        .copy(currentStage = queryParameters.getStage())
}

private suspend fun CreateIdeaWizardSession.withBasicInfo(queryParameters: Parameters): CreateIdeaWizardSession {
    return copy(
        name = getValue(CreateIdeaStage.BASIC_INFO, this.name) { ValidateIdeaNameStep.process(queryParameters).getOrNull() },
        description = getValue(CreateIdeaStage.BASIC_INFO, this.description) { ValidateIdeaDescriptionStep.process(queryParameters).getOrNull() },
        difficulty = getValue(CreateIdeaStage.BASIC_INFO, this.difficulty) { ValidateIdeaDifficultyStep.process(queryParameters).getOrNull() },
    )
}

private suspend fun CreateIdeaWizardSession.withAuthorInfo(queryParameters: Parameters): CreateIdeaWizardSession {
    return copy(
        author = getValue(CreateIdeaStage.AUTHOR_INFO, this.author) { ValidateIdeaAuthorStep.process(queryParameters).getOrNull() }
    )
}

private suspend fun CreateIdeaWizardSession.withVersion(queryParameters: Parameters): CreateIdeaWizardSession {
    return copy(
        versionRange = getValue(CreateIdeaStage.VERSION_COMPATIBILITY, this.versionRange) {
            ValidateIdeaMinecraftVersionStep.process(queryParameters).getOrNull()
        }
    )
}

private suspend fun CreateIdeaWizardSession.withItemRequirements(queryParameters: Parameters): CreateIdeaWizardSession {
    if (this.versionRange == null) return this

    return copy(
        itemRequirements = getValue(CreateIdeaStage.ITEM_REQUIREMENTS, this.itemRequirements) {
            ValidateAllItemRequirementsStep(this.versionRange).process(queryParameters).getOrNull()?.takeUnless { it.isEmpty() }
                ?.mapKeys { it.key.id }
        }
    )
}

private suspend fun CreateIdeaWizardSession.withCategoryData(queryParameters: Parameters): CreateIdeaWizardSession {
    val category = ValidateIdeaCategoryStep.process(queryParameters).getOrNull() ?: return this

    return copy(
        category = category,
        categoryData = getValue(CreateIdeaStage.CATEGORY_SPECIFIC_FIELDS, this.categoryData) {
            ValidateIdeaCategoryDataStep(category).process(queryParameters).getOrNull()
        }
    )
}

private suspend fun <T> CreateIdeaWizardSession.getValue(relevantStage: CreateIdeaStage, existingValue: T?, getValue: suspend () -> T?): T? {
    return if (this.currentStage == relevantStage) {
        getValue() ?: existingValue
    } else {
        existingValue ?: getValue()
    }
}

private fun Parameters.getStage(): CreateIdeaStage {
    return this["stage"]?.let {
        try {
            CreateIdeaStage.valueOf(it.uppercase())
        } catch (_: IllegalArgumentException) {
            CreateIdeaStage.BASIC_INFO
        }
    } ?: CreateIdeaStage.BASIC_INFO
}