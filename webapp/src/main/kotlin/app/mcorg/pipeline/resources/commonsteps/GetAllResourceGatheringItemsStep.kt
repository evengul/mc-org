package app.mcorg.pipeline.resources.commonsteps

import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.resources.extractors.toResourceGatheringItems

val GetAllResourceGatheringItemsStep = DatabaseSteps.query<Int, List<ResourceGatheringItem>>(
    sql = SafeSQL.select("SELECT id, project_id, item_id, name, required, collected from resource_gathering where project_id = ? order by required"),
    parameterSetter = { statement, projectId -> statement.setInt(1, projectId) },
    resultMapper = { it.toResourceGatheringItems() }
)