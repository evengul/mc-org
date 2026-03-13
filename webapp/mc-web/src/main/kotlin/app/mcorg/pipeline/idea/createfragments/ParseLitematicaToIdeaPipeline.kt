package app.mcorg.pipeline.idea.createfragments

import app.mcorg.domain.model.minecraft.Litematica
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.nbt.util.LitematicaReader
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.idea.commonsteps.GetItemsInVersionRangeStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.utils.io.readRemaining
import kotlinx.html.ButtonType
import kotlinx.html.button
import kotlinx.html.hiddenInput
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.stream.createHTML
import kotlinx.io.readByteArray

suspend fun ApplicationCall.handleParseLitematica() {
    val input = receiveMultipart()

    handlePipeline(
        onSuccess = { (_, litematica) ->
            val allItems = GetItemsInVersionRangeStep.process(MinecraftVersionRange.Unbounded)
                .getOrNull()
                .orEmpty()
                .associateBy { it.id }

            val html = litematica.items.entries
                .sortedByDescending { it.value }
                .joinToString("") { (itemId, qty) ->
                    val itemName = allItems[itemId]?.name ?: itemId
                    createHTML().li("item-req") {
                        id = "item-req-$itemId"
                        +"$itemName \u00d7 $qty"
                        hiddenInput {
                            name = "itemRequirements[$itemId]"
                            value = qty.toString()
                        }
                        button(classes = "btn btn--ghost btn--sm") {
                            type = ButtonType.button
                            attributes["onclick"] = "this.closest('li').remove()"
                            +"Remove"
                        }
                    }
                }

            respondHtml(html)
        }
    ) {
        val content = GetContentStep.run(input)
        ParseLitematicaStep.run(content)
    }
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
