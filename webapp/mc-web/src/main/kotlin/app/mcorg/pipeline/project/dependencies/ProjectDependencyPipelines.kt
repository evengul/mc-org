package app.mcorg.pipeline.project.dependencies

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.project.NamedProjectId
import app.mcorg.domain.model.project.ProjectDependency
import app.mcorg.domain.pipeline.PipelineScope
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.failure.ValidationFailure
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.pages.dependenciesFieldFragment
import app.mcorg.presentation.templated.dsl.pages.dependenciesPanelFragment
import app.mcorg.presentation.utils.getProjectDependencyId
import app.mcorg.presentation.utils.getProjectId
import app.mcorg.presentation.utils.getWorldId
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters

/**
 * MCO-302 — the manual dependency editor's write half.
 *
 * The read half already existed: [GetProjectDependenciesStep] and
 * [GetAvailableProjectDependenciesStep] both survived the removal of the original editor, as did
 * `ProjectDependencyItemPlugin` and `CacheManager.onProjectDependencyCreated`/`Deleted`. Only the
 * routes, these steps and the UI were gone — which left `ImportIdeaPipeline` as the sole writer
 * in `src/main`, able to assert a dependency nothing could then remove.
 *
 * **What a row here means:** someone asserting an ordering the item-level graph does not derive
 * — "dig the perimeter before building the farm inside it". It carries no item and no quantity by
 * design (see `RoadmapPage.claimText`), which is what distinguishes it from a derived edge.
 */

/**
 * Validates that the posted project is one this project may actually depend on.
 *
 * **The allowed set is [GetAvailableProjectDependenciesStep]'s answer, not a hand-rolled check**,
 * and that is the whole point: it already excludes the project itself, projects in another world,
 * projects already depended on, and — recursively, in both directions — anything that would close
 * a cycle. Re-deriving any of that here would be a second opinion that could drift from the one
 * the picker is populated from, so the form could offer an option the POST then rejected.
 */
internal data class ValidateProjectDependencyInputStep(val available: List<NamedProjectId>) :
    Step<Parameters, AppFailure.ValidationError, Int> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, Int> {
        val raw = input["dependsOnProjectId"]
        if (raw.isNullOrBlank()) {
            return Result.failure(AppFailure.ValidationError(listOf(ValidationFailure.MissingParameter("dependsOnProjectId"))))
        }
        val id = raw.toIntOrNull()
            ?: return Result.failure(
                AppFailure.ValidationError(listOf(ValidationFailure.InvalidFormat("dependsOnProjectId", "must be a project id")))
            )
        return if (available.any { it.id == id }) {
            Result.success(id)
        } else {
            // Covers "does not exist", "another world", "already a dependency" and "would make a
            // cycle" with one message, because from the caller's side they are the same answer:
            // this is not something this project may be made to wait on.
            Result.failure(
                AppFailure.ValidationError(
                    listOf(
                        ValidationFailure.CustomValidation(
                            "dependsOnProjectId",
                            "This project cannot be made to wait on that one"
                        )
                    )
                )
            )
        }
    }
}

/**
 * `ON CONFLICT DO NOTHING` against the `UNIQUE(project_id, depends_on_project_id)` from V2_7_0.
 *
 * The validation above already rejects a duplicate, so a conflict here means two requests raced.
 * Both asked for the same row to exist and it does, so the second is not an error — and it must
 * not overwrite `origin`, or re-adding a dependency an import made would quietly relabel it.
 */
internal data class AddProjectDependencyStep(val projectId: Int) :
    Step<Int, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.update<Int>(
            sql = SafeSQL.insert(
                """
                INSERT INTO project_dependencies (project_id, depends_on_project_id, origin)
                VALUES (?, ?, 'MANUAL')
                ON CONFLICT (project_id, depends_on_project_id) DO NOTHING
                """.trimIndent()
            ),
            parameterSetter = { stmt, dependsOnProjectId ->
                stmt.setInt(1, projectId)
                stmt.setInt(2, dependsOnProjectId)
            }
        ).process(input).map {
            CacheManager.onProjectDependencyCreated(projectId, input)
            input
        }
    }
}

/**
 * Keyed on `(project_id, depends_on_project_id)` rather than the row's own `id`, because that is
 * the pair `ProjectDependencyItemPlugin` verified and put on the call — the route's
 * `{dependencyId}` is the depended-on **project**, not the dependency row.
 *
 * **Deletes regardless of origin.** An imported dependency is exactly the one Even could not get
 * rid of, so refusing to delete it would reproduce the bug this issue exists to fix.
 */
internal data class DeleteProjectDependencyStep(val projectId: Int) :
    Step<Int, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Int> {
        return DatabaseSteps.update<Int>(
            sql = SafeSQL.delete("DELETE FROM project_dependencies WHERE project_id = ? AND depends_on_project_id = ?"),
            parameterSetter = { stmt, dependsOnProjectId ->
                stmt.setInt(1, projectId)
                stmt.setInt(2, dependsOnProjectId)
            }
        ).process(input).map {
            CacheManager.onProjectDependencyDeleted(projectId, input)
            input
        }
    }
}

/** Panel state is the two lists together — what is declared, and what may still be added. */
internal data class DependencyPanelData(
    val dependencies: List<ProjectDependency>,
    val available: List<NamedProjectId>,
)

/**
 * Both lists, re-read after every write so the picker cannot offer what was just added — and,
 * more importantly, so removing a dependency puts that project back in the picker without a
 * page reload.
 */
private suspend fun PipelineScope<AppFailure>.readPanel(worldId: Int, projectId: Int) =
    DependencyPanelData(
        dependencies = GetProjectDependenciesStep(projectId).run(Unit),
        available = GetAvailableProjectDependenciesStep(worldId).run(projectId),
    )

suspend fun ApplicationCall.handleGetDependenciesPanel() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    handlePipeline(
        onSuccess = { data: DependencyPanelData ->
            respondHtml(dependenciesPanelFragment(worldId, projectId, data.dependencies, data.available))
        }
    ) {
        readPanel(worldId, projectId)
    }
}

suspend fun ApplicationCall.handleAddProjectDependency() {
    val parameters = receiveParameters()
    val worldId = getWorldId()
    val projectId = getProjectId()
    handlePipeline(
        onSuccess = { data: DependencyPanelData ->
            respondHtml(
                dependenciesPanelFragment(worldId, projectId, data.dependencies, data.available) +
                    dependenciesFieldFragment(worldId, projectId, data.dependencies)
            )
        }
    ) {
        // Read inside the pipeline, so a database failure here fails the request rather than
        // silently emptying the allowed set — which would reject every valid submission as
        // "cannot depend on that one".
        val available = GetAvailableProjectDependenciesStep(worldId).run(projectId)
        val dependsOnProjectId = ValidateProjectDependencyInputStep(available).run(parameters)
        AddProjectDependencyStep(projectId).run(dependsOnProjectId)
        readPanel(worldId, projectId)
    }
}

suspend fun ApplicationCall.handleDeleteProjectDependency() {
    val worldId = getWorldId()
    val projectId = getProjectId()
    val dependsOnProjectId = getProjectDependencyId()
    handlePipeline(
        onSuccess = { data: DependencyPanelData ->
            respondHtml(
                dependenciesPanelFragment(worldId, projectId, data.dependencies, data.available) +
                    dependenciesFieldFragment(worldId, projectId, data.dependencies)
            )
        }
    ) {
        DeleteProjectDependencyStep(projectId).run(dependsOnProjectId)
        readPanel(worldId, projectId)
    }
}
