package app.mcorg.pipeline.idea

import app.mcorg.domain.model.idea.Idea
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.idea.extractors.toIdea
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.idea.ideasListContainerContent
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.stream.createHTML


suspend fun ApplicationCall.handleSearchIdeas() {
    val filters = IdeaFilterParser.parse(request.queryParameters)

    handlePipeline(
        onSuccess = { result ->
            respondHtml(createHTML().div {
                id = "ideas-list-container"
                ideasListContainerContent(result, filters)
            })
        }
    ) {
        SearchIdeasStep.run(filters)
    }
}

object SearchIdeasStep : Step<IdeaSearchFilters, AppFailure.DatabaseError, PaginatedResult<Idea>> {
    override suspend fun process(input: IdeaSearchFilters): Result<AppFailure.DatabaseError, PaginatedResult<Idea>> {
        val sqlWhereClause = IdeaSqlBuilder.buildWhereClause(input)

        val baseSql = """
            SELECT
                i.id, i.name, i.description, i.category, i.author, i.sub_authors, i.labels,
                i.favourites_count, i.rating_average, i.rating_count, i.difficulty,
                i.minecraft_version_range, i.category_data, i.created_by, i.created_at,
                COUNT(*) OVER() AS total_count,
                COALESCE(
                    json_agg(
                        json_build_object(
                            'mspt', t.mspt,
                            'hardware', t.hardware,
                            'version', t.minecraft_version
                        )
                    ) FILTER (WHERE t.id IS NOT NULL),
                    '[]'
                ) as test_data
            FROM ideas i
            LEFT JOIN idea_test_data t ON i.id = t.idea_id
            ${sqlWhereClause.whereClause}
            GROUP BY i.id, i.name, i.description, i.category, i.author, i.sub_authors, i.labels,
                     i.favourites_count, i.rating_average, i.rating_count, i.difficulty,
                     i.minecraft_version_range, i.category_data, i.created_by, i.created_at
            ORDER BY i.created_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()

        return DatabaseSteps.query<SqlWhereClause, Pair<List<Idea>, Int>>(
            sql = SafeSQL.select(baseSql),
            parameterSetter = { statement, whereClause ->
                var index = 1
                whereClause.parameters.forEach { param ->
                    when (param) {
                        is Array<*> -> {
                            val sqlArray = statement.connection.createArrayOf("text", param)
                            statement.setArray(index, sqlArray)
                        }
                        else -> statement.setObject(index, param)
                    }
                    index++
                }
                statement.setInt(index, whereClause.pageSize)
                statement.setInt(index + 1, (whereClause.page - 1) * whereClause.pageSize)
            },
            resultMapper = { rs ->
                val ideas = mutableListOf<Idea>()
                var totalCount = 0
                while (rs.next()) {
                    ideas.add(rs.toIdea())
                    if (ideas.size == 1) totalCount = rs.getInt("total_count")
                }
                ideas to totalCount
            }
        ).process(sqlWhereClause).map { (ideas, totalCount) ->
            PaginatedResult(ideas, totalCount, input.page, input.pageSize)
        }
    }
}
