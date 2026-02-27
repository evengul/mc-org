package app.mcorg.pipeline.resources.extractors

import app.mcorg.domain.model.resources.ResourceGatheringItem
import java.sql.ResultSet

fun ResultSet.toResourceGatheringItems(): List<ResourceGatheringItem> {
    return buildList {
        while (next()) {
            add(toResourceGatheringItem())
        }
    }
}

fun ResultSet.toResourceGatheringItem() = ResourceGatheringItem(
    id = getInt("id"),
    projectId = getInt("project_id"),
    itemId = getString("item_id"),
    name = getString("name"),
    required = getInt("required"),
    collected = getInt("collected"),
)