package app.mcorg.pipeline.world

import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.world.commonsteps.GetPermittedWorldsInput
import app.mcorg.pipeline.world.commonsteps.GetPermittedWorldsStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.dsl.worldCard
import kotlinx.html.div
import kotlinx.html.id
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import kotlinx.html.stream.createHTML

suspend fun ApplicationCall.handleSearchWorlds() {
    val userId = this.getUser().id

    val query = request.queryParameters["query"] ?: ""
    val sortBy = request.queryParameters["sortBy"]?.takeIf {
        it in setOf("name_asc", "modified_desc")
    } ?: "modified_desc"

    handlePipeline(
        onSuccess = { worlds ->
            respondHtml(createHTML(prettyPrint = false).div {
                id = "world-card-list"
                worlds.forEach { world ->
                    worldCard(world)
                }
            })
        }
    ) {
        GetPermittedWorldsStep.run(GetPermittedWorldsInput(userId = userId, query, sortBy))
    }
}

private val CountPermittedWorldsStep = DatabaseSteps.query<Int, Int>(
    sql = SafeSQL.select(
        """
                SELECT COUNT(distinct world_id) as total_worlds
                FROM world w
                INNER JOIN world_members wm ON w.id = wm.world_id
                WHERE wm.user_id = ?
            """.trimIndent()
    ),
    parameterSetter = { statement, inputData ->
        statement.setInt(1, inputData)
    },
    resultMapper = { resultSet ->
        if (resultSet.next()) {
            resultSet.getInt("total_worlds")
        } else {
            0
        }
    }
)
