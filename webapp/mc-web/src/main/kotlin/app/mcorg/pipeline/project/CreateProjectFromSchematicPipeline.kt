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
import kotlinx.io.readByteArray

/**
 * Creates a project directly from an uploaded schematic file with its exact
 * material list — one of the "+ New project" doors. Currently .litematic only;
 * .schem/.nbt parsing is not yet supported by mc-nbt.
 */

data class SchematicUpload(
    val fileName: String?,
    val providedName: String?,
    val content: ByteArray,
)

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
    override suspend fun process(input: MultiPartData): Result<AppFailure, SchematicUpload> {
        var content: ByteArray? = null
        var fileName: String? = null
        var providedName: String? = null

        input.forEachPart { part ->
            when {
                part is PartData.FileItem && part.originalFileName?.endsWith(".litematic") == true -> {
                    content = part.provider().readRemaining().readByteArray()
                    fileName = part.originalFileName
                }
                part is PartData.FormItem && part.name == "name" -> {
                    providedName = part.value.takeIf { it.isNotBlank() }
                    part.release()
                }
                else -> part.release()
            }
        }

        val bytes = content
        return when {
            bytes == null -> Result.failure(
                AppFailure.customValidationError("schematicFile", "Provide a .litematic file")
            )
            bytes.isEmpty() -> Result.failure(
                AppFailure.customValidationError("schematicFile", "Schematic file is empty")
            )
            else -> Result.success(SchematicUpload(fileName, providedName, bytes))
        }
    }
}

