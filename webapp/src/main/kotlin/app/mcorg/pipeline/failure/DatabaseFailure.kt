package app.mcorg.pipeline.failure

sealed interface DatabaseFailure {
    data object ConnectionError : DatabaseFailure
    data object StatementError : DatabaseFailure
    data object IntegrityConstraintError : DatabaseFailure
    data object UnknownError : DatabaseFailure
    data object NoIdReturned : DatabaseFailure
    data object NotFound : DatabaseFailure
}