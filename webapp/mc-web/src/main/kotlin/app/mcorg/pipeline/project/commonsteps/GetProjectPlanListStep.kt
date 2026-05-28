package app.mcorg.pipeline.project.commonsteps

import app.mcorg.domain.model.minecraft.Dimension
import app.mcorg.domain.model.minecraft.MinecraftLocation
import app.mcorg.domain.model.project.NamedProjectId
import app.mcorg.domain.model.project.ProjectPlanListItem
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

data class GetProjectPlanListStep(val worldId: Int) : Step<Unit, AppFailure.DatabaseError, List<ProjectPlanListItem>> {
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, List<ProjectPlanListItem>> {
        return DatabaseSteps.query<Unit, List<ProjectPlanListItem>>(
            sql = SafeSQL.select("""
                SELECT p.id, p.name, p.stage,
                       p.location_dimension, p.location_x, p.location_y, p.location_z,
                       COUNT(DISTINCT rg.id) AS resource_definition_count,
                       ARRAY(
                           SELECT pd2.depends_on_project_id
                           FROM project_dependencies pd2
                           JOIN projects bp ON bp.id = pd2.depends_on_project_id
                           WHERE pd2.project_id = p.id
                           ORDER BY bp.name
                       ) AS blocked_by_ids,
                       ARRAY(
                           SELECT bp.name
                           FROM project_dependencies pd2
                           JOIN projects bp ON bp.id = pd2.depends_on_project_id
                           WHERE pd2.project_id = p.id
                           ORDER BY bp.name
                       ) AS blocked_by_names,
                       ARRAY(
                           SELECT pd2.project_id
                           FROM project_dependencies pd2
                           JOIN projects bp ON bp.id = pd2.project_id
                           WHERE pd2.depends_on_project_id = p.id
                           ORDER BY bp.name
                       ) AS blocks_ids,
                       ARRAY(
                           SELECT bp.name
                           FROM project_dependencies pd2
                           JOIN projects bp ON bp.id = pd2.project_id
                           WHERE pd2.depends_on_project_id = p.id
                           ORDER BY bp.name
                       ) AS blocks_names
                FROM projects p
                LEFT JOIN resource_gathering rg ON rg.project_id = p.id
                WHERE p.world_id = ?
                GROUP BY p.id
                ORDER BY
                  CASE WHEN COUNT(DISTINCT rg.id) = 0 THEN 0 ELSE 1 END ASC,
                  (SELECT COUNT(*) FROM project_dependencies pd3 WHERE pd3.project_id = p.id) DESC,
                  p.name ASC
            """.trimIndent()),
            parameterSetter = { statement, _ ->
                statement.setInt(1, worldId)
            },
            resultMapper = { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(
                            ProjectPlanListItem(
                                id = resultSet.getInt("id"),
                                name = resultSet.getString("name"),
                                stage = ProjectStage.valueOf(resultSet.getString("stage")),
                                resourceDefinitionCount = resultSet.getInt("resource_definition_count"),
                                blockedByProjects = zipToNamedProjectIds(
                                    resultSet.getArray("blocked_by_ids"),
                                    resultSet.getArray("blocked_by_names")
                                ),
                                blocksProjects = zipToNamedProjectIds(
                                    resultSet.getArray("blocks_ids"),
                                    resultSet.getArray("blocks_names")
                                ),
                                location = MinecraftLocation(
                                    dimension = Dimension.valueOf(resultSet.getString("location_dimension")),
                                    x = resultSet.getInt("location_x"),
                                    y = resultSet.getInt("location_y"),
                                    z = resultSet.getInt("location_z")
                                )
                            )
                        )
                    }
                }
            }
        ).process(input)
    }
}

private fun zipToNamedProjectIds(idsArray: java.sql.Array?, namesArray: java.sql.Array?): List<NamedProjectId> {
    val ids = (idsArray?.array as? Array<*>)?.map { (it as Number).toInt() } ?: return emptyList()
    val names = (namesArray?.array as? Array<*>)?.map { it as String } ?: return emptyList()
    return ids.zip(names).map { (id, name) -> NamedProjectId(id, name) }
}
