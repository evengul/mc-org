package app.mcorg.pipeline.project.dependencies

import app.mcorg.domain.model.project.NamedProjectId
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

data class GetAvailableProjectDependenciesStep(val worldId: Int) : Step<Int, AppFailure.DatabaseError, List<NamedProjectId>> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, List<NamedProjectId>> {
        return DatabaseSteps.query<Int, List<NamedProjectId>>(
            SafeSQL.with(
                """
                WITH RECURSIVE dependency_chain AS (
                    -- Base case: direct dependencies of the input project
                    SELECT depends_on_project_id as project_id
                    FROM project_dependencies
                    WHERE project_id = ?
                    
                    UNION
                    
                    -- Recursive case: transitive dependencies
                    SELECT pd.depends_on_project_id
                    FROM project_dependencies pd
                    INNER JOIN dependency_chain dc ON pd.project_id = dc.project_id
                ),
                reverse_dependency_chain AS (
                    -- Base case: projects that directly depend on the input project
                    SELECT project_id
                    FROM project_dependencies
                    WHERE depends_on_project_id = ?
                    
                    UNION
                    
                    -- Recursive case: projects that transitively depend on the input project
                    SELECT pd.project_id
                    FROM project_dependencies pd
                    INNER JOIN reverse_dependency_chain rdc ON pd.depends_on_project_id = rdc.project_id
                )
                SELECT p.id, p.name
                FROM projects p
                WHERE p.world_id = ? -- Only projects from the same world
                  AND p.id != ? -- Exclude the project itself
                  AND p.id NOT IN (
                      -- Exclude projects already directly dependent
                      SELECT depends_on_project_id
                      FROM project_dependencies
                      WHERE project_id = ?
                  )
                  AND p.id NOT IN (
                      -- Exclude projects that would make cycles (projects in the dependency chain)
                      SELECT project_id FROM dependency_chain
                  )
                  AND p.id NOT IN (
                      -- Exclude projects that would make reverse cycles (projects that depend on us)
                      SELECT project_id FROM reverse_dependency_chain
                  )
                ORDER BY p.name
                """.trimIndent()),
            parameterSetter = { statement, projectId ->
                statement.setInt(1, projectId)  // dependency_chain CTE
                statement.setInt(2, projectId)  // reverse_dependency_chain CTE
                statement.setInt(3, worldId)    // filter by world_id
                statement.setInt(4, projectId)  // exclude self
                statement.setInt(5, projectId)  // exclude direct dependencies
            },
            resultMapper = {
                buildList {
                    while (it.next()) {
                        add(
                            NamedProjectId(
                                id = it.getInt("id"),
                                name = it.getString("name")
                            )
                        )
                    }
                }
            }
        ).process(input)
    }
}