object ParseSchematicStep : Step<SchematicUpload, AppFailure, Litematica> {
    override suspend fun process(input: SchematicUpload): Result<AppFailure, Litematica> {
        return when (val parsed = LitematicaReader.readLitematica(input.content)) {
            is Result.Failure -> Result.failure(
                AppFailure.customValidationError("schematicFile", "Could not read the schematic file")
            )
            is Result.Success -> Result.success(parsed.value)
        }
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
            ResolvedMaterials(
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

    /** One region's — or the whole file's — block counts, resolved to gathered items. */
    private fun resolve(counts: Map<String, Int>, byId: Map<String, Item>): ResolvedMaterials {
        // Fluids first, because they are the one case where the cell count is not the amount
        // to gather: a bucket is reusable, so 4,013 water cells are one water bucket carried
        // and refilled. The cell count is kept as [SchematicMaterials.placedCounts] so the
        // review screen can still show it — see FLUID_PLACEMENTS.
        val fluidCells = LinkedHashMap<Item, Int>()
        counts.forEach { (blockId, amount) ->
            val bucketId = FLUID_PLACEMENTS[blockId] ?: return@forEach
            val bucket = byId[bucketId] ?: return@forEach
            fluidCells[bucket] = (fluidCells[bucket] ?: 0) + amount
        }

        // A schematic stores placed block-state ids; some are gathered as a different
        // item (birch_wall_sign -> birch_sign) and some are not a material at all
        // (an extended piston's head). Normalize, then merge amounts for block ids that
        // collapse onto the same item (e.g. a build with both a sign and a wall sign).
        val resolved = counts.entries
            .filter { (blockId, _) -> blockId !in FLUID_PLACEMENTS }
            .mapNotNull { (blockId, amount) -> resolveMaterial(blockId, byId)?.let { it to amount } }

        // One bucket per fluid, on top of any the build stores as real items.
        val byItem = LinkedHashMap<Item, Int>()
        resolved.forEach { (item, amount) -> byItem[item] = (byItem[item] ?: 0) + amount }
        fluidCells.keys.forEach { bucket -> byItem[bucket] = (byItem[bucket] ?: 0) + 1 }

        return ResolvedMaterials(
            requirements = byItem.map { (item, amount) -> item to amount },
            placedCounts = fluidCells.entries.associate { (bucket, cells) -> bucket.id to cells },
        )
    }

    private fun sumRequirements(lists: List<List<Pair<Item, Int>>>): List<Pair<Item, Int>> {
        val byItem = LinkedHashMap<Item, Int>()
        lists.forEach { list -> list.forEach { (item, amount) -> byItem[item] = (byItem[item] ?: 0) + amount } }
        return byItem.map { (item, amount) -> item to amount }
    }

    private data class ResolvedMaterials(
        val requirements: List<Pair<Item, Int>>,
        val placedCounts: Map<String, Int>,
    )

    /**
     * Resolves a schematic block-state id to the item a player actually gathers, or
     * null when the cell is not a material (dropped).
     *
     * Order:
     * 1. [isDropped] — placed forms that are not a material of their own (an extended
     *    piston's head, potted plants counted as the pot+plant elsewhere, candle cakes,
     *    silverfish-infested blocks).
     * 2. [REDIRECTS] — explicit block -> item for placed forms whose gathered item has a
     *    different id: crops to their seed/produce (carrots -> carrot, cocoa ->
     *    cocoa_beans), tool/effect placements to their material (dirt_path/farmland
     *    -> dirt, redstone_wire -> redstone, wall_torch -> torch), and filled block states
     *    to the block you place (lava_cauldron -> cauldron).
     * 3. *wall* variants — drop the "_wall_" infix when that yields a real item
     *    (birch_wall_sign -> birch_sign, dead_horn_coral_wall_fan -> dead_horn_coral_fan).
     * 4. Otherwise resolve by the id itself, or drop when the version has no such item.
     *
     * Fluids never reach here — they are handled ahead of it, since their amount is capped
     * at one bucket rather than taken from the cell count (see [FLUID_PLACEMENTS]).
     *
     * Truly non-obtainable blocks (budding_amethyst) are intentionally left to resolve to
     * nothing and surface as a BLOCKED row rather than a wrong guess. Placed *effect* blocks
     * whose material is a separate cell already counted plus a reusable tool (fire,
     * soul_fire, bubble_column, the portals) are dropped as non-materials — see [isDropped].
     */
    private fun resolveMaterial(blockId: String, byId: Map<String, Item>): Item? {
        if (isDropped(blockId)) return null
        REDIRECTS[blockId]?.let { target -> byId[target]?.let { return it } }
        if ("_wall_" in blockId) {
            byId[blockId.replace("_wall_", "_")]?.let { return it }
        }
        return byId[blockId]
    }

    private fun isDropped(blockId: String): Boolean {
        if (blockId in NON_MATERIAL_BLOCKS) return true
        val name = blockId.substringAfter(':')
        return name.startsWith("potted_") || name.startsWith("infested_") || name.endsWith("candle_cake")
    }

    companion object {
        /**
         * Placed block-state ids whose gathered item has a *different* id. Crops resolve
         * to the seed/produce you plant or harvest; tool/effect placements resolve to the
         * material left behind. The target must still exist in the version's catalog to be
         * used (else the block falls through to its own id / a BLOCKED row).
         */
        private val REDIRECTS = mapOf(
            // crops / growth -> seed or produce item
            "minecraft:carrots" to "minecraft:carrot",
            "minecraft:potatoes" to "minecraft:potato",
            "minecraft:beetroots" to "minecraft:beetroot_seeds",
            "minecraft:cocoa" to "minecraft:cocoa_beans",
            "minecraft:melon_stem" to "minecraft:melon_seeds",
            "minecraft:attached_melon_stem" to "minecraft:melon_seeds",
            "minecraft:pumpkin_stem" to "minecraft:pumpkin_seeds",
            "minecraft:attached_pumpkin_stem" to "minecraft:pumpkin_seeds",
            "minecraft:cave_vines" to "minecraft:glow_berries",
            "minecraft:cave_vines_plant" to "minecraft:glow_berries",
            "minecraft:kelp_plant" to "minecraft:kelp",
            "minecraft:bamboo_sapling" to "minecraft:bamboo",
            "minecraft:sweet_berry_bush" to "minecraft:sweet_berries",
            "minecraft:tall_seagrass" to "minecraft:seagrass",
            "minecraft:twisting_vines_plant" to "minecraft:twisting_vines",
            "minecraft:weeping_vines_plant" to "minecraft:weeping_vines",
            "minecraft:big_dripleaf_stem" to "minecraft:big_dripleaf",
            "minecraft:pitcher_crop" to "minecraft:pitcher_pod",
            "minecraft:torchflower_crop" to "minecraft:torchflower_seeds",
            // placed tool/effect forms -> the material gathered
            "minecraft:dirt_path" to "minecraft:dirt",
            "minecraft:farmland" to "minecraft:dirt",
            "minecraft:redstone_wire" to "minecraft:redstone",
            "minecraft:tripwire" to "minecraft:string",
            "minecraft:suspicious_sand" to "minecraft:sand",
            "minecraft:suspicious_gravel" to "minecraft:gravel",
            "minecraft:wall_torch" to "minecraft:torch",
            // A filled cauldron is a block *state* of the cauldron, not an item of its own
            // (there is no lava_cauldron item). What you gather is the cauldron; the bucket
            // that fills it is a reusable tool, like the flint & steel behind a fire cell.
            "minecraft:lava_cauldron" to "minecraft:cauldron",
            "minecraft:water_cauldron" to "minecraft:cauldron",
            "minecraft:powder_snow_cauldron" to "minecraft:cauldron",
        )
    }
}

private data class MapSchematicToProjectStep(
    val availableItems: List<Item>,
    val upload: SchematicUpload,
) : Step<Litematica, AppFailure, SchematicProject> {
    override suspend fun process(input: Litematica): Result<AppFailure, SchematicProject> {
        val materials = when (val mapped = MapSchematicToMaterialsStep(availableItems).process(input)) {
            is Result.Failure -> return Result.Failure(mapped.error)
            is Result.Success -> mapped.value
        }

        val name = upload.providedName
            ?: input.name.takeIf { it.isNotBlank() && it != "Unnamed" }
            ?: upload.fileName?.removeSuffix(".litematic")
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
