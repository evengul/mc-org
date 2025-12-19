package app.mcorg.pipeline.idea.createfragments

import app.mcorg.domain.model.idea.Author
import app.mcorg.domain.model.minecraft.Litematica
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.nbt.util.LitematicaReader
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.idea.createsession.CreateIdeaWizardSession
import app.mcorg.pipeline.idea.createsession.getWizardSession
import app.mcorg.pipeline.idea.createsession.updateWizardSession
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.idea.createwizard.generalFields
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.utils.io.readRemaining
import kotlinx.html.form
import kotlinx.html.stream.createHTML
import kotlinx.io.readByteArray

suspend fun ApplicationCall.handleParseLitematica() {
    val input = receiveMultipart()

    executePipeline(
        onSuccess = { (filename, litematica) ->
            updateWizardSession { withLitematicaData(litematica, filename) }
            val updatedSession = getWizardSession()
            respondHtml(createHTML().form {
                generalFields(updatedSession)
            })
        }
    ) {
        value(input)
            .step(GetContentStep)
            .step(ParseLitematicaStep)
    }
}

private fun CreateIdeaWizardSession.withLitematicaData(litematica: Litematica, filename: String?): CreateIdeaWizardSession {
    return this.copy(
        name = this.name ?: litematica.name.takeUnless { it.isBlank() } ?: filename?.removeSuffix(".litematic"),
        description = this.description ?: litematica.description,
        author = this.author ?: Author.SingleAuthor(litematica.author),
        litematicaFileName = filename?.removeSuffix(".litematic"),
        litematicaUploadedAt = System.currentTimeMillis(),
        itemRequirements = if (this.itemRequirements == null) {
            litematica.items.entries.associate { it.key to it.value }
        } else {
            val items = mutableMapOf<String, Int>()
            items.putAll(this.itemRequirements)
            litematica.items.entries.forEach { entry ->
                items[entry.key] = (items[entry.key] ?: 0) + entry.value
            }
            items
        }
    )
}

private object GetContentStep : Step<MultiPartData, AppFailure, Pair<String?, ByteArray>> {
    override suspend fun process(input: MultiPartData): Result<AppFailure, Pair<String?, ByteArray>> {
        var content: ByteArray? = null
        var name: String? = null
        input.forEachPart { part ->
            if (part is PartData.FileItem && part.originalFileName?.endsWith(".litematic") == true) {
                content = part.provider().readRemaining().readByteArray()
                name = part.originalFileName
            } else {
                part.dispose()
            }
        }
        return if (content != null) {
            if (content.isEmpty()) {
                Result.failure(AppFailure.customValidationError("litematicFile", "Litematica file is empty"))
            }
            Result.success(name to content)
        } else {
            Result.failure(AppFailure.customValidationError("litematicFile", "Litematica file not provided"))
        }
    }
}

private object ParseLitematicaStep : Step<Pair<String?, ByteArray>, AppFailure, Pair<String?, Litematica>> {
    override suspend fun process(input: Pair<String?, ByteArray>): Result<AppFailure, Pair<String?, Litematica>> {
        return when (val compound = LitematicaReader.readLitematica(input.second)) {
            is Result.Failure -> compound.mapError { AppFailure.customValidationError("litematicFile", "Could not read Litematica file") }
            is Result.Success -> compound.mapSuccess { input.first to it }
        }
    }
}