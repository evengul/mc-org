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
        val requirements = input.items.mapNotNull { (itemId, amount) ->
            byId[itemId]?.let { it to amount }
        }

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
