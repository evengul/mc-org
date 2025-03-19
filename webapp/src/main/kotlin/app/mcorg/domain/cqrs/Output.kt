package app.mcorg.domain.cqrs

import app.mcorg.domain.util.Either
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

data class Output<E : Failure, S : Success>(val value: Either<E, S>) {

    suspend fun <T> fold(failure: suspend (E) -> T, success: suspend (S) -> T): T = coroutineScope {
        value.fold({ runBlocking { failure(it) } }, { runBlocking { success(it) } })
    }

    companion object {
        fun <E : Failure, S : Success> success(value: S): Output<E, S> = Output(Either.right(value))
        fun <E : Failure, S : Success> failure(value: E): Output<E, S> = Output(Either.left(value))

        fun <F : Failure> success() = success<F, Success>(DefaultSuccess())
    }
}

interface Failure
interface Success

class DefaultSuccess : Success