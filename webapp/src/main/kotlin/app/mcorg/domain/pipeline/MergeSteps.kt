package app.mcorg.domain.pipeline

object MergeSteps {
    fun <E, A, B> combine(): suspend (A, B) -> Result<E, Pair<A, B>> = { a, b ->
        Result.success(Pair(a, b))
    }

    fun <E, A, B, C> combine(): suspend (A, B, C) -> Result<E, Triple<A, B, C>> = { a, b, c ->
        Result.success(Triple(a, b, c))
    }

    fun <E, T> collectList(): suspend (Map<String, Any>) -> Result<E, List<T>> = { results ->
        @Suppress("UNCHECKED_CAST")
        Result.success(results.values.toList() as List<T>)
    }

    fun <E, A, B, R> transform(
        transform: suspend (A, B) -> R
    ): suspend (A, B) -> Result<E, R> = { a, b ->
        Result.success(transform(a, b))
    }

    fun <E, A, B, C, R> transform(
        transform: suspend (A, B, C) -> R
    ): suspend (A, B, C) -> Result<E, R> = { a, b, c ->
        Result.success(transform(a, b, c))
    }

    fun <E, A, B> validate(
        error: E,
        predicate: suspend (A, B) -> Boolean
    ): suspend (A, B) -> Result<E, Pair<A, B>> = { a, b ->
        if (predicate(a, b)) {
            Result.success(Pair(a, b))
        } else {
            Result.failure(error)
        }
    }
}

