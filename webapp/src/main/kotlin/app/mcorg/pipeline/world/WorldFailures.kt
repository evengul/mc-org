package app.mcorg.pipeline.world

sealed interface CreateWorldFailure

sealed interface DeleteWorldFailure

sealed interface SelectWorldFailure : CreateWorldFailure