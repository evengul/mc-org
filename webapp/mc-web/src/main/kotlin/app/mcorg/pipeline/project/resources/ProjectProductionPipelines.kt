package app.mcorg.pipeline.project.resources

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.project.ProjectProduction
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.pipeline.Step
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.pipeline.project.GetProjectStateStep
import app.mcorg.pipeline.resources.invalidateDemandSuppliedBy
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.pipeline.world.ValidateWorldMemberRole
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.pages.productionsFieldFragment
import app.mcorg.presentation.templated.dsl.pages.productionsPanelFragment
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getProjectProductionItemId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters

/** Validated input for [UpsertProjectProductionStep]. */
data class ProjectProductionInput(
    val itemId: String,
    val name: String,
    val ratePerHour: Int,
)

/**
 * MCO-297 — the production editor endpoints. POST is an upsert on (project_id, item_id)
 * (unique constraint from V2_50_0): adding an item that is already produced updates its
 * rate instead of duplicating the row, so inline rate edits and the add form share one
 * endpoint. Rate is optional and display-only under unbounded-supply V1; 0 means "unknown".
 */
internal data class ValidateProjectProductionInputStep(val validItems: List<Item>) :
    Step<Parameters, AppFailure.ValidationError, ProjectProductionInput> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, ProjectProductionInput> {
        val itemId = ValidationSteps.required("itemId") { it }.process(input)
            .flatMap { id ->
                ValidationSteps.validateAllowedValues(
                    "itemId",
                    validItems.map { item -> item.id },
                    { it },
                    ignoreCase = false
                ).process(id)
            }

        val rateRaw = input["ratePerHour"]
        val rate: Result<ValidationFailure, Int> = if (rateRaw.isNullOrBlank()) {
            Result.success(0)
        } else {
            val parsed = rateRaw.toIntOrNull()
            if (parsed != null && parsed >= 0) Result.success(parsed)
            else Result.failure(ValidationFailure.InvalidFormat("ratePerHour", "must be a non-negative integer"))
        }

        val errors = mutableListOf<ValidationFailure>()
        if (itemId is Result.Failure) errors.add(itemId.error)
        if (rate is Result.Failure) errors.add(rate.error)

        return if (errors.isEmpty()) {
            val id = (itemId as Result.Success).value
            val name = validItems.first { it.id == id }.name
            Result.success(ProjectProductionInput(id, name, (rate as Result.Success).value))
        } else {
            Result.failure(AppFailure.ValidationError(errors))
        }
    }
}

internal data class UpsertProjectProductionStep(val projectId: Int) :
    Step<ProjectProductionInput, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: ProjectProductionInput): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.update<ProjectProductionInput>(
            sql = SafeSQL.insert("""
                INSERT INTO project_productions (project_id, item_id, name, rate_per_hour)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (project_id, item_id)
                DO UPDATE SET rate_per_hour = EXCLUDED.rate_per_hour, name = EXCLUDED.name, updated_at = NOW()
                RETURNING id
            """),
            parameterSetter = { stmt, production ->
                stmt.setInt(1, projectId)
                stmt.setString(2, production.itemId)
                stmt.setString(3, production.name)
                stmt.setInt(4, production.ratePerHour)
            }
        ).process(input)
    }
}

internal data class DeleteProjectProductionStep(val projectId: Int) :
    Step<Int, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.update<Int>(
            sql = SafeSQL.delete("DELETE FROM project_productions WHERE id = ? AND project_id = ?"),
            parameterSetter = { stmt, productionId ->
                stmt.setInt(1, productionId)
                stmt.setInt(2, projectId)
            }
        ).process(input)
    }
}

private suspend fun ApplicationCall.isWorldAdmin(worldId: Int): Boolean =
    ValidateWorldMemberRole<Unit>(getUser(), Role.ADMIN, worldId).process(Unit) is Result.Success

suspend fun ApplicationCall.handleGetProductionsPanel() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    val isAdmin = isWorldAdmin(worldId)
    handlePipeline(
        onSuccess = { productions: List<ProjectProduction> ->
            respondHtml(productionsPanelFragment(worldId, projectId, productions, isAdmin))
        }
    ) {
        GetResourceProductionStep.run(projectId)
    }
}

suspend fun ApplicationCall.handleUpsertProjectProduction() {
    val parameters = receiveParameters()
    val worldId = getWorldId()
    val projectId = getProjectId()
    val isAdmin = isWorldAdmin(worldId)
    val validItems = GetItemsInWorldVersionStep.process(worldId).getOrNull() ?: emptyList()
    handlePipeline(
        onSuccess = { productions: List<ProjectProduction> ->
            respondHtml(
                productionsPanelFragment(worldId, projectId, productions, isAdmin) +
                    productionsFieldFragment(worldId, projectId, productions, isAdmin)
            )
        }
    ) {
        val input = ValidateProjectProductionInputStep(validItems).run(parameters)
        UpsertProjectProductionStep(projectId).run(input)
        // A new produced item on an operational farm is new world supply (MCO-404). Editing a
        // rate is not — V1 supply is unbounded (MCO-287), so the rate never reached the plan —
        // but telling the two apart costs a read of what was there before, and the invalidation
        // is a single DELETE against an action taken by hand.
        invalidateDemandIfOperational(worldId, projectId)
        GetResourceProductionStep.run(projectId)
    }
}

suspend fun ApplicationCall.handleDeleteProjectProduction() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    val productionId = getProjectProductionItemId()
    val isAdmin = isWorldAdmin(worldId)
    handlePipeline(
        onSuccess = { productions: List<ProjectProduction> ->
            respondHtml(
                productionsPanelFragment(worldId, projectId, productions, isAdmin) +
                    productionsFieldFragment(worldId, projectId, productions, isAdmin)
            )
        }
    ) {
        // Before the delete: afterwards the row that says which item stopped being supplied is
        // gone (MCO-404).
        invalidateDemandIfOperational(worldId, projectId)
        DeleteProjectProductionStep(projectId).run(productionId)
        GetResourceProductionStep.run(projectId)
    }
}

/**
 * Invalidates stored demand for a production change, but only on a farm that is actually
 * supplying — a project that is not DONE contributes nothing to anyone's plan
 * (`GetWorldFarmSuppliesStep`), so editing its productions cannot have made a stored plan wrong.
 */
private suspend fun invalidateDemandIfOperational(worldId: Int, projectId: Int) {
    val state = GetProjectStateStep.process(projectId)
    if (state is Result.Success && state.value == ProjectState.DONE) {
        invalidateDemandSuppliedBy(worldId, projectId)
    }
}
