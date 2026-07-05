package app.mcorg.pipeline.resources.commonsteps

import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.resources.extractors.toResourceGatheringItems

val GetAllResourceGatheringItemsStep = DatabaseSteps.query<Int, List<ResourceGatheringItem>>(
    sql = SafeSQL.select(
        """
        SELECT rg.id, rg.project_id, rg.item_id, rg.name, rg.required,
               COALESCE(rgp.collected, 0) AS collected,
               rg.source_type,
               p.id AS solved_project_id, p.name AS solved_project_name
        FROM resource_gathering rg
        LEFT JOIN resource_gathering_progress rgp
               ON rgp.project_id = rg.project_id AND rgp.item_id = rg.item_id
        LEFT JOIN projects p ON p.id = rg.solved_by_project_id
        WHERE rg.project_id = ?
        ORDER BY rg.required DESC, rg.name
        """.trimIndent()
    ),
    parameterSetter = { statement, projectId -> statement.setInt(1, projectId) },
    resultMapper = { it.toResourceGatheringItems() }
)
