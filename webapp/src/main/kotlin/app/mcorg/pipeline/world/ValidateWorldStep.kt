package app.mcorg.pipeline.world

import app.mcorg.domain.pipeline.Step

sealed interface WorldValidationFailure : CreateWorldFailure {
    data object WorldNameEmpty : WorldValidationFailure
    data object WorldNameTooLong : WorldValidationFailure
}

const val WORLD_NAME_MAX_LENGTH = 50

val ValidateWorldNonEmptyStep = Step.validate<String, WorldValidationFailure.WorldNameEmpty>(WorldValidationFailure.WorldNameEmpty) { it.isNotEmpty() }
val ValidateWorldNameLengthStep = Step.validate<String, WorldValidationFailure.WorldNameTooLong>(WorldValidationFailure.WorldNameTooLong) { it.length <= WORLD_NAME_MAX_LENGTH }