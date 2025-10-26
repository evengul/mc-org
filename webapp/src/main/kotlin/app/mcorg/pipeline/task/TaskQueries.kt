package app.mcorg.pipeline.task

import app.mcorg.pipeline.SafeSQL

fun searchTasksQuery(sortQuery: String) = SafeSQL.select("""
    SELECT 
        t.id,
        t.project_id,
        t.name,
        t.description,
        t.stage,
        t.priority,
        t.requirement_type,
        t.requirement_item_required_amount,
        t.requirement_item_collected,
        t.requirement_action_completed,
        CASE 
            WHEN t.priority = 'CRITICAL' THEN 1
            WHEN t.priority = 'HIGH' THEN 2
            WHEN t.priority = 'MEDIUM' THEN 3
            WHEN t.priority = 'LOW' THEN 4
            ELSE 5
        END as priority_order
    FROM tasks t
    WHERE t.project_id = ?
      AND (? IS NULL OR LOWER(t.name) LIKE ? OR LOWER(t.description) LIKE ?)
      AND (? = 'ALL' OR t.priority = ?)
      AND (? = 'ALL' OR t.stage = ?)
      AND (? = 'ALL'
        OR (? = 'COMPLETED' AND ((t.requirement_type = 'ACTION' AND t.requirement_action_completed = TRUE) OR (t.requirement_type = 'ITEM' AND t.requirement_item_collected >= t.requirement_item_required_amount)))
        OR (? = 'IN_PROGRESS' AND ((t.requirement_type = 'ACTION' AND t.requirement_action_completed = FALSE) OR (t.requirement_type = 'ITEM' AND t.requirement_item_collected < t.requirement_item_required_amount)))
        )
    ORDER BY $sortQuery
""".trimIndent())

val getTaskQuery = SafeSQL.select("""
    SELECT 
        t.id,
        t.project_id,
        t.name,
        t.description,
        t.stage,
        t.priority,
        t.requirement_type,
        t.requirement_item_required_amount,
        t.requirement_item_collected,
        t.requirement_action_completed
    FROM tasks t
    WHERE t.id = ?
""".trimIndent())