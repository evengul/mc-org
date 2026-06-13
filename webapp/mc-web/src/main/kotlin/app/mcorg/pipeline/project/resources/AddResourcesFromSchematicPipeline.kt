package app.mcorg.pipeline.project.resources

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.project.MapSchematicToMaterialsStep
import app.mcorg.pipeline.project.ParseSchematicStep
import app.mcorg.pipeline.project.ReceiveSchematicStep
import app.mcorg.pipeline.project.SchematicUpload
import app.mcorg.pipeline.resources.commonsteps.GetAllResourceGatheringItemsStep
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.dsl.pages.planResourceTableFragment
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import kotlinx.html.div
import kotlinx.html.stream.createHTML

/**
 * Replaces a project's resource list with the exact material counts from an uploaded
 * Litematica file — the project-page counterpart to "create project from schematic".
 *
 * A schematic is the complete material list for a build, so an upload **replaces** the
 * project's resources rather than merging. The receive/parse/map steps are shared with
 * [app.mcorg.pipeline.project.handleCreateProjectFromSchematic].
 */
suspend fun ApplicationCall.handleAddResourcesFromSchematic() {
    val user = this.getUser()
    val worldId = this.getWorldId()
    val projectId = this.getProjectId()
    val multipart = receiveMultipart()

    val items = GetItemsInWorldVersionStep.process(worldId).getOrNull() ?: emptyList()
    val previousIds = GetAllResourceGatheringItemsStep.process(projectId).getOrNull()?.map { it.id } ?: emptyList()

    handlePipeline(
        onSuccess = { resources ->
            respondHtml(
                planResourceTableFragment(worldId, projectId, resources) +
                    createHTML().div { hxOutOfBands("delete:#plan-empty-state") }
            )
        }
    ) {
        val upload = ReceiveSchematicStep.run(multipart)
        ValidateWorldMemberRole<SchematicUpload>(user, Role.ADMIN, worldId).run(upload)
        val litematica = ParseSchematicStep.run(upload)
        val materials = MapSchematicToMaterialsStep(items).run(litematica)
        ReplaceProjectResourcesStep(projectId).run(materials)

        previousIds.forEach { CacheManager.onResourceGatheringDeleted(projectId, it) }
        val resources = GetAllResourceGatheringItemsStep.run(projectId)
        resources.forEach { CacheManager.onResourceGatheringCreated(projectId, it.id) }
        resources
    }
}

private data class ReplaceProjectResourcesStep(
    val projectId: Int,
) : Step<List<Pair<Item, Int>>, AppFailure.DatabaseError, Unit> {
    override suspend fun process(input: List<Pair<Item, Int>>): Result<AppFailure.DatabaseError, Unit> {
        return DatabaseSteps.transaction { connection ->
            object : Step<List<Pair<Item, Int>>, AppFailure.DatabaseError, Unit> {
                override suspend fun process(input: List<Pair<Item, Int>>): Result<AppFailure.DatabaseError, Unit> {
                    val deleteResult = DatabaseSteps.update<List<Pair<Item, Int>>>(
                        sql = SafeSQL.delete("DELETE FROM resource_gathering WHERE project_id = ?"),
                        parameterSetter = { statement, _ -> statement.setInt(1, projectId) },
                        transactionConnection = connection
                    ).process(input)

                    if (deleteResult is Result.Failure) {
                        return Result.Failure(deleteResult.error)
                    }

                    val insertResult = DatabaseSteps.batchUpdate<Pair<Item, Int>>(
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
                    ).process(input)

                    if (insertResult is Result.Failure) {
                        return Result.Failure(insertResult.error)
                    }

                    return Result.success(Unit)
                }
            }
        }.process(input)
    }
}
