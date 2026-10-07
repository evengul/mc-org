package app.mcorg.pipeline.project.resources

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.project.ProjectProduction
import app.mcorg.domain.model.project.ProjectProductionMode
import app.mcorg.pipeline.Step
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.pipeline.project.GetProjectStateStep
import app.mcorg.pipeline.resources.InvalidateDemandSuppliedByStep
import app.mcorg.pipeline.resources.invalidateDemandSuppliedBy
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.pages.ModeSwitch
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
 * MCO-297 — the production editor endpoints. POST is an upsert on the item within the list the
 * project supplies from (V2_76_0's partial indexes; see [UpsertProjectProductionStep]): adding an item that is already produced updates its
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

/**
 * Writes one produced item into the list the project supplies from: its active mode when it has
 * modes (MCO-413), its one mode-less list otherwise. A rate edit on the tree farm while it runs oak
 * is a statement about oak mode, and must not grow a mode-less row beside the modes.
 *
 * Two statements rather than one because the conflict target differs: each list's items are unique
 * through its own partial index (V2_76_0), and ON CONFLICT has to name the one it means.
 */
internal data class UpsertProjectProductionStep(val projectId: Int) :
    Step<ProjectProductionInput, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: ProjectProductionInput): Result<AppFailure.DatabaseError, Int> {
        val modes = GetProjectProductionModesStep.process(projectId)
        if (modes is Result.Failure) return modes
        val activeModeId = modes.getOrNull()!!.firstOrNull { it.active }?.id

        return if (activeModeId == null) {
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
                    stmt.setInt(2, activeModeId)
                    stmt.setString(3, production.itemId)
                    stmt.setString(4, production.name)
                    stmt.setInt(5, production.ratePerHour)
                }
            ).process(input)
        }
    }
}

/** Parses `modeId` and checks it is one of this project's own modes. */
internal data class ValidateProductionModeStep(val projectId: Int) :
    Step<Parameters, AppFailure, Int> {
    override suspend fun process(input: Parameters): Result<AppFailure, Int> {
        val modeId = input["modeId"]?.toIntOrNull()
            ?: return Result.failure(
                AppFailure.ValidationError(listOf(ValidationFailure.InvalidFormat("modeId", "must be a mode id")))
            )
        val modes = GetProjectProductionModesStep.process(projectId)
        if (modes is Result.Failure) return modes
        val own = modes.getOrNull()!!
        return if (own.any { it.id == modeId }) {
            Result.success(modeId)
        } else {
            Result.failure(
                AppFailure.ValidationError(listOf(ValidationFailure.InvalidValue("modeId", own.map { it.id.toString() })))
            )
        }
    }
}

/**
 * Makes the given mode the project's one active mode.
 *
 * Two statements in one transaction rather than a single `SET active = (id = ?)`: the one-active
 * rule is a partial unique index, and Postgres checks a non-deferrable index row by row, so a single
 * UPDATE that reaches the new mode before the old one fails on a state that would never commit.
 *
 * The project's mode rows are locked first. Two members switching the same farm at once would
 * otherwise interleave under READ COMMITTED: the second clear waits on the first's row, never sees
 * the mode the first just turned on, and the second set then trips the index. With the lock the
 * second switch starts after the first commits, and simply wins.
 *
 * Exactly one row must turn on, or the whole switch rolls back — a farm with modes and none
 * running would fall through to nothing in [GetResourceProductionStep]'s view.
 */
internal data class SwitchProductionModeStep(val projectId: Int) :
    Step<Int, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Int> =
        DatabaseSteps.transaction { connection ->
            object : Step<Int, AppFailure.DatabaseError, Int> {
                override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Int> {
                    val locked = DatabaseSteps.query<Int, Int>(
                        sql = SafeSQL.select("SELECT id FROM project_production_modes WHERE project_id = ? FOR UPDATE"),
                        parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
                        resultMapper = { rs -> var n = 0; while (rs.next()) n++; n },
                        transactionConnection = connection,
                    ).process(input)
                    if (locked is Result.Failure) return locked
                    val cleared = DatabaseSteps.update<Int>(
                        sql = SafeSQL.update("UPDATE project_production_modes SET active = FALSE WHERE project_id = ? AND active"),
                        parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) },
                        connection,
                    ).process(input)
                    if (cleared is Result.Failure) return cleared
                    val set = DatabaseSteps.update<Int>(
                        sql = SafeSQL.update("UPDATE project_production_modes SET active = TRUE WHERE id = ? AND project_id = ?"),
                        parameterSetter = { stmt, modeId ->
                            stmt.setInt(1, modeId)
                            stmt.setInt(2, projectId)
                        },
                        connection,
                    ).process(input)
                    return if (set is Result.Success && set.value != 1) Result.failure(AppFailure.DatabaseError.NotFound) else set
                }
            }
        }.process(input)
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
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Int> =
        DatabaseSteps.transaction { connection ->
            Step<Int, AppFailure.DatabaseError, Int> { productionId ->
                if (invalidateInWorld != null) {
                    val invalidated = InvalidateDemandSuppliedByStep(invalidateInWorld, projectId, connection).process(Unit)
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
 * Everything the production panel and chip draw for one project, in two queries: a project
 * without modes reads its one list, a project with modes reads every mode's rows and takes the
 * running mode's from them. Production has one call thread, so round trips are worth counting.
 */
internal object GetProductionsViewStep : Step<Int, AppFailure.DatabaseError, ProductionsView> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, ProductionsView> =
        GetProjectProductionModesStep.process(input).flatMap { modes ->
            val running = modes.firstOrNull { it.active }
            if (modes.isEmpty()) {
                GetResourceProductionStep.process(input).map { ProductionsView(it) }
            } else {
                GetModeProductionsStep.process(input).map { all ->
                    ProductionsView(all.filter { it.modeId == running?.id }, modes, all)
                }
            }
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

/**
 * Switches which runtime mode a farm runs in (MCO-413). The rates were copied from the design at
 * import, so nothing is re-typed: the switch only changes which of them supply.
 *
 * The response carries what it switched *from*, so the panel can say what changed for other plans
 * and offer the way back (frame 1a's note).
 */
suspend fun ApplicationCall.handleSwitchProductionMode() {
    val parameters = receiveParameters()
    val worldId = getWorldId()
    val projectId = getProjectId()
    handlePipeline(
        onSuccess = { view: ProductionsView -> respondHtml(view.panelAndChip(worldId, projectId)) }
    ) {
        val modeId = ValidateProductionModeStep(projectId).run(parameters)
        val before = GetProjectProductionModesStep.run(projectId).firstOrNull { it.active }
        SwitchProductionModeStep(projectId).run(modeId)
        // Once, after the switch, covers both directions: invalidation reads every mode's items, so
        // the plans that gathered what the old mode made are caught alongside the new mode's.
        val isDone = GetProjectStateStep.run(projectId) == ProjectState.DONE
        if (isDone) invalidateDemandSuppliedBy(worldId, projectId)
        GetProductionsViewStep.run(projectId).copy(switched = before?.let { ModeSwitch(it, isDone) })
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
        UpsertProjectProductionStep(projectId).run(input)
        // A new produced item on an operational farm is new world supply (MCO-404). Editing a
        // rate is not — V1 supply is unbounded (MCO-287), so the rate never reached the plan —
        // but telling the two apart costs a read of what was there before, and the invalidation
        // is one statement against an action taken by hand. After the upsert is safe on its own
        // transaction: the bump can only come after the change, never before it.
        if (isOperational(projectId)) invalidateDemandSuppliedBy(worldId, projectId)
        GetProductionsViewStep.run(projectId)
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
