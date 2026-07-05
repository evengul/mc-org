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
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
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
)

suspend fun ApplicationCall.handleCreateProjectFromSchematic() {
    val user = this.getUser()
    val worldId = this.getWorldId()
    val multipart = receiveMultipart()

    val items = GetItemsInWorldVersionStep.process(worldId).getOrNull() ?: emptyList()

    handlePipeline(
        onSuccess = { projectId ->
            clientRedirect(Link.Worlds.world(worldId).project(projectId).to)
        }
    ) {
        val upload = ReceiveSchematicStep.run(multipart)
        ValidateWorldMemberRole<SchematicUpload>(user, Role.ADMIN, worldId).run(upload)
        val litematica = ParseSchematicStep.run(upload)
        val project = MapSchematicToProjectStep(items, upload).run(litematica)
        val projectId = CreateProjectFromSchematicStep(worldId).run(project)
        CacheManager.onProjectCreated(worldId, projectId)
        projectId
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
) : Step<Litematica, AppFailure, List<Pair<Item, Int>>> {
    override suspend fun process(input: Litematica): Result<AppFailure, List<Pair<Item, Int>>> {
        if (input.items.isEmpty()) {
            return Result.failure(
                AppFailure.customValidationError("schematicFile", "The schematic contains no materials")
            )
        }

        val byId = availableItems.associateBy { it.id }
        // A schematic stores placed block-state ids; some are gathered as a different
        // item (birch_wall_sign -> birch_sign) and some are not a material at all
        // (an extended piston's head). Normalize, then merge amounts for block ids that
        // collapse onto the same item (e.g. a build with both a sign and a wall sign).
        val requirements = input.items.entries
            .mapNotNull { (blockId, amount) -> resolveMaterial(blockId, byId)?.let { it to amount } }
            .groupBy({ it.first }, { it.second })
            .map { (item, amounts) -> item to amounts.sum() }

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

        return Result.success(requirements)
    }

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
     *    cocoa_beans), and tool/effect placements to their material (dirt_path/farmland
     *    -> dirt, redstone_wire -> redstone, wall_torch -> torch).
     * 3. *wall* variants — drop the "_wall_" infix when that yields a real item
     *    (birch_wall_sign -> birch_sign, dead_horn_coral_wall_fan -> dead_horn_coral_fan).
     * 4. Otherwise resolve by the id itself, or drop when the version has no such item.
     *
     * Truly non-obtainable blocks (budding_amethyst, portals) are intentionally left to
     * resolve to nothing and surface as a BLOCKED row rather than a wrong guess. Placed
     * *effect* blocks whose material is a separate cell already counted plus a reusable
     * tool (fire, soul_fire, bubble_column) are dropped as non-materials — see [isDropped].
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
         * Blocks that occupy a cell in a schematic but are not a material of their own —
         * the piston already accounts for its extended head and the moving block; the
         * placed-effect blocks (fire, soul_fire, bubble_column) are created in-world from
         * the block below them (a separate, already-counted cell) plus a reusable tool
         * (flint & steel / a water bucket), so they carry no material of their own.
         */
        private val NON_MATERIAL_BLOCKS = setOf(
            "minecraft:piston_head",
            "minecraft:moving_piston",
            "minecraft:fire",
            "minecraft:soul_fire",
            "minecraft:bubble_column",
        )

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
        )
    }
}

private data class MapSchematicToProjectStep(
    val availableItems: List<Item>,
    val upload: SchematicUpload,
) : Step<Litematica, AppFailure, SchematicProject> {
    override suspend fun process(input: Litematica): Result<AppFailure, SchematicProject> {
        val requirements = when (val materials = MapSchematicToMaterialsStep(availableItems).process(input)) {
            is Result.Failure -> return Result.Failure(materials.error)
            is Result.Success -> materials.value
        }

        val name = upload.providedName
            ?: input.name.takeIf { it.isNotBlank() && it != "Unnamed" }
            ?: upload.fileName?.removeSuffix(".litematic")
            ?: "Imported build"

        return Result.success(SchematicProject(name.take(100), requirements.toMap()))
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
