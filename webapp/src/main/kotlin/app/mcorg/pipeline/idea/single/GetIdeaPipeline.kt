package app.mcorg.pipeline.idea.single

import app.mcorg.domain.model.idea.Idea
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.idea.toIdea
import app.mcorg.pipeline.notification.GetUnreadNotificationCountStep
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.templated.idea.ideaPage
import app.mcorg.presentation.utils.getIdeaId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

sealed interface GetIdeaFailure {
    object DatabaseError : GetIdeaFailure
}

data class GetCommentsInput(
    val ideaId: Int,
    val userId: Int
)

suspend fun ApplicationCall.handleGetIdea() {
    val ideaId = this.getIdeaId()
    val user = this.getUser()

    val ideaPipeline = Pipeline.create<GetIdeaFailure, Int>()
        .pipe(GetIdeaStep)

    val commentsPipeline = Pipeline.create<GetIdeaFailure, GetCommentsInput>()
        .pipe(GetIdeaCommentsStep)

    val notificationsPipeline = Pipeline.create<GetIdeaFailure, Int>()
        .pipe(Step.value(user.id))
        .pipe(object : Step<Int, GetIdeaFailure, Int> {
            override suspend fun process(input: Int): Result<GetIdeaFailure, Int> {
                return GetUnreadNotificationCountStep.process(input)
                    .mapError { GetIdeaFailure.DatabaseError }
            }
        })
        .recover { Result.success(0) }

    executeParallelPipeline(
        onSuccess = { (idea, comments, unreadCount) ->
            respondHtml(ideaPage(user, idea, comments, unreadCount))
        },
        onFailure = {
            respond(HttpStatusCode.InternalServerError, "Failed to get idea")
        }
    ) {
        val idea = pipeline("idea", ideaId, ideaPipeline)
        val comments = pipeline("comments", GetCommentsInput(ideaId, user.id), commentsPipeline)
        val unreadCount = pipeline("unreadCount", user.id, notificationsPipeline)

        merge("ideaWithCommentsAndCount", idea, comments, unreadCount) { ideaResult, commentsResult, countResult ->
            Result.success(Triple(ideaResult, commentsResult, countResult))
        }
    }
}

private object GetIdeaStep : Step<Int, GetIdeaFailure, Idea> {
    override suspend fun process(input: Int): Result<GetIdeaFailure, Idea> {
        return DatabaseSteps.query<Int, GetIdeaFailure, Idea>(
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
                WHERE i.id = ?
                GROUP BY i.id, i.name, i.description, i.category, i.author, i.sub_authors, i.labels,
                         i.favourites_count, i.rating_average, i.rating_count, i.difficulty,
                         i.minecraft_version_range, i.category_data, i.created_by, i.created_at
                ORDER BY i.created_at DESC
            """.trimIndent()),
            parameterSetter = { statement, id ->
                statement.setInt(1, id)
            },
            errorMapper = { GetIdeaFailure.DatabaseError },
            resultMapper = { resultSet ->
                resultSet.next()
                resultSet.toIdea()
            }
        ).process(input)
    }
}

private object GetIdeaCommentsStep : Step<GetCommentsInput, GetIdeaFailure, List<app.mcorg.domain.model.idea.Comment>> {
    override suspend fun process(input: GetCommentsInput): Result<GetIdeaFailure, List<app.mcorg.domain.model.idea.Comment>> {
        return DatabaseSteps.query<GetCommentsInput, GetIdeaFailure, List<app.mcorg.domain.model.idea.Comment>>(
            sql = SafeSQL.select("""
                SELECT 
                    c.id,
                    c.idea_id,
                    c.commenter_id,
                    c.commenter_name,
                    c.content,
                    c.rating,
                    c.likes_count,
                    c.created_at,
                    EXISTS(
                        SELECT 1 
                        FROM idea_comment_likes 
                        WHERE comment_id = c.id AND user_id = ?
                    ) as you_liked
                FROM idea_comments c
                WHERE c.idea_id = ?
                ORDER BY c.created_at DESC
            """.trimIndent()),
            parameterSetter = { statement, input ->
                statement.setInt(1, input.userId) // For you_liked check
                statement.setInt(2, input.ideaId) // For filtering comments by idea
            },
            errorMapper = { GetIdeaFailure.DatabaseError },
            resultMapper = { resultSet ->
                val comments = mutableListOf<app.mcorg.domain.model.idea.Comment>()
                while (resultSet.next()) {
                    val id = resultSet.getInt("id")
                    val ideaId = resultSet.getInt("idea_id")
                    val commenterId = resultSet.getInt("commenter_id")
                    val commenterName = resultSet.getString("commenter_name")
                    val content = resultSet.getString("content")
                    val ratingDecimal = resultSet.getBigDecimal("rating")
                    val rating = ratingDecimal?.toInt()
                    val likesCount = resultSet.getInt("likes_count")
                    val createdAt = resultSet.getTimestamp("created_at").toInstant()
                        .atZone(java.time.ZoneId.systemDefault())
                    val youLiked = resultSet.getBoolean("you_liked")

                    val comment = when {
                        content != null && rating != null -> {
                            app.mcorg.domain.model.idea.Comment.RatedTextComment(
                                id = id,
                                ideaId = ideaId,
                                commenterId = commenterId,
                                commenterName = commenterName,
                                createdAt = createdAt,
                                likes = likesCount,
                                content = content,
                                rating = rating,
                                youLiked = youLiked
                            )
                        }
                        content != null && rating == null -> {
                            app.mcorg.domain.model.idea.Comment.TextComment(
                                id = id,
                                ideaId = ideaId,
                                commenterId = commenterId,
                                commenterName = commenterName,
                                createdAt = createdAt,
                                likes = likesCount,
                                content = content,
                                youLiked = youLiked
                            )
                        }
                        content == null && rating != null -> {
                            app.mcorg.domain.model.idea.Comment.RatingComment(
                                id = id,
                                ideaId = ideaId,
                                commenterId = commenterId,
                                commenterName = commenterName,
                                createdAt = createdAt,
                                likes = likesCount,
                                youLiked = youLiked,
                                rating = rating
                            )
                        }
                        else -> throw IllegalStateException("Comment must have either content or rating")
                    }
                    comments.add(comment)
                }
                comments
            }
        ).process(input)
    }
}