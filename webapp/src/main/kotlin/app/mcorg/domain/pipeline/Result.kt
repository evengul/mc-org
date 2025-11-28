package app.mcorg.domain.pipeline

sealed interface Result<out E, out S> {
    data class Success<out S>(val value: S) : Result<Nothing, S>
    data class Failure<out E>(val error: E) : Result<E, Nothing>

    suspend fun <R> map(transform: suspend (S) -> R): Result<E, R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    suspend fun peek(peekFunc: suspend (S) -> Unit): Result<E, S> = when (this) {
        is Success -> {
            peekFunc(value)
            this
        }
        is Failure -> this
    }

    suspend fun <R> flatMap(transform: suspend (S) -> Result<@UnsafeVariance E, R>): Result<E, R> = when (this) {
        is Success -> transform(value)
        is Failure -> this
    }

    fun <S2> flatMapSuccess(transform: (S) -> Result<@UnsafeVariance E, S2>): Result<E, S2> = when (this) {
        is Success -> transform(this.value)
        is Failure -> this
    }

    fun <S2> mapSuccess(transform: (S) -> S2): Result<@UnsafeVariance E, S2> = when (this) {
        is Success -> Success(transform(this.value))
        is Failure -> this
    }

    suspend fun <F> mapError(transform: suspend (E) -> F): Result<F, S> = when (this) {
        is Success -> this
        is Failure -> Failure(transform(error))
    }

    suspend fun recover(transform: suspend (E) -> Result<@UnsafeVariance E, @UnsafeVariance S>): Result<E, S> = when (this) {
        is Success -> this
        is Failure -> transform(error)
    }

    fun getOrNull(): S? = when (this) {
        is Success -> value
        is Failure -> null
    }

    fun getOrThrow(): S = when (this) {
        is Success -> this.value
        is Failure -> throw IllegalStateException("Result is a failure with error: ${this.error}")
    }

    fun errorOrNull(): E? = when (this) {
        is Success -> null
        is Failure -> error
    }

    suspend fun fold(onSuccess: suspend (S) -> Unit, onFailure: suspend (E) -> Unit) {
        when (this) {
            is Success -> onSuccess(value)
            is Failure -> onFailure(error)
        }
    }

    companion object {
        fun <E, S> success(value: S): Result<E, S> = Success(value)
        fun <E> success(): Result<E, Unit> = Success(Unit)
        fun <E> failure(error: E): Result<E, Nothing> = Failure(error)

        fun <E, S> tryCatch(errorMapper: (Exception) -> E, block: () -> S): Result<E, S> {
            return try {
                success(block())
            } catch (e: Exception) {
                failure(errorMapper(e))
            }
        }
    }

}