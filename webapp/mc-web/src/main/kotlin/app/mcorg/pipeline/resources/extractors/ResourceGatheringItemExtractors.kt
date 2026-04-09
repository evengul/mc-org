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

fun ResultSet.toResourceGatheringItem(): ResourceGatheringItem {
    val sourceType = getString("source_type")
    val solvedProjectId = getInt("solved_project_id").takeUnless { wasNull() }
    val solvedProjectName = getString("solved_project_name")
    val solvedByProject = if (solvedProjectId != null && solvedProjectName != null) {
        solvedProjectId to solvedProjectName
    } else {
        null
    }
    return ResourceGatheringItem(
        id = getInt("id"),
        projectId = getInt("project_id"),
        itemId = getString("item_id"),
        name = getString("name"),
        required = getInt("required"),
        collected = getInt("collected"),
        solvedByProject = solvedByProject,
        sourceType = sourceType,
    )
}
