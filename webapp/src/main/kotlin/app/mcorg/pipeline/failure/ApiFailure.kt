package app.mcorg.pipeline.failure

sealed interface ApiFailure {
    data object NetworkError : ApiFailure
    data object TimeoutError : ApiFailure
    data object RateLimitExceeded : ApiFailure
    data class HttpError(val statusCode: Int, val body: String? = null) : ApiFailure
    data object SerializationError : ApiFailure
    data object UnknownError : ApiFailure
}
