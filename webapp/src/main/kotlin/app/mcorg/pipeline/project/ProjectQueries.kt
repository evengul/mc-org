package app.mcorg.pipeline.project

import app.mcorg.pipeline.SafeSQL

val insertProjectQuery = SafeSQL.insert("""
    INSERT INTO projects (world_id, name, description, type, stage, location_x, location_y, location_z, location_dimension) 
    VALUES (?, ?, ?, ?, 'IDEA', 0, 0, 0, 'OVERWORLD') 
    RETURNING id
""".trimIndent())

val getProjectsByWorldIdQuery = SafeSQL.select("""
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
        COALESCE(task_stats.tasks_completed, 0) as tasks_completed,
        COALESCE(task_stats.stage_progress, 0.0) as stage_progress
    FROM projects p
    LEFT JOIN (
        SELECT 
            t.project_id,
            COUNT(t.id) as tasks_total,
            COUNT(ct.task_id) as tasks_completed,
            CASE 
                WHEN COUNT(CASE WHEN t.stage = p_inner.stage THEN 1 END) = 0 THEN 0.0
                ELSE CAST(COUNT(CASE WHEN t.stage = p_inner.stage AND ct.task_id IS NOT NULL THEN 1 END) AS DECIMAL) 
                     / COUNT(CASE WHEN t.stage = p_inner.stage THEN 1 END)
            END as stage_progress
        FROM tasks t
        JOIN projects p_inner ON t.project_id = p_inner.id
        LEFT JOIN (
            SELECT tr.task_id
            FROM task_requirements tr
            GROUP BY tr.task_id
            HAVING COUNT(*) = SUM(
                CASE
                    WHEN tr.type = 'ITEM' AND tr.collected >= tr.required_amount THEN 1
                    WHEN tr.type = 'ACTION' AND tr.completed = true THEN 1
                    ELSE 0
                END
            )
        ) ct ON t.id = ct.task_id
        GROUP BY t.project_id
    ) task_stats ON p.id = task_stats.project_id
    WHERE p.world_id = ?
    ORDER BY p.id
""".trimIndent())
