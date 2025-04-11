package app.mcorg.pipeline.project

import java.sql.Connection

fun getProjectProgress(connection: Connection, projectId: Int): Double {
    connection.prepareStatement("""
            select round((SUM(t.completed)::float / (select count(id) from task t where project_id = ?)::float)::numeric, 3)
    from (select done::float / needed::float as completed from task where project_id = ?) t;
        """.trimIndent())
        .apply { setInt(1, projectId); setInt(2, projectId) }
        .executeQuery()
        .use { rs ->
            if (rs.next()) {
                return rs.getDouble(1)
            }
        }
    return 0.0
}