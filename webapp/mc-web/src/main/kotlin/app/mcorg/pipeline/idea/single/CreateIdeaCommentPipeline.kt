package app.mcorg.pipeline.idea.single

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.idea.Comment
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.handlePipeline
import app.mcorg.presentation.templated.idea.ideaAlreadyCommentedOob
import app.mcorg.presentation.templated.idea.ideaCommentItem
import app.mcorg.presentation.templated.idea.ideaRatingDistributionOob
import app.mcorg.presentation.utils.getIdeaId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.html.li
import kotlinx.html.stream.createHTML

private data class CreateIdeaCommentInput(
    val content: String?,
    val rating: Int?
)

suspend fun ApplicationCall.handleCreateIdeaComment() {
    val ideaId = this.getIdeaId()
    val userId = this.getUser().id

    val parameters = this.receiveParameters()

    handlePipeline(
        onSuccess = { (comment, distribution) ->
            respondHtml(
                createHTML().li { ideaCommentItem(userId, comment) } +
                ideaRatingDistributionOob(distribution.total, distribution.average, distribution.countPerStar) +
                ideaAlreadyCommentedOob()
            )
        }
    ) {
        val input = ValidateCommentInput.run(parameters)
        val validated = ValidateNoExistingCommentStep(ideaId, userId).run(input)
        val comment = CreateCommentStep(ideaId, userId).run(validated)
        CacheManager.onIdeaCommentCreated(comment.id)
        val distribution = FetchRatingDistributionStep(ideaId).run(Unit)
        comment to distribution
    }
}

private object ValidateCommentInput : Step<Parameters, AppFailure.ValidationError, CreateIdeaCommentInput> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, CreateIdeaCommentInput> {
        val content = ValidationSteps.optional("content").process(input)
        val rating = ValidationSteps.optionalInt("rating") { it }
            .process(input)
            .flatMap { ratingValue ->
                if (ratingValue != null) {
                    ValidationSteps.validateRange("rating", min = 1, max = 5) { it }.process(ratingValue)
                } else {
                    Result.success(null)
                }
            }

        listOfNotNull(
            content.errorOrNull(),
            rating.errorOrNull()
        ).takeIf { it.isNotEmpty() }?.let {
            return Result.failure(AppFailure.ValidationError(it))
        }

        val contentValue = content.getOrNull()
        val ratingValue = rating.getOrNull()

        if (contentValue == null && ratingValue == null) {
            return Result.failure(AppFailure.customValidationError("content", "A comment or rating is required"))
        }

        return Result.success(
            CreateIdeaCommentInput(
                content = contentValue,
                rating = ratingValue
            )
        )
    }
}

private data class ValidateNoExistingCommentStep(val ideaId: Int, val userId: Int) : Step<CreateIdeaCommentInput, AppFailure, CreateIdeaCommentInput> {
    override suspend fun process(input: CreateIdeaCommentInput): Result<AppFailure, CreateIdeaCommentInput> {
        DatabaseSteps.query<Unit, Boolean>(
            sql = SafeSQL.select("""
                SELECT EXISTS (
                    SELECT 1 FROM idea_comments
                    WHERE idea_id = ? AND commenter_id = ?
                )
            """.trimIndent()),
            parameterSetter = { statement, _ ->
                statement.setInt(1, ideaId)
                statement.setInt(2, userId)
            },
            resultMapper = { resultSet ->
                resultSet.next() && resultSet.getBoolean(1)
            }
        ).process(Unit).let { result ->
            return when (result) {
                is Result.Success -> {
                    if (result.value) {
                        Result.failure(AppFailure.customValidationError("user", "You have already commented on this idea"))
                    } else {
                        Result.success(input)
                    }
                }
                is Result.Failure -> Result.failure(result.error)
            }
        }
    }
}

private data class CreateCommentStep(val ideaId: Int, val userId: Int) : Step<CreateIdeaCommentInput, AppFailure.DatabaseError, Comment> {
    override suspend fun process(input: CreateIdeaCommentInput): Result<AppFailure.DatabaseError, Comment> {
        return DatabaseSteps.transaction { connection ->
            object : Step<CreateIdeaCommentInput, AppFailure.DatabaseError, Comment> {
                override suspend fun process(input: CreateIdeaCommentInput): Result<AppFailure.DatabaseError, Comment> {
                    val commenterName = DatabaseSteps.query<Int, String>(
                        sql = SafeSQL.select("SELECT username FROM minecraft_profiles WHERE id = ?"),
                        parameterSetter = { statement, userId ->
                            statement.setInt(1, userId)
                        },
                        resultMapper = { resultSet ->
                            if (resultSet.next()) {
                                resultSet.getString("username")
                            } else {
                                throw IllegalStateException("User not found")
                            }
                        },
                        connection
                    ).process(userId)

                    if (commenterName is Result.Failure) {
                        return Result.failure(commenterName.error)
                    }

                    val id = DatabaseSteps.update<CreateIdeaCommentInput>(
                        sql = SafeSQL.insert("""
                            INSERT INTO idea_comments (idea_id, commenter_id, commenter_name, content, rating, likes_count, created_at)
                            VALUES (?, ?, ?, ?, ?, 0, NOW())
                            RETURNING id
                        """.trimIndent()),
                        parameterSetter = { statement, input ->
                            statement.setInt(1, ideaId)
                            statement.setInt(2, userId)
                            statement.setString(3, commenterName.getOrNull()!!)
                            if (input.content != null) statement.setString(4, input.content)
                            else statement.setNull(4, java.sql.Types.VARCHAR)
                            if (input.rating != null) {
                                statement.setInt(5, input.rating)
                            } else {
                                statement.setNull(5, java.sql.Types.INTEGER)
                            }
                        },
                        connection
                    ).process(input)

                    if (id is Result.Failure) {
                        return Result.failure(id.error)
                    }

                    return DatabaseSteps.query<Int, Comment>(
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
                                FALSE as you_liked
                            FROM idea_comments c
                            WHERE c.id = ?
                        """.trimIndent()),
                        parameterSetter = { statement, commentId ->
                            statement.setInt(1, commentId)
                        },
                        resultMapper = { it.next(); it.toComment() },
                        connection
                    ).process(id.getOrNull()!!)
                }
            }
        }.process(input)
    }
}
