package app.mcorg.pipeline.idea.createfragments

import app.mcorg.domain.model.minecraft.Litematica
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.nbt.util.LitematicaReader
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.idea.commonsteps.GetItemsInVersionRangeStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.plugins.MAX_SCHEMATIC_UPLOAD_BYTES
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        var tooLarge = false

        input.forEachPart { part ->
            if (part is PartData.FileItem && part.originalFileName?.endsWith(".litematic") == true) {
                // One byte past the limit, so an oversized body is detectable without ever being
                // held in full. The Content-Length plugin on this route catches the honest case;
                // this catches a chunked upload or a lying header (MCO-345).
                val bytes = part.provider().readRemaining(MAX_SCHEMATIC_UPLOAD_BYTES + 1).readByteArray()
                if (bytes.size > MAX_SCHEMATIC_UPLOAD_BYTES) {
                    tooLarge = true
                } else {
                    content = bytes
                    name = part.originalFileName
                }
                part.release()
            } else {
                part.release()
            }
        }

        val bytes = content
        return when {
            tooLarge -> Result.failure(
                AppFailure.customValidationError(
                    "litematicFile",
                    "That file is too large. Schematics must be under ${MAX_SCHEMATIC_UPLOAD_BYTES / (1024 * 1024)} MB.",
                )
            )
            bytes == null -> Result.failure(
                AppFailure.customValidationError("litematicFile", "Litematica file not provided")
            )
            // Previously computed and thrown away — the empty case fell through to success.
            bytes.isEmpty() -> Result.failure(
                AppFailure.customValidationError("litematicFile", "Litematica file is empty")
            )
            else -> Result.success(name to bytes)
        }
    }
}

private object ParseLitematicaStep : Step<Pair<String?, ByteArray>, AppFailure, Pair<String?, Litematica>> {
    override suspend fun process(input: Pair<String?, ByteArray>): Result<AppFailure, Pair<String?, Litematica>> {
        // Decompressing and walking an NBT tree is CPU- and allocation-heavy work on
        // attacker-supplied input. Off the Netty event loop, so a slow file costs one IO thread
        // rather than a worker that every other in-flight request is sharing (MCO-345).
        val parsed = withContext(Dispatchers.IO) { LitematicaReader.readLitematica(input.second) }
        return when (parsed) {
            is Result.Failure -> parsed.mapError { AppFailure.customValidationError("litematicFile", "Could not read Litematica file") }
            is Result.Success -> parsed.mapSuccess { input.first to it }
        }
    }
}
