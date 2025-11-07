package app.mcorg.pipeline.idea.single

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.TransactionConnection
import app.mcorg.pipeline.failure.AppFailure
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.utils.getIdeaId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.server.application.*

private data class Input(
    val ideaId: Int,
    val userId: Int
)

suspend fun ApplicationCall.handleFavouriteIdea() {
    val ideaId = this.getIdeaId()
    val userId = this.getUser().id

    executePipeline(
        onSuccess = { respondHtml("Favorite (${it})") }) {
        step(Step.value(Input(ideaId, userId)))
            .step(ChangeFavouriteStateStep)
            .step(Step.value(ideaId))
            .step(GetFavouriteCountForIdeaStep)
    }
}

private object ChangeFavouriteStateStep : Step<Input, AppFailure.DatabaseError, Boolean> {
    override suspend fun process(input: Input): Result<AppFailure.DatabaseError, Boolean> {
        return DatabaseSteps.transaction { connection ->
            object : Step<Input, AppFailure.DatabaseError, Boolean> {
                override suspend fun process(input: Input): Result<AppFailure.DatabaseError, Boolean> {
                    return when (val result = getFavouriteStateForUserStep(connection).process(input)) {
                        is Result.Success -> {
                            if (result.value) {
                                removeFavouriteIdeaStep(connection).process(input)
                            } else {
                                favouriteIdeaStep(connection).process(input)
                            }
                            Result.success(!result.value)
                        }

                        is Result.Failure -> Result.failure(result.error)
                    }
                }
            }
        }.process(input)
    }

    private fun getFavouriteStateForUserStep(connection: TransactionConnection) = DatabaseSteps.query<Input, Boolean>(
        sql = SafeSQL.select("SELECT EXISTS(SELECT 1 FROM idea_favourites WHERE idea_id = ? AND user_id = ?)"),
        parameterSetter = { statement, (ideaId, userId) ->
            statement.setInt(1, ideaId)
            statement.setInt(2, userId)
        },
        resultMapper = { rs ->
            rs.next() && rs.getBoolean(1)
        },
        connection
    )

    private fun favouriteIdeaStep(connection: TransactionConnection) = DatabaseSteps.update<Input>(
        SafeSQL.insert("INSERT INTO idea_favourites (idea_id, user_id) VALUES (?, ?)"),
        parameterSetter = { statement, (ideaId, userId) ->
            statement.setInt(1, ideaId)
            statement.setInt(2, userId)
        },
        connection
    )

    private fun removeFavouriteIdeaStep(connection: TransactionConnection) = DatabaseSteps.update<Input>(
        SafeSQL.delete("DELETE FROM idea_favourites WHERE idea_id = ? AND user_id = ?"),
        parameterSetter = { statement, (ideaId, userId) ->
            statement.setInt(1, ideaId)
            statement.setInt(2, userId)
        },
        connection
    )
}

private val GetFavouriteCountForIdeaStep = DatabaseSteps.query<Int, Int>(
    sql = SafeSQL.select("SELECT COUNT(*) FROM idea_favourites WHERE idea_id = ?"),
    parameterSetter = { statement, ideaId ->
        statement.setInt(1, ideaId)
    },
    resultMapper = { rs ->
        if (rs.next()) {
            rs.getInt(1)
        } else 0
    }
)