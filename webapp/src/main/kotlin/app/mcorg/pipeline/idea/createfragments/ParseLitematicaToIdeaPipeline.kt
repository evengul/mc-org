package app.mcorg.pipeline.idea.createfragments

import app.mcorg.domain.model.idea.Author
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.Litematica
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.nbt.util.LitematicaReader
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.minecraft.GetSupportedVersionsStep
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.idea.createwizard.CreateIdeaWizardData
import app.mcorg.presentation.templated.idea.createwizard.generalFields
import app.mcorg.presentation.templated.idea.createwizard.toCreateIdeaDataHolder
import app.mcorg.presentation.utils.getUser
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

    val user = this.getUser()
    val supportedVersions = GetSupportedVersionsStep.getSupportedVersions()

    val data = request.queryParameters.toCreateIdeaDataHolder(user.minecraftUsername, supportedVersions)

    executePipeline(
        onSuccess = { respondHtml(createHTML().form {
            generalFields(it.second.toValues(it.first, data))
        }) }
    ) {
        value(input)
            .step(GetContentStep)
            .step(ParseLitematicaStep)
    }
}

private fun Litematica.toValues(filename: String?, existingValues: CreateIdeaWizardData): CreateIdeaWizardData {
    return existingValues.copy(
        name = existingValues.name ?: this.name,
        description = existingValues.name ?: this.description,
        author = existingValues.author ?: Author.SingleAuthor(this.author),
        litematicaValues = (this.name.takeIf { it.isNotEmpty() } ?: filename ?: "Unnamed") to this.items.entries.sumOf { it.value },
        // TODO: Category data without category selection for the size
        itemRequirements = if (existingValues.itemRequirements == null) {
            this.items.entries.associate { Item(it.key, it.key) to it.value }
        } else {
            val items = mutableMapOf<Item, Int>()
            items.putAll(existingValues.itemRequirements)
            this.items.entries.forEach { entry ->
                val item = Item(entry.key, entry.key) // TODO: Name resolution with the new format
                items[item] = (items[item] ?: 0) + entry.value
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