package app.mcorg.pipeline.project.resources

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.pipeline.Step
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.pipeline.project.GetProjectStateStep
import app.mcorg.pipeline.resources.InvalidateDemandSuppliedByStep
import app.mcorg.pipeline.resources.SupplyReach
import app.mcorg.pipeline.resources.supplyReach
import app.mcorg.pipeline.resources.invalidateDemandSuppliedBy
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.pages.ProductionsView
import app.mcorg.presentation.templated.dsl.pages.productionsFieldFragment
import app.mcorg.presentation.templated.dsl.pages.productionsPanelFragment
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getProjectProductionItemId
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
 * MCO-297 — the production editor endpoints. POST is an upsert on the item within one list — the
 * project's mode-less list, or one of its modes (V2_76_0's partial indexes; see
 * [UpsertProjectProductionStep]): adding an item that is already produced updates its rate
 * instead of duplicating the row, so inline rate edits and the add form share one
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

/**
 * Writes one produced item into one list: the given runtime mode's when the project has modes
 * (MCO-413), its one mode-less list otherwise (`modeId` null). A rate edit on the tree farm's oak row
 * is a statement about Oak Mode, and must not grow a mode-less row beside the modes.
 *
 * Two statements rather than one because the conflict target differs: each list's items are unique
 * through its own partial index (V2_76_0), and ON CONFLICT has to name the one it means.
 */
internal data class UpsertProjectProductionStep(val projectId: Int, val modeId: Int?) :
    Step<ProjectProductionInput, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: ProjectProductionInput): Result<AppFailure.DatabaseError, Int> =
        if (modeId == null) {
            DatabaseSteps.update<ProjectProductionInput>(
                sql = SafeSQL.insert("""
                    INSERT INTO project_productions (project_id, item_id, name, rate_per_hour)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (project_id, item_id) WHERE mode_id IS NULL
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
        } else {
            DatabaseSteps.update<ProjectProductionInput>(
                sql = SafeSQL.insert("""
                    INSERT INTO project_productions (project_id, mode_id, item_id, name, rate_per_hour)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (mode_id, item_id) WHERE mode_id IS NOT NULL
                    DO UPDATE SET rate_per_hour = EXCLUDED.rate_per_hour, name = EXCLUDED.name, updated_at = NOW()
                    RETURNING id
                """),
                parameterSetter = { stmt, production ->
                    stmt.setInt(1, projectId)
                    stmt.setInt(2, modeId)
                    stmt.setString(3, production.itemId)
                    stmt.setString(4, production.name)
                    stmt.setInt(5, production.ratePerHour)
                }
            ).process(input)
        }
}

/**
 * Which list a production write lands in: one of this project's own modes, or — for a project
 * without modes — its mode-less list (null).
 *
 * Required when the project has modes, and refused when it has none: either mistake would break
 * "a project with modes has no mode-less rows" (V2_76_0), which no constraint can check. A mode of
 * another project is refused like a missing one, so a write can never reach across projects.
 */
internal data class ValidateProductionModeStep(val projectId: Int) :
    Step<Parameters, AppFailure, Int?> {
    override suspend fun process(input: Parameters): Result<AppFailure, Int?> {
        val raw = input["modeId"]?.takeIf { it.isNotBlank() }
        val modes = GetProjectProductionModesStep.process(projectId)
        if (modes is Result.Failure) return modes
        val own = modes.getOrNull()!!

        if (own.isEmpty()) {
            return if (raw == null) Result.success(null)
            else Result.failure(
                AppFailure.ValidationError(listOf(ValidationFailure.InvalidValue("modeId", emptyList())))
            )
        }
        val modeId = raw?.toIntOrNull()
        return if (modeId != null && own.any { it.id == modeId }) {
            Result.success(modeId)
        } else {
            Result.failure(
                AppFailure.ValidationError(listOf(ValidationFailure.InvalidValue("modeId", own.map { it.id.toString() })))
            )
        }
    }
}

/**
 * Deletes one production row. With [invalidateInWorld] set — the farm is supplying — the stored
 * demand of what it supplied is invalidated first, in the same transaction: before, because the
 * row that says which item stopped being supplied is about to go (MCO-404); in the same
 * transaction, so the generation bump and the delete commit together, or a derivation starting in
 * between would store a plan that still counts this item as supplied (MCO-584).
 */
internal data class DeleteProjectProductionStep(val projectId: Int, val invalidateInWorld: Int? = null) :
    Step<Int, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Int> {
        // Read before the transaction opens: see InvalidateDemandSuppliedByStep's reach.
        val reach: SupplyReach? = invalidateInWorld?.let { worldId ->
            when (val read = supplyReach(worldId, projectId)) {
                is Result.Failure -> return read
                is Result.Success -> read.value
            }
        }
        return deleteWith(reach, input)
    }

    private suspend fun deleteWith(reach: SupplyReach?, input: Int): Result<AppFailure.DatabaseError, Int> =
        DatabaseSteps.transaction { connection ->
            Step<Int, AppFailure.DatabaseError, Int> { productionId ->
                if (invalidateInWorld != null && reach != null) {
                    val invalidated = InvalidateDemandSuppliedByStep(invalidateInWorld, projectId, reach, connection).process(Unit)
                    if (invalidated is Result.Failure) return@Step invalidated
                }
                DatabaseSteps.update<Int>(
                    sql = SafeSQL.delete("DELETE FROM project_productions WHERE id = ? AND project_id = ?"),
                    parameterSetter = { stmt, id ->
                        stmt.setInt(1, id)
                        stmt.setInt(2, projectId)
                    },
                    transactionConnection = connection,
                ).process(productionId)
            }
        }.process(input)
}

/**
 * Everything the production panel and chip draw for one project, in two queries: its modes, and
 * every production row (one list, or each mode's). Production has one call thread, so round trips
 * are worth counting.
 */
internal object GetProductionsViewStep : Step<Int, AppFailure.DatabaseError, ProductionsView> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, ProductionsView> =
        GetProjectProductionModesStep.process(input).flatMap { modes ->
            GetResourceProductionStep.process(input).map { rows -> ProductionsView(rows, modes) }
        }
}

/** The panel and the chip, re-rendered together after any write. */
private fun ProductionsView.panelAndChip(worldId: Int, projectId: Int): String =
    productionsPanelFragment(worldId, projectId, this) + productionsFieldFragment(worldId, projectId, this)

suspend fun ApplicationCall.handleGetProductionsPanel() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    handlePipeline(
        onSuccess = { view: ProductionsView -> respondHtml(productionsPanelFragment(worldId, projectId, view)) }
    ) {
        GetProductionsViewStep.run(projectId)
    }
}

suspend fun ApplicationCall.handleUpsertProjectProduction() {
    val parameters = receiveParameters()
    val worldId = getWorldId()
    val projectId = getProjectId()
    val validItems = GetItemsInWorldVersionStep.process(worldId).getOrNull() ?: emptyList()
    handlePipeline(
        onSuccess = { view: ProductionsView -> respondHtml(view.panelAndChip(worldId, projectId)) }
    ) {
        val input = ValidateProjectProductionInputStep(validItems).run(parameters)
        val modeId = ValidateProductionModeStep(projectId).run(parameters)
        UpsertProjectProductionStep(projectId, modeId).run(input)
        // A new produced item on an operational farm is new world supply (MCO-404). Editing a
        // rate is not — V1 supply is unbounded (MCO-287), so the rate never reached the plan —
        // but telling the two apart costs a read of what was there before, and the invalidation
        // is one statement against an action taken by hand. After the upsert is safe on its own
        // transaction: the bump can only come after the change, never before it.
        if (isOperational(projectId)) invalidateDemandSuppliedBy(worldId, projectId)
        GetProductionsViewStep.run(projectId).copy(lastModeId = modeId)
    }
}

suspend fun ApplicationCall.handleDeleteProjectProduction() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    val productionId = getProjectProductionItemId()
    handlePipeline(
        onSuccess = { view: ProductionsView -> respondHtml(view.panelAndChip(worldId, projectId)) }
    ) {
        DeleteProjectProductionStep(projectId, invalidateInWorld = worldId.takeIf { isOperational(projectId) })
            .run(productionId)
        CacheManager.onProjectProductionItemDeleted(productionId, projectId)
        GetProductionsViewStep.run(projectId)
    }
}

/**
 * Whether a production change needs to invalidate stored demand: only on a farm that is actually
 * supplying — a project that is not DONE contributes nothing to anyone's plan
 * (`GetWorldFarmSuppliesStep`), so editing its productions cannot have made a stored plan wrong.
 */
private suspend fun isOperational(projectId: Int): Boolean {
    val state = GetProjectStateStep.process(projectId)
    return state is Result.Success && state.value == ProjectState.DONE
}
