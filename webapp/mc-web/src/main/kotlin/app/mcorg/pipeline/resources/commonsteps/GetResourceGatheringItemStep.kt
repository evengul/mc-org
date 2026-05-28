package app.mcorg.pipeline.resources.commonsteps

import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.resources.extractors.toResourceGatheringItem

val GetResourceGatheringItemStep = DatabaseSteps.query<Int, ResourceGatheringItem>(
    sql = SafeSQL.select(
        """
        SELECT rg.id, rg.project_id, rg.item_id, rg.name, rg.required, rg.collected,
               rg.source_type,
               p.id AS solved_project_id, p.name AS solved_project_name
        FROM resource_gathering rg
        LEFT JOIN projects p ON p.id = rg.solved_by_project_id
        WHERE rg.id = ?
        """.trimIndent()
    ),
    parameterSetter = { statement, taskId -> statement.setInt(1, taskId) },
    resultMapper = {
        it.next()
        it.toResourceGatheringItem()
    }
)
