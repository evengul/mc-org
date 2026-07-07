package app.mcorg.api

import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.AppFailure
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json

/** Lenient JSON codec for the API surface. `encodeDefaults` so e.g. `token_type` is always emitted. */
val apiJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    isLenient = true
}

suspend inline fun <reified T> ApplicationCall.respondJson(status: HttpStatusCode, value: T) {
    respondText(apiJson.encodeToString(value), ContentType.Application.Json, status)
}

suspend fun ApplicationCall.respondApiError(
    status: HttpStatusCode,
    error: String,
    message: String? = null,
) = respondJson(status, ApiErrorResponse(error, message))

/** Decode a JSON request body, or null when the body is missing/malformed. */
suspend inline fun <reified T> ApplicationCall.receiveJsonOrNull(): T? =
    runCatching { apiJson.decodeFromString<T>(receiveText()) }.getOrNull()

/** The world a project belongs to; [AppFailure.DatabaseError.NotFound] when the project is absent. */
object GetProjectWorldIdStep : Step<Int, AppFailure.DatabaseError, Int> {
    override suspend fun process(input: Int): Result<AppFailure.DatabaseError, Int> =
        DatabaseSteps.query<Int, Int?>(
            sql = SafeSQL.select("SELECT world_id FROM projects WHERE id = ?"),
            parameterSetter = { st, id -> st.setInt(1, id) },
            resultMapper = { if (it.next()) it.getInt("world_id") else null },
        ).process(input).flatMap {
            if (it == null) Result.failure(AppFailure.DatabaseError.NotFound) else Result.success(it)
        }
}
