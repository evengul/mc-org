package app.mcorg.pipeline.idea.single

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.presentation.handler.executePipeline
import app.mcorg.presentation.utils.getIdeaId
import app.mcorg.presentation.utils.getUser
import app.mcorg.presentation.utils.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

private data class Input(
    val ideaId: Int,
    val userId: Int
)

sealed interface FavouriteIdeaFailure {
    object DatabaseError : FavouriteIdeaFailure
}

suspend fun ApplicationCall.handleFavouriteIdea() {
    val ideaId = this.getIdeaId()
    val userId = this.getUser().id

    executePipeline(
        onSuccess = { respondHtml("Favorite (${it})") },
        onFailure = { respond(HttpStatusCode.InternalServerError, "Failed to change favourite state of idea for user") }
    ) {
        step(Step.value(Input(ideaId, userId)))
            .step(ChangeFavouriteStateStep)
            .step(Step.value(ideaId))
            .step(GetFavouriteCountForIdeaStep)
    }
}

private object ChangeFavouriteStateStep : Step<Input, FavouriteIdeaFailure, Boolean> {
    override suspend fun process(input: Input): Result<FavouriteIdeaFailure, Boolean> {
        return DatabaseSteps.transaction(object : Step<Input, FavouriteIdeaFailure, Boolean> {
            override suspend fun process(input: Input): Result<FavouriteIdeaFailure, Boolean> {
                return when (val result = GetFavouriteStateForUserStep.process(input)) {
                    is Result.Success -> {
                        if (result.value) {
                            RemoveFavouriteIdeaStep.process(input)
                        } else {
                            FavouriteIdeaStep.process(input)
                        }
                        Result.success(!result.value)
                    }
                    is Result.Failure -> Result.failure(result.error)
                }
            }
        }, errorMapper = { FavouriteIdeaFailure.DatabaseError }).process(input)
    }
}

private object GetFavouriteStateForUserStep : Step<Input, FavouriteIdeaFailure, Boolean> {
    override suspend fun process(input: Input): Result<FavouriteIdeaFailure, Boolean> {
        return DatabaseSteps.query<Input, FavouriteIdeaFailure, Boolean>(
            sql = SafeSQL.select("SELECT EXISTS(SELECT 1 FROM idea_favourites WHERE idea_id = ? AND user_id = ?)"),
            parameterSetter = { statement, (ideaId, userId) ->
                statement.setInt(1, ideaId)
                statement.setInt(2, userId)
            },
            errorMapper = { FavouriteIdeaFailure.DatabaseError },
            resultMapper = { rs ->
                rs.next() && rs.getBoolean(1)
            }
        ).process(input)
    }
}

private object FavouriteIdeaStep : Step<Input, FavouriteIdeaFailure, Unit> {
    override suspend fun process(input: Input): Result<FavouriteIdeaFailure, Unit> {
        return DatabaseSteps.update<Input, FavouriteIdeaFailure>(
            SafeSQL.insert("INSERT INTO idea_favourites (idea_id, user_id) VALUES (?, ?)"),
            parameterSetter = { statement, (ideaId, userId) ->
                statement.setInt(1, ideaId)
                statement.setInt(2, userId)
            },
            errorMapper = { FavouriteIdeaFailure.DatabaseError }
        ).process(input).map {  }
    }
}

private object RemoveFavouriteIdeaStep : Step<Input, FavouriteIdeaFailure, Unit> {
    override suspend fun process(input: Input): Result<FavouriteIdeaFailure, Unit> {
        return DatabaseSteps.update<Input, FavouriteIdeaFailure>(
            SafeSQL.delete("DELETE FROM idea_favourites WHERE idea_id = ? AND user_id = ?"),
            parameterSetter = { statement, (ideaId, userId) ->
                statement.setInt(1, ideaId)
                statement.setInt(2, userId)
            },
            errorMapper = { FavouriteIdeaFailure.DatabaseError }
        ).process(input).map {  }
    }
}

private object GetFavouriteCountForIdeaStep : Step<Int, FavouriteIdeaFailure, Int> {
    override suspend fun process(input: Int): Result<FavouriteIdeaFailure, Int> {
        return DatabaseSteps.query<Int, FavouriteIdeaFailure, Int>(
            sql = SafeSQL.select("SELECT COUNT(*) FROM idea_favourites WHERE idea_id = ?"),
            parameterSetter = { statement, ideaId ->
                statement.setInt(1, ideaId)
            },
            errorMapper = { FavouriteIdeaFailure.DatabaseError },
            resultMapper = { rs ->
                rs.next()
                rs.getInt(1)
            }
        ).process(input)
    }
}