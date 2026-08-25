package app.mcorg.pipeline.project

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.Litematica
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Step
import app.mcorg.nbt.util.LitematicaReader
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.project.resources.GetItemsInWorldVersionStep
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.plugins.MAX_SCHEMATIC_UPLOAD_BYTES
import app.mcorg.presentation.templated.dsl.Link
import app.mcorg.presentation.utils.clientRedirect
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.getWorldName
import app.mcorg.presentation.templated.dsl.pages.importReviewPage
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray

/**
 * Creates a project directly from an uploaded schematic file with its exact
 * material list — one of the "+ New project" doors. Currently .litematic only;
 * .schem/.nbt parsing is not yet supported by mc-nbt.
 */

/** One uploaded `.litematic`. */
data class SchematicFile(
    val fileName: String?,
    val content: ByteArray,
) {
    /** The name a group is qualified with: the file without its extension. */
    val stem: String get() = fileName?.removeSuffix(".litematic").orEmpty()
}

/**
 * What arrived from the upload control — one or more files, and an optional name for the project.
 *
 * Several files, because Litematica saves a selection from **one** world: a build with a nether
 * side cannot be a single file, and Seam used to model it as either several unrelated projects or
 * one project missing part of its materials (MCO-414).
 */
data class SchematicUpload(
    val files: List<SchematicFile>,
    val providedName: String?,
) {
    val fileName: String? get() = files.firstOrNull()?.fileName
}

data class SchematicProject(
    val name: String,
    val requirements: Map<Item, Int>,
    val placedCounts: Map<String, Int> = emptyMap(),
    val regions: List<ResolvedRegion> = emptyList(),
)

/**
 * A schematic's material list, plus the cell counts that did not become amounts.
 *
 * [placedCounts] is keyed by item id and only carries fluids, whose amount is capped at one
 * reusable bucket (MCO-396). It exists so the review screen can say "1 water bucket, placed
 * 4,013×" — the gathering truth and the schematic's own number, neither one hiding the other.
 * It is review-time context only and is not persisted with the project.
 */
data class SchematicMaterials(
    val requirements: List<Pair<Item, Int>>,
    val placedCounts: Map<String, Int> = emptyMap(),
    val regions: List<ResolvedRegion> = emptyList(),
)

/**
 * One subregion's resolved material list (MCO-398).
 *
 * Empty for a schematic with no regions in it. A **single** region is still reported — the
 * review screen is what decides that one group is not worth any group chrome, since
 * Litematica names a lone region after the schematic itself or leaves it "Unnamed".
 */
data class ResolvedRegion(
    val name: String,
    val requirements: List<Pair<Item, Int>>,
    val placedCounts: Map<String, Int> = emptyMap(),
    /**
     * The file this region came from, or null when the import was a single file (MCO-414).
     *
     * Null rather than "the only file" so a one-file import renders exactly as it did before
     * multi-file existed: the review screen qualifies a group by its file only when there is
     * another file to tell it apart from.
     */
    val sourceFile: String? = null,
)

/**
 * Step one of the import (MCO-303): parse the upload and show what it would create,
 * *before* creating it. Nothing is written here.
 *
 * The parsed list is not stored anywhere — the review page round-trips it as form fields
 * and [handleCreateProjectFromSchematic] builds the project from what comes back. That
 * keeps the flow free of draft rows to garbage-collect, at the cost of the list living
 * only in that one page (a reload re-posts the file, which is the honest behaviour).
 */
suspend fun ApplicationCall.handleReviewSchematic() {
    val user = this.getUser()
    val worldId = this.getWorldId()
    val multipart = receiveMultipart()

    val items = GetItemsInWorldVersionStep.process(worldId).getOrNull() ?: emptyList()

    handlePipeline(
        onSuccess = { project: SchematicProject ->
            respondHtml(
                importReviewPage(
                    user = user,
                    worldId = worldId,
                    worldName = getWorldName(worldId),
                    projectName = project.name,
                    requirements = project.requirements,
                    placedCounts = project.placedCounts,
                    regions = project.regions,
                    warnings = computeImportWarnings(worldId, project.requirements),
                )
            )
        }
    ) {
        val upload = ReceiveSchematicStep.run(multipart)
        ValidateWorldMemberRole<SchematicUpload>(user, Role.ADMIN, worldId).run(upload)
        val litematica = ParseSchematicStep.run(upload)
        MapSchematicToProjectStep(items, upload).run(litematica)
    }
}

/**
 * Step two: create the project from the **reviewed** list.
 *
 * Takes the review page's fields rather than the file. The list arrives as one
 * [ReviewedMaterialsCodec] payload carrying every row and whether the user struck it, so
 * exclusion is still just "build the rows you were told to keep".
 */
suspend fun ApplicationCall.handleCreateProjectFromSchematic() {
    val user = this.getUser()
    val worldId = this.getWorldId()
    val parameters = receiveParameters()

    val items = GetItemsInWorldVersionStep.process(worldId).getOrNull() ?: emptyList()

    handlePipeline(
        onSuccess = { projectId ->
            val target = Link.Worlds.world(worldId).project(projectId).to
            // The review page submits as a plain form (it is a page, not a fragment), so a
            // browser here needs a real redirect — an HX-Redirect header would be ignored
            // and leave a blank page.
            if (request.headers["HX-Request"] == "true") {
                clientRedirect(target)
            } else {
                response.headers.append("Location", target)
                respond(HttpStatusCode.SeeOther, "")
            }
        }
    ) {
        val project = ValidateReviewedMaterialsStep(items).run(parameters)
        ValidateWorldMemberRole<SchematicProject>(user, Role.ADMIN, worldId).run(project)
        val projectId = CreateProjectFromSchematicStep(worldId).run(project)
        CacheManager.onProjectCreated(worldId, projectId)
        projectId
    }
}

/**
 * Reads the review page's submission back into a [SchematicProject].
 *
 * The whole list arrives in one `materials` field (MCO-315); rows the user struck are carried
 * too and dropped here. Every id is re-checked against the world's catalog — the review page
 * is a form like any other, and what it sends is not trusted just because the server rendered
 * it a moment ago.
 */
internal data class ValidateReviewedMaterialsStep(val availableItems: List<Item>) :
    Step<Parameters, AppFailure, SchematicProject> {

    override suspend fun process(input: Parameters): Result<AppFailure, SchematicProject> {
        val name = input["name"]?.trim()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(
                AppFailure.customValidationError("name", "Give the project a name")
            )

        val rows = when (val decoded = ReviewedMaterialsCodec.decode(input[ReviewedMaterialsCodec.FIELD])) {
            is Result.Failure -> return Result.Failure(decoded.error)
            is Result.Success -> decoded.value
        }

        val byId = availableItems.associateBy { it.id }
        val errors = mutableListOf<ValidationFailure>()
        val requirements = mutableMapOf<Item, Int>()

        rows.forEach { row ->
            if (!row.included) return@forEach
            val item = byId[row.itemId]
            if (item == null) {
                errors.add(ValidationFailure.CustomValidation("materials", "Unknown item: ${row.itemId}"))
                return@forEach
            }
            // Summed, not assigned: with region groups (MCO-398) the same item legitimately
            // arrives once per region it appears in, and the last row would otherwise silently
            // replace the earlier ones — 200 oak planks in the shell erasing 500 in the frame.
            requirements[item] = (requirements[item] ?: 0) + row.amount
        }

        if (errors.isNotEmpty()) return Result.failure(AppFailure.ValidationError(errors))

        // Excluding every row leaves nothing to build — almost certainly a misclick, and a
        // project with no materials is indistinguishable from a blank one.
        if (requirements.isEmpty()) {
            return Result.failure(
                AppFailure.customValidationError("materials", "Keep at least one material to import")
            )
        }

        return Result.success(SchematicProject(name.take(100), requirements))
    }
}

object ReceiveSchematicStep : Step<MultiPartData, AppFailure, SchematicUpload> {

    /**
     * A sanity bound, not a product limit. The case this exists for is a build split by dimension,
     * which is two or three files; the cap is well above that and only stops a hand-rolled post
     * asking the server to parse an unbounded number of schematics. Related: MCO-421.
     */
    const val MAX_FILES = 12

    override suspend fun process(input: MultiPartData): Result<AppFailure, SchematicUpload> {
        val files = mutableListOf<SchematicFile>()
        var providedName: String? = null
        var tooMany = false
        var tooLarge = false
        // One budget for the whole upload rather than one per file: SchematicUploadLimitPlugin
        // bounds the declared Content-Length of the entire body, and a per-file cap would let a
        // chunked request spend that limit MAX_FILES times over (MCO-345).
        var remaining = MAX_SCHEMATIC_UPLOAD_BYTES

        input.forEachPart { part ->
            when {
                part is PartData.FileItem && part.originalFileName?.endsWith(".litematic") == true -> {
                    // Every matching part, not the last one: the control is `multiple` now, so a
                    // build that spans dimensions arrives as several parts under one field name
                    // (MCO-414). Overwriting here was the old single-file behaviour and would
                    // silently import only the final file.
                    if (files.size >= MAX_FILES) {
                        tooMany = true
                    } else {
                        // One byte past what is left of the budget, so an oversized body is
                        // detected without ever being held in full. The plugin catches the honest
                        // case ahead of this; this catches a chunked upload or a lying header.
                        val bytes = part.provider().readRemaining(remaining + 1).readByteArray()
                        if (bytes.size > remaining) {
                            tooLarge = true
                        } else {
                            remaining -= bytes.size
                            files.add(SchematicFile(part.originalFileName, bytes))
                        }
                    }
                    part.release()
                }
                part is PartData.FormItem && part.name == "name" -> {
                    providedName = part.value.takeIf { it.isNotBlank() }
                    part.release()
                }
                else -> part.release()
            }
        }

        return when {
            tooLarge -> Result.failure(
                AppFailure.customValidationError(
                    "schematicFile",
                    "That file is too large. Schematics must be under ${MAX_SCHEMATIC_UPLOAD_BYTES / (1024 * 1024)} MB.",
                )
            )
            tooMany -> Result.failure(
                AppFailure.customValidationError("schematicFile", "Import at most $MAX_FILES files at once")
            )
            files.isEmpty() -> Result.failure(
                AppFailure.customValidationError("schematicFile", "Provide a .litematic file")
            )
            // Named, because with several files "one of them is empty" is not actionable.
            files.any { it.content.isEmpty() } -> Result.failure(
                AppFailure.customValidationError(
                    "schematicFile",
                    files.filter { it.content.isEmpty() }
                        .joinToString(prefix = "Empty schematic file: ") { it.fileName ?: "unnamed" },
                )
            )
            else -> Result.success(SchematicUpload(files, providedName))
        }
    }
}

/** A file and what was read out of it. */
data class ParsedSchematic(val fileName: String?, val litematica: Litematica) {
    val stem: String get() = fileName?.removeSuffix(".litematic").orEmpty()
}

private fun sumRequirementLists(lists: List<List<Pair<Item, Int>>>): List<Pair<Item, Int>> {
    val byItem = LinkedHashMap<Item, Int>()
    lists.forEach { list -> list.forEach { (item, amount) -> byItem[item] = (byItem[item] ?: 0) + amount } }
    return byItem.map { (item, amount) -> item to amount }
}

private fun sumPlacedCounts(maps: List<Map<String, Int>>): Map<String, Int> {
    val total = LinkedHashMap<String, Int>()
    maps.forEach { map -> map.forEach { (id, count) -> total[id] = (total[id] ?: 0) + count } }
    return total
}

/**
 * Resolves every uploaded file and presents them as one material list (MCO-414).
 *
 * A build that spans dimensions is several files, and the review screen already has the concept
 * that fits them: MCO-398's region groups. So a file contributes its regions as groups, tagged
 * with the file they came from, and the flat list is the sum across all of them. Two files are
 * more groups — not a new idea for the screen to explain.
 *
 * A file with no regions at all (test-constructed data; real Litematica files always have at
 * least one) becomes a single group named after the file, so it is still strikeable rather than
 * dissolving into the flat list.
 *
 * Fluids keep the "one bucket per section" reading from MCO-398: each region caps its own water
 * at one bucket and the sum runs over regions, so a pond spanning the overworld and nether halves
 * of a build asks for one bucket per half — one per section you actually go and build.
 */
data class MapSchematicFilesToMaterialsStep(
    val availableItems: List<Item>,
) : Step<List<ParsedSchematic>, AppFailure, SchematicMaterials> {

    override suspend fun process(input: List<ParsedSchematic>): Result<AppFailure, SchematicMaterials> {
        if (input.isEmpty()) {
            return Result.failure(
                AppFailure.customValidationError("schematicFile", "Provide a .litematic file")
            )
        }

        val single = input.size == 1
        val perFile = input.map { parsed ->
            when (val mapped = MapSchematicToMaterialsStep(availableItems).process(parsed.litematica)) {
                is Result.Failure -> return Result.Failure(mapped.error)
                is Result.Success -> parsed to mapped.value
            }
        }

        val regions = perFile.flatMap { (parsed, materials) ->
            when {
                // One file: leave the tag off entirely, so the screen renders exactly as it did
                // before multi-file existed rather than qualifying groups against nothing.
                single -> materials.regions
                materials.regions.isEmpty() -> listOf(
                    ResolvedRegion(
                        name = parsed.stem,
                        requirements = materials.requirements,
                        placedCounts = materials.placedCounts,
                        sourceFile = parsed.stem,
                    )
                )
                else -> materials.regions.map { it.copy(sourceFile = parsed.stem) }
            }
        }

        return Result.success(
            SchematicMaterials(
                requirements = sumRequirementLists(perFile.map { it.second.requirements }),
                placedCounts = sumPlacedCounts(perFile.map { it.second.placedCounts }),
                regions = regions,
            )
        )
    }
}

object ParseSchematicStep : Step<SchematicUpload, AppFailure, List<ParsedSchematic>> {
    override suspend fun process(input: SchematicUpload): Result<AppFailure, List<ParsedSchematic>> {
        val parsed = input.files.map { file ->
            // Off the Netty event loop — see ParseLitematicaStep. Decompression and tree-walking
            // of attacker-supplied bytes must not run where it can stall unrelated requests
            // (MCO-345).
            val read = withContext(Dispatchers.IO) { LitematicaReader.readLitematica(file.content) }
            when (read) {
                // Named, since with several files "could not read the schematic file" leaves the
                // user guessing which one to re-export.
                is Result.Failure -> return Result.failure(
                    AppFailure.customValidationError(
                        "schematicFile",
                        "Could not read ${file.fileName ?: "the schematic file"}",
                    )
                )
                is Result.Success -> ParsedSchematic(file.fileName, read.value)
            }
        }
        return Result.success(parsed)
    }
}

/**
 * Resolves a parsed [Litematica]'s materials against the world version's item catalog.
 * Shared by the "create project from schematic" and "add resources from schematic" flows.
 */
data class MapSchematicToMaterialsStep(
    val availableItems: List<Item>,
) : Step<Litematica, AppFailure, SchematicMaterials> {
    override suspend fun process(input: Litematica): Result<AppFailure, SchematicMaterials> {
        if (input.items.isEmpty()) {
            return Result.failure(
                AppFailure.customValidationError("schematicFile", "The schematic contains no materials")
            )
        }

        val byId = availableItems.associateBy { it.id }

        // Per subregion where the file has them (MCO-398), so the review screen can offer a
        // decorative shell as one group to strike. A region contributing nothing after
        // resolution is dropped rather than shown as an empty group.
        val regions = input.regions.mapNotNull { region ->
            val resolved = resolve(region.items, byId)
            resolved.takeIf { it.requirements.isNotEmpty() }
                ?.let { ResolvedRegion(region.name, it.requirements, it.placedCounts) }
        }

        // The flat list is the sum over regions, not a second resolution of the flattened
        // file — otherwise the two would disagree about fluids. Resolving per region caps
        // each region's water at one bucket, so a build whose pond spans two regions asks
        // for two: one per section you actually build. Resolving the flattened file would
        // say one, and the review screen (which totals the region rows) would say two.
        // One bucket per section is the honest reading, and both paths now give it.
        //
        // A `Litematica` with no regions at all — the idea path, and directly constructed
        // test data — still resolves the flattened map, which is the pre-MCO-398 behaviour.
        val flat = if (regions.isEmpty()) {
            resolve(input.items, byId)
        } else {
            PlacedMaterials(
                requirements = sumRequirements(regions.map { it.requirements }),
                placedCounts = regions.map { it.placedCounts }.reduce { acc, next ->
                    (acc.keys + next.keys).associateWith { (acc[it] ?: 0) + (next[it] ?: 0) }
                },
            )
        }

        val requirements = flat.requirements

        if (requirements.isEmpty()) {
            return Result.failure(
                AppFailure.ValidationError(
                    listOf(
                        ValidationFailure.CustomValidation(
                            "schematicFile",
                            "No materials in this schematic exist in the world's Minecraft version"
                        )
                    )
                )
            )
        }

        return Result.success(
            SchematicMaterials(
                requirements = requirements,
                placedCounts = flat.placedCounts,
                regions = regions,
            )
        )
    }

    /**
     * One region's — or the whole file's — block counts, resolved to gathered items.
     *
     * The rules themselves live in `PlacedBlocks.kt`, shared with the idea door (MCO-308).
     * The only thing this door decides for itself is what to do with an id the version's
     * catalog does not have: drop it. A `.litematic` is often saved on a newer version than
     * the world it is being imported into, and one unrecognised id is not a reason to refuse
     * a file — the idea door, whose version range was already checked, reports it instead.
     */
    private fun resolve(counts: Map<String, Int>, byId: Map<String, Item>): PlacedMaterials =
        resolvePlacedCells(counts, byId)

    private fun sumRequirements(lists: List<List<Pair<Item, Int>>>): List<Pair<Item, Int>> =
        sumRequirementLists(lists)
}

private data class MapSchematicToProjectStep(
    val availableItems: List<Item>,
    val upload: SchematicUpload,
) : Step<List<ParsedSchematic>, AppFailure, SchematicProject> {
    override suspend fun process(input: List<ParsedSchematic>): Result<AppFailure, SchematicProject> {
        val materials = when (val mapped = MapSchematicFilesToMaterialsStep(availableItems).process(input)) {
            is Result.Failure -> return Result.Failure(mapped.error)
            is Result.Success -> mapped.value
        }

        // The first file names the project. With several files their internal names are things
        // like "Sorter (nether)" — a fragment of the build, not the build — so the name is a
        // starting point the user edits on the review screen, and picking the first is at least
        // predictable. Whatever they typed still wins.
        val first = input.first()
        val name = upload.providedName
            ?: first.litematica.name.takeIf { it.isNotBlank() && it != "Unnamed" }
            ?: first.stem.takeIf { it.isNotBlank() }
            ?: "Imported build"

        return Result.success(
            SchematicProject(
                name.take(100),
                materials.requirements.toMap(),
                materials.placedCounts,
                materials.regions,
            )
        )
    }
}

private data class CreateProjectFromSchematicStep(val worldId: Int) : Step<SchematicProject, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: SchematicProject): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.transaction { connection ->
            object : Step<SchematicProject, AppFailure.DatabaseError, Int> {
                override suspend fun process(input: SchematicProject): Result<AppFailure.DatabaseError, Int> {
                    val projectIdResult = DatabaseSteps.update<SchematicProject>(
                        sql = SafeSQL.insert("""
                            INSERT INTO projects (world_id, name, description, type, stage, state, location_x, location_y, location_z, location_dimension)
                            VALUES (?, ?, '', 'BUILDING', 'RESOURCE_GATHERING', 'ACTIVE', NULL, NULL, NULL, NULL)
                            RETURNING id
                        """.trimIndent()),
                        parameterSetter = { statement, project ->
                            statement.setInt(1, worldId)
                            statement.setString(2, project.name)
                        },
                        transactionConnection = connection
                    ).process(input)

                    if (projectIdResult is Result.Failure) {
                        return Result.Failure(projectIdResult.error)
                    }

                    val projectId = projectIdResult.getOrNull()!!

                    val requirements = DatabaseSteps.batchUpdate<Pair<Item, Int>>(
                        SafeSQL.insert("""
                            INSERT INTO resource_gathering (project_id, name, required, item_id)
                            VALUES (?, ?, ?, ?)
                        """.trimIndent()),
                        parameterSetter = { statement, requirement ->
                            statement.setInt(1, projectId)
                            statement.setString(2, requirement.first.name)
                            statement.setInt(3, requirement.second)
                            statement.setString(4, requirement.first.id)
                        },
                        transactionConnection = connection
                    ).process(input.requirements.toList())

                    if (requirements is Result.Failure) {
                        return Result.Failure(requirements.error)
                    }

                    return Result.success(projectId)
                }
            }
        }.process(input)
    }
}
