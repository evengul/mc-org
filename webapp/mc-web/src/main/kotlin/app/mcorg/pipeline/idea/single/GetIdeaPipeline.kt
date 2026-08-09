package app.mcorg.pipeline.idea.single

import app.mcorg.domain.model.idea.Comment
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.pipeline.idea.commonsteps.GetIdeaStep
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.idea.ideaPage
import app.mcorg.presentation.utils.getIdeaId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*
import java.sql.ResultSet

data class GetCommentsInput(
    val ideaId: Int,
    val userId: Int
)

suspend fun ApplicationCall.handleGetIdea() {
    val ideaId = this.getIdeaId()
    val user = this.getUser()

    handlePipeline(
        onSuccess = { (idea, comments, materials) ->
            respondHtml(ideaPage(user, idea, comments, materials))
        }
    ) {
        parallel(
            { GetIdeaStep.run(ideaId) },
            { GetIdeaCommentsStep.run(GetCommentsInput(ideaId, user.id)) },
            { GetIdeaMaterialsStep.run(ideaId) },
        )
    }
}

/** One line of an idea's material list. [name] is null when the item is not in the ingested catalog. */
data class IdeaMaterial(
    val itemId: String,
    val name: String?,
    val quantity: Int,
)

/**
 * What an idea costs to build. Written on create and read by the import pipeline, but never shown
 * to the person deciding whether to import it — the whole point of the list.
 *
 * Item names come from the ingested catalog rather than the raw id, so "minecraft:tnt" reads
 * "TNT" and not "Tnt". Names are per-version; any version's name will do for display, so this
 * takes the highest one rather than joining on the idea's version range.
 */
private val GetIdeaMaterialsStep = DatabaseSteps.query<Int, List<IdeaMaterial>>(
    sql = SafeSQL.select("""
                SELECT
                    r.item_id,
                    r.quantity,
                    (
                        SELECT mi.item_name
                        FROM minecraft_items mi
                        WHERE mi.item_id = r.item_id
                        ORDER BY mi.version DESC
                        LIMIT 1
                    ) AS item_name
                FROM idea_item_requirements r
                WHERE r.idea_id = ?
                ORDER BY r.quantity DESC
            """.trimIndent()),
    parameterSetter = { statement, ideaId -> statement.setInt(1, ideaId) },
    resultMapper = { rs ->
        buildList {
            while (rs.next()) {
                add(
                    IdeaMaterial(
                        itemId = rs.getString("item_id"),
                        name = rs.getString("item_name"),
                        quantity = rs.getInt("quantity"),
                    )
                )
            }
        }
    }
)

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
