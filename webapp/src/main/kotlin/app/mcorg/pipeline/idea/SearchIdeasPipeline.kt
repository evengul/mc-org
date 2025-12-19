package app.mcorg.pipeline.idea

import app.mcorg.domain.model.idea.Idea
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.idea.commonsteps.GetAllIdeasStep
import app.mcorg.pipeline.idea.extractors.toIdea
import app.mcorg.presentation.templated.idea.ideaList
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.ApplicationCall
import kotlinx.html.stream.createHTML
import kotlinx.html.ul


suspend fun ApplicationCall.handleSearchIdeas() {
    // Parse ALL filter parameters using the filter parser
    val filters = IdeaFilterParser.parse(request.queryParameters)

    // Query database with all filters
    when (val result = SearchIdeasStep.process(filters)) {
        is Result.Success -> {
            // Return HTML fragment for idea list
            respondHtml(createHTML().ul {
                ideaList(result.value)
            })
        }
        is Result.Failure -> {
            // Return empty list on error
            respondHtml(createHTML().ul {
                ideaList(emptyList())
            })
        }
    }
}

object SearchIdeasStep : Step<IdeaSearchFilters, AppFailure.DatabaseError, List<Idea>> {
    override suspend fun process(input: IdeaSearchFilters): Result<AppFailure.DatabaseError, List<Idea>> {
        // If no filters at all, delegate to GetAllIdeasStep
        if (input.category == null &&
            input.query == null &&
            input.difficulties.isEmpty() &&
            input.minRating == null &&
            input.minecraftVersion == null &&
            input.categoryFilters.isEmpty()) {
            return GetAllIdeasStep.process(Unit)
        }

        // Build dynamic WHERE clause from filters
        val sqlWhereClause = IdeaSqlBuilder.buildWhereClause(input)

        // Base SELECT with LEFT JOIN for test data
        val baseSql = """
            SELECT 
                i.id, i.name, i.description, i.category, i.author, i.sub_authors, i.labels,
                i.favourites_count, i.rating_average, i.rating_count, i.difficulty,
                i.minecraft_version_range, i.category_data, i.created_by, i.created_at,
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
        """.trimIndent()

        return DatabaseSteps.query<SqlWhereClause, List<Idea>>(
            sql = SafeSQL.select(baseSql),
            parameterSetter = { statement, whereClause ->
                whereClause.parameters.forEachIndexed { index, param ->
                    when (param) {
                        is Array<*> -> {
                            // Handle arrays for PostgreSQL (e.g., for ?| operator)
                            val sqlArray = statement.connection.createArrayOf("text", param)
                            statement.setArray(index + 1, sqlArray)
                        }
                        else -> {
                            // Handle all other types
                            statement.setObject(index + 1, param)
                        }
                    }
                }
            },
            resultMapper = { rs ->
                buildList {
                    while (rs.next()) {
                        add(rs.toIdea())
                    }
                }
            }
        ).process(sqlWhereClause)
    }
}

/**
 * Extension function to convert a ResultSet row to an Idea object
 */
