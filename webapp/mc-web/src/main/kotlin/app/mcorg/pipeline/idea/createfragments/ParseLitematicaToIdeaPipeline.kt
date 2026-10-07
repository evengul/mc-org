package app.mcorg.pipeline.idea.createfragments

import app.mcorg.domain.model.minecraft.Litematica
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.Step
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.idea.commonsteps.GetItemsInVersionRangeStep
import app.mcorg.pipeline.project.LitematicParts
import app.mcorg.pipeline.project.ReceiveSchematicStep
import app.mcorg.pipeline.project.SchematicParseGate
import app.mcorg.pipeline.project.readLitematicParts
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.plugins.UPLOAD_TOO_LARGE_MESSAGE
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.content.MultiPartData
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import kotlinx.html.ButtonType
import kotlinx.html.button
import kotlinx.html.hiddenInput
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.stream.createHTML

/**
 * Parses uploaded `.litematic` files into material rows for the create form.
 *
 * ## Whose list is being filled
 *
 * `?mode=<index>` names a **build-time** variant's own list (MCO-463) \u2014 the 4-module farm is its
 * own download, so it gets its own upload and its own rows. Without the parameter the rows fill the
 * idea's single base list, which is every idea that has no build-time variants and the only shape
 * that existed before MCO-463.
 *
 * The index is positional and re-assigned on every save (see `buildStageJson`), so it is carried
 * through here rather than interpreted: this handler only needs to know which block asked.
 */
suspend fun ApplicationCall.handleParseLitematica() {
    // Whatever the block put in its URL, reduced to digits. The value ends up in a field name, so
    // a hand-edited `?mode=x"><script>` must not reach the markup \u2014 and a non-numeric index would
    // not match a mode block on parse anyway.
    val modeIndex = request.queryParameters["mode"]?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
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
                        // Scoped by mode: the same item appears in several variants' lists, and
                        // duplicate ids would make the de-duplication lookup pick the wrong row.
                        id = if (modeIndex == null) "item-req-$itemId" else "item-req-$modeIndex-$itemId"
                        attributes["data-item-id"] = itemId
                        +"$itemName \u00d7 $qty"
                        hiddenInput {
                            name = if (modeIndex == null) {
                                "itemRequirements[$itemId]"
                            } else {
                                "modeRequirements[$modeIndex][$itemId]"
                            }
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
        val files = when (val read = input.readLitematicParts()) {
            LitematicParts.TooLarge -> return Result.failure(
                AppFailure.customValidationError(
                    "litematicFile",
                    UPLOAD_TOO_LARGE_MESSAGE,
                )
            )
            LitematicParts.TooMany -> return Result.failure(
                AppFailure.customValidationError(
                    "litematicFile",
                    "Import at most ${ReceiveSchematicStep.MAX_FILES} files at once",
                )
            )
            is LitematicParts.Read -> read.files.map { it.fileName to it.content }
        }

        return when {
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
        // Decompressing and walking an NBT tree is CPU- and allocation-heavy work on
        // attacker-supplied input, so it goes through the gate: off the call thread (MCO-345),
        // a bounded number at once and for a bounded time (MCO-426).
        val parsed = when (val read = SchematicParseGate.shared.parse(input.map { it.second })) {
            is Result.Failure -> return Result.failure(
                AppFailure.customValidationError(
                    "litematicFile",
                    read.error.failure.describe(input[read.error.index].first ?: "Litematica file"),
                )
            )
            is Result.Success -> read.value
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
