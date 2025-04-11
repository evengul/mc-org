package app.mcorg.pipeline.world

import app.mcorg.pipeline.DatabaseFailure

sealed interface WorldParamFailure {
    data object UserNotInWorld : WorldParamFailure
    data class Other(val failure: DatabaseFailure) : WorldParamFailure
}

sealed interface GetAllWorldsFailure

sealed interface CreateWorldFailure

sealed interface DeleteWorldFailure

sealed interface SelectWorldFailure : CreateWorldFailure