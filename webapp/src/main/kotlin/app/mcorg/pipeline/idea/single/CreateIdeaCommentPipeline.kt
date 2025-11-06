package app.mcorg.pipeline.idea.single

import app.mcorg.domain.model.idea.Comment
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.ValidationSteps
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.templated.idea.ideaCommentItem
import app.mcorg.presentation.utils.getIdeaId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.html.li
import kotlinx.html.stream.createHTML

private data class CreateIdeaCommentInput(
    val content: String,
    val rating: Int?
)

suspend fun ApplicationCall.handleCreateIdeaComment() {
    val ideaId = this.getIdeaId()
    val userId = this.getUser().id

    val parameters = this.receiveParameters()

    executePipeline(
        onSuccess = {
            respondHtml(createHTML().li {
                ideaCommentItem(userId, it)
            })
        },
        onFailure = {
            respond(HttpStatusCode.InternalServerError, "Failed to comment on idea")
        }
    ) {
        value(parameters)
            .step(ValidateCommentInput)
            .step(ValidateNoExistingCommentStep(ideaId, userId))
            .step(CreateCommentStep(ideaId, userId))
    }
}

private object ValidateCommentInput : Step<Parameters, AppFailure.ValidationError, CreateIdeaCommentInput> {
    override suspend fun process(input: Parameters): Result<AppFailure.ValidationError, CreateIdeaCommentInput> {
        val content = ValidationSteps.required("content") { it }.process(input)
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

        return Result.success(
            CreateIdeaCommentInput(
                content = content.getOrNull()!!,
                rating = rating.getOrNull()
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
                            statement.setString(4, input.content)
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