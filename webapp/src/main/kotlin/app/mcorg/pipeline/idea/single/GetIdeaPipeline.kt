package app.mcorg.pipeline.idea.single

import app.mcorg.domain.model.idea.Comment
import app.mcorg.domain.pipeline.Pipeline
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.idea.commonsteps.GetIdeaStep
import app.mcorg.pipeline.notification.getUnreadNotificationsOrZero
import app.mcorg.presentation.handler.executeParallelPipeline
import app.mcorg.presentation.templated.idea.ideaPage
import app.mcorg.presentation.utils.getIdeaId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import java.sql.ResultSet

data class GetCommentsInput(
    val ideaId: Int,
    val userId: Int
)

suspend fun ApplicationCall.handleGetIdea() {
    val ideaId = this.getIdeaId()
    val user = this.getUser()

    val ideaPipeline = Pipeline.create<AppFailure.DatabaseError, Int>()
        .pipe(GetIdeaStep)

    val commentsPipeline = Pipeline.create<AppFailure.DatabaseError, GetCommentsInput>()
        .pipe(GetIdeaCommentsStep)

    val notifications = getUnreadNotificationsOrZero(user.id)

    executeParallelPipeline(
        onSuccess = { (idea, comments) ->
            respondHtml(ideaPage(user, idea, comments, notifications))
        },
        onFailure = {
            respond(HttpStatusCode.InternalServerError, "Failed to get idea")
        }
    ) {
        val idea = pipeline("idea", ideaId, ideaPipeline)
        val comments = pipeline("comments", GetCommentsInput(ideaId, user.id), commentsPipeline)

        merge("ideaWithComments", idea, comments) { ideaResult, commentsResult ->
            Result.success(Pair(ideaResult, commentsResult))
        }
    }
}

private val GetIdeaCommentsStep = DatabaseSteps.query<GetCommentsInput, List<Comment>>(
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
    resultMapper = { resultSet ->
        val comments = mutableListOf<Comment>()
        while (resultSet.next()) {
            val comment = resultSet.toComment()
            comments.add(comment)
        }
        comments
    }
)

fun ResultSet.toComment(): Comment {
    val id = getInt("id")
    val ideaId = getInt("idea_id")
    val commenterId = getInt("commenter_id")
    val commenterName = getString("commenter_name")
    val content = getString("content")
    val ratingDecimal = getBigDecimal("rating")
    val rating = ratingDecimal?.toInt()
    val likesCount = getInt("likes_count")
    val createdAt = getTimestamp("created_at").toInstant()
        .atZone(java.time.ZoneId.systemDefault())
    val youLiked = getBoolean("you_liked")

    return when {
        content != null && rating != null -> {
            Comment.RatedTextComment(
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
            Comment.TextComment(
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
            Comment.RatingComment(
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
}