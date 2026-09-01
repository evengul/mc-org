package app.mcorg.pipeline.world.commonsteps

/**
 * The SELECT columns filling [app.mcorg.domain.model.world.WorldProjectTally] — one count
 * per lifecycle state a project can be on the board in. CANCELLED and ARCHIVED are
 * deliberately absent: they are shelved, and the Worlds page presents the tally as parts
 * that sum to a whole.
 *
 * [projects] is the alias the caller joined the `projects` table under (a literal in the
 * query's own source, never user input). Counts are DISTINCT so the numbers survive a
 * query that fans out over a second join.
 */
fun projectTallyColumns(projects: String): String = """
    COUNT(DISTINCT CASE WHEN $projects.state = 'ACTIVE' THEN $projects.id END) as active_projects,
    COUNT(DISTINCT CASE WHEN $projects.state = 'PENDING' THEN $projects.id END) as pending_projects,
    COUNT(DISTINCT CASE WHEN $projects.state = 'PAUSED' THEN $projects.id END) as paused_projects,
    COUNT(DISTINCT CASE WHEN $projects.state = 'DONE' THEN $projects.id END) as done_projects
""".trimIndent()
