package app.mcorg.pipeline.task

import app.mcorg.domain.model.task.PremadeTask
import app.mcorg.domain.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.failure.ConvertMaterialListStepFailure

object ConvertMaterialListStep : Step<String, ConvertMaterialListStepFailure, List<PremadeTask>> {

    override suspend fun process(input: String): Result<ConvertMaterialListStepFailure, List<PremadeTask>> {
        return Result.success(input.getMaterialLines().map { it.materialLineToTask() })
    }

    private fun String.materialLineToTask(): PremadeTask {
        val pieces = split("|").map { it.trim() }.filter { it.isNotBlank() }
        val name = pieces[0]
        val amount = pieces[1].toInt()

        return PremadeTask(name, amount)
    }

    private fun String.getMaterialLines(): List<String> {
        return this.split(System.lineSeparator().toRegex())
            .asSequence()
            .map { it.trim() }
            .filter { !it.contains("+") }
            .filter { !it.contains("Item") }
            .filter { !it.contains("Material List") }
            .filter { it.isNotBlank() }
            .toList()
    }
}