package app.mcorg.pipeline.idea.commonsteps

import app.mcorg.domain.model.idea.Idea
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.idea.extractors.toIdea

/**
 * Step to get all ideas from the database
 */
val GetAllIdeasStep = DatabaseSteps.query<Unit, List<Idea>>(
    sql = SafeSQL.select("""
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
                GROUP BY i.id, i.name, i.description, i.category, i.author, i.sub_authors, i.labels,
                         i.favourites_count, i.rating_average, i.rating_count, i.difficulty,
                         i.minecraft_version_range, i.category_data, i.created_by, i.created_at
                ORDER BY i.created_at DESC
            """.trimIndent()),
    parameterSetter = { _, _ -> },
    resultMapper = { rs ->
        buildList {
            while (rs.next()) {
                add(rs.toIdea())
            }
        }
    }
)