package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.world.commonsteps.GetPermittedWorldsInput
import app.mcorg.pipeline.world.commonsteps.GetPermittedWorldsStep
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.hxOutOfBands
import app.mcorg.presentation.templated.home.worldList
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import kotlinx.html.ul

suspend fun ApplicationCall.handleSearchWorlds() {
    val userId = this.getUser().id

    val query = request.queryParameters["query"] ?: ""
    val sortBy = request.queryParameters["sortBy"]?.takeIf {
        it in setOf("name_asc", "modified_desc")
    } ?: "modified_desc"

    executeParallelPipeline(
        onSuccess = { (worlds, count) -> respondHtml(createHTML().ul {
            worldList(worlds)
        } + createHTML().p("subtle") {
            hxOutOfBands("true")
            id = "home-worlds-count"
            + "Showing ${worlds.size} of $count world${if(count == 1) "" else "s"}."
        })}
    ) {
        val getWorlds = singleStep("getWorlds", GetPermittedWorldsInput(userId = userId, query, sortBy), GetPermittedWorldsStep)

        val countWorlds = singleStep("countWorlds", userId, CountPermittedWorldsStep)

        merge("searchWorldsData", getWorlds, countWorlds) { worlds, totalCount ->
            Result.success(worlds to totalCount)
        }
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

