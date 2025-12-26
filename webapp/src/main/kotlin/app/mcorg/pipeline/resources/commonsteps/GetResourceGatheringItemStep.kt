package app.mcorg.pipeline.resources.commonsteps

import app.mcorg.domain.model.resources.ResourceGatheringItem
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.resources.extractors.toResourceGatheringItem

val GetResourceGatheringItemStep = DatabaseSteps.query<Int, ResourceGatheringItem>(
    sql = SafeSQL.select("SELECT id, project_id, item_id, name, required, collected from resource_gathering where id = ?"),
    parameterSetter = { statement, taskId -> statement.setInt(1, taskId) },
    resultMapper = {
        it.next()
        it.toResourceGatheringItem()
    }
)