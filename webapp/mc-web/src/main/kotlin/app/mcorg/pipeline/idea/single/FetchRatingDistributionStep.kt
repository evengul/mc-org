package app.mcorg.pipeline.idea.single

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure

data class RatingDistributionData(
    val total: Int,
    val average: Double,
    val countPerStar: Map<Int, Int>
)

data class FetchRatingDistributionStep(val ideaId: Int) : Step<Unit, AppFailure.DatabaseError, RatingDistributionData> {
    override suspend fun process(input: Unit): Result<AppFailure.DatabaseError, RatingDistributionData> {
        val summary = DatabaseSteps.query<Unit, Pair<Int, Double>>(
            sql = SafeSQL.select("SELECT rating_count, rating_average FROM ideas WHERE id = ?"),
            parameterSetter = { ps, _ -> ps.setInt(1, ideaId) },
            resultMapper = { rs ->
                rs.next()
                rs.getInt("rating_count") to rs.getDouble("rating_average")
            }
        ).process(Unit)

        if (summary is Result.Failure) return Result.failure(summary.error)
        val (total, average) = summary.getOrNull()!!

        val counts = DatabaseSteps.query<Unit, Map<Int, Int>>(
            sql = SafeSQL.select("""
                SELECT rating, COUNT(*) as cnt
                FROM idea_comments
                WHERE idea_id = ? AND rating IS NOT NULL
                GROUP BY rating
            """.trimIndent()),
            parameterSetter = { ps, _ -> ps.setInt(1, ideaId) },
            resultMapper = { rs ->
                val map = mutableMapOf<Int, Int>()
                while (rs.next()) map[rs.getInt("rating")] = rs.getInt("cnt")
                map
            }
        ).process(Unit)

        if (counts is Result.Failure) return Result.failure(counts.error)

        return Result.success(RatingDistributionData(total, average, counts.getOrNull()!!))
    }
}
