package app.mcorg.pipeline.project

import app.mcorg.pipeline.SafeSQL

val insertProjectQuery = SafeSQL.insert("""
    INSERT INTO projects (world_id, name, description, type, stage, location_x, location_y, location_z, location_dimension) 
    VALUES (?, ?, ?, ?, 'IDEA', 0, 0, 0, 'OVERWORLD') 
    RETURNING id
""".trimIndent())

fun getProjectsByWorldIdQuery(sortBy: String = "p.updated_at DESC, p.name ASC") = SafeSQL.select("""
    SELECT
        p.id,
        p.world_id,
        p.name,
        p.description,
        p.type,
        p.stage,
        p.location_dimension,
        p.location_x,
        p.location_y,
        p.location_z,
        p.created_at,
        p.updated_at,
        COALESCE(task_stats.tasks_total, 0) as tasks_total,
        COALESCE(task_stats.tasks_completed, 0) as tasks_completed
    FROM projects p
    LEFT JOIN (
        SELECT 
            t.project_id,
            COUNT(t.id) as tasks_total,
            COUNT(ct.id) as tasks_completed
        FROM tasks t
        LEFT JOIN (
            SELECT t2.id
            FROM tasks t2
            GROUP BY t2.id
            HAVING COUNT(*) = SUM(
                CASE
                    WHEN t2.requirement_type = 'ITEM' AND t2.requirement_item_collected >= t2.requirement_item_required_amount THEN 1
                    WHEN t2.requirement_type = 'ACTION' AND t2.requirement_action_completed = true THEN 1
                    ELSE 0
                END
            )
        ) ct ON t.id = ct.id
        GROUP BY t.project_id
    ) task_stats ON p.id = task_stats.project_id
    WHERE p.world_id = ? AND (? = '' OR LOWER(p.name) ILIKE '%' || ? || '%' OR LOWER(p.description) ILIKE '%' || ? || '%') AND (? = TRUE OR p.stage != 'COMPLETED')
    ORDER BY $sortBy 
""".trimIndent())

val getProjectByIdQuery = SafeSQL.select("""
    SELECT 
        p.id,
        p.world_id,
        p.name,
        p.description,
        p.type,
        p.stage,
        p.location_x,
        p.location_y,
        p.location_z,
        p.location_dimension,
        p.created_at,
        p.updated_at,
        COALESCE(task_stats.tasks_total, 0) as tasks_total,
        COALESCE(task_stats.tasks_completed, 0) as tasks_completed
    FROM projects p
    LEFT JOIN (
        SELECT 
            t.project_id,
            COUNT(t.id) as tasks_total,
            COUNT(ct.id) as tasks_completed
        FROM tasks t
        LEFT JOIN (
            SELECT t2.id
            FROM tasks t2
            GROUP BY t2.id
            HAVING COUNT(*) = SUM(
                CASE
                    WHEN t2.requirement_type = 'ITEM' AND t2.requirement_item_collected >= t2.requirement_item_required_amount THEN 1
                    WHEN t2.requirement_type = 'ACTION' AND t2.requirement_action_completed = true THEN 1
                    ELSE 0
                END
            )
        ) ct ON t.id = ct.id
        GROUP BY t.project_id
    ) task_stats ON p.id = task_stats.project_id
    WHERE p.id = ?
""".trimIndent())
