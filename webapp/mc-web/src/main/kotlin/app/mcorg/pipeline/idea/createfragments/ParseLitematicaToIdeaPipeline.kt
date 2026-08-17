package app.mcorg.pipeline.idea.createfragments

import app.mcorg.domain.model.minecraft.Litematica
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.nbt.util.LitematicaReader
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.idea.commonsteps.GetItemsInVersionRangeStep
import app.mcorg.pipeline.project.ReceiveSchematicStep
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

/**
 * Every `.litematic` in the upload (MCO-414).
 *
 * Several, for the same reason the project import takes several: Litematica saves a selection
 * from one world, so a design with a nether side is more than one file and capturing only one of
 * them would record a material list for part of the build.
 */
private object GetContentStep : Step<MultiPartData, AppFailure, List<Pair<String?, ByteArray>>> {
    override suspend fun process(input: MultiPartData): Result<AppFailure, List<Pair<String?, ByteArray>>> {
        val files = mutableListOf<Pair<String?, ByteArray>>()
        var tooMany = false

        input.forEachPart { part ->
            if (part is PartData.FileItem && part.originalFileName?.endsWith(".litematic") == true) {
                if (files.size >= ReceiveSchematicStep.MAX_FILES) {
                    tooMany = true
                    part.release()
                } else {
                    files.add(part.originalFileName to part.provider().readRemaining().readByteArray())
                }
            } else {
                part.release()
            }
        }

        return when {
            tooMany -> Result.failure(
                AppFailure.customValidationError(
                    "litematicFile",
                    "Import at most ${ReceiveSchematicStep.MAX_FILES} files at once",
                )
            )
            files.isEmpty() -> Result.failure(
                AppFailure.customValidationError("litematicFile", "Litematica file not provided")
            )
            // This used to build the failure and then fall through to success, so an empty file
            // was read as a schematic with no materials rather than rejected.
            files.any { it.second.isEmpty() } -> Result.failure(
                AppFailure.customValidationError("litematicFile", "Litematica file is empty")
            )
            else -> Result.success(files)
        }
    }
}

/**
 * Parses each file and presents them as one material list.
 *
 * Summed per item, not concatenated: the caller renders one `<li>` per id carrying the quantity
 * in a hidden field, and the draft parser keys those by id — so two rows for the same item would
 * mean the second silently replacing the first rather than adding to it.
 */
private object ParseLitematicaStep :
    Step<List<Pair<String?, ByteArray>>, AppFailure, Pair<String?, Litematica>> {

    override suspend fun process(
        input: List<Pair<String?, ByteArray>>,
    ): Result<AppFailure, Pair<String?, Litematica>> {
        val parsed = input.map { (name, bytes) ->
            when (val compound = LitematicaReader.readLitematica(bytes)) {
                is Result.Failure -> return Result.failure(
                    AppFailure.customValidationError(
                        "litematicFile",
                        "Could not read ${name ?: "Litematica file"}",
                    )
                )
                is Result.Success -> compound.value
            }
        }

        val first = parsed.first()
        if (parsed.size == 1) return Result.success(input.first().first to first)

        val items = LinkedHashMap<String, Int>()
        parsed.forEach { file -> file.items.forEach { (id, count) -> items[id] = (items[id] ?: 0) + count } }

        return Result.success(
            input.first().first to first.copy(
                items = items,
                regions = parsed.flatMap { it.regions },
            )
        )
    }
}
