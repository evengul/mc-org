package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.DatabaseFailure
import app.mcorg.pipeline.useConnection

sealed interface EnsureWorldExistsFailure : WorldParamFailure {
    data object WorldNotFound : EnsureWorldExistsFailure
    data class Other(val failure: DatabaseFailure) : EnsureWorldExistsFailure
}

object EnsureWorldExistsStep : Step<Int, EnsureWorldExistsFailure, Int> {
    override suspend fun process(input: Int): Result<EnsureWorldExistsFailure, Int> {
        return useConnection({ EnsureWorldExistsFailure.Other(it) }) {
            val statement = prepareStatement("select 1 from world where id = ? limit 1")
            statement.setInt(1, input)
            val resultSet = statement.executeQuery()
            return@useConnection if (resultSet.next()) {
                Result.success(input)
            } else {
                Result.failure(EnsureWorldExistsFailure.WorldNotFound)
            }
        }
    }
}