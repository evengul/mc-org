package app.mcorg.pipeline.task

import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import java.io.InputStream

sealed interface ValidateMaterialListStepFailure : CreateLitematicaTasksFailure {
    data object InvalidFile : ValidateMaterialListStepFailure
    data object BlankFile : ValidateMaterialListStepFailure
}

object ValidateMaterialListStep : Step<InputStream, ValidateMaterialListStepFailure, String> {
    override suspend fun process(input: InputStream): Result<ValidateMaterialListStepFailure, String> {
        val fileContent = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return if (fileContent.isBlank()) {
            Result.failure(ValidateMaterialListStepFailure.BlankFile)
        } else if (!fileContent.contains("Material List") || !fileContent.contains("Item")) {
            Result.failure(ValidateMaterialListStepFailure.InvalidFile)
        } else {
            Result.success(fileContent)
        }
    }
}