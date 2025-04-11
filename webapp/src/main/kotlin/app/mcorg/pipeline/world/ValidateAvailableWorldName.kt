package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface ValidateAvailableWorldNameFailure : CreateWorldFailure {
    data object AlreadyExists : ValidateAvailableWorldNameFailure
    data class Other(val failure: DatabaseFailure) : ValidateAvailableWorldNameFailure
}

object ValidateAvailableWorldName : Step<String, ValidateAvailableWorldNameFailure, String> {
    override suspend fun process(input: String): Result<ValidateAvailableWorldNameFailure, String> {
        return useConnection(::mapDatabaseFailure) {
            prepareStatement("select id from world where name = ? limit 1")
                .apply { setString(1, input) }
                .executeQuery()
                .use { resultSet ->
                    if (resultSet.next()) {
                        return@useConnection Result.failure(ValidateAvailableWorldNameFailure.AlreadyExists)
                    }
                }
            return@useConnection Result.success(input)
        }
    }
}

private fun mapDatabaseFailure(e: DatabaseFailure): ValidateAvailableWorldNameFailure
        = ValidateAvailableWorldNameFailure.Other(e)