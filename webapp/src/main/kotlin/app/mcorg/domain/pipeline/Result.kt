package app.mcorg.domain.pipeline

import app.mcorg.domain.util.Either
import app.mcorg.domain.util.Left
import app.mcorg.domain.util.Right
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

data class Result<E : Failure, S>(val value: Either<E, S>) {

    fun <T> fold(failure: (E) -> T, success: (S) -> T): T {
        return value.fold({ failure(it) }, { success(it) })
    }

    val isSuccess: Boolean
        get() = value is Right<E, S>

    val isFailure: Boolean
        get() = value is Left<E, S>

    fun getOrNull(): S? {
        return when (value) {
            is Right<E, S> -> value.value
            is Left<E, S> -> null
        }
    }

    fun <E2 : Failure> mapFailure(f: (E) -> E2): Result<E2, S> {
        return fold(
            { failure(f(it)) },
            { success(it) }
        )
    }

    fun <S2> andThen(f: (S) -> Result<E, S2>): Result<E, S2> {
        return fold(
            { failure(it) },
            {
                f(it).fold(
                    { failure(it) },
                    { success(it) }
                )
            }
        )
    }

    companion object {
        fun <E : Failure, S> success(value: S): Result<E, S> = Result(Either.right(value))
        fun <E : Failure, S> failure(value: E): Result<E, S> = Result(Either.left(value))
    }
}

interface Failure