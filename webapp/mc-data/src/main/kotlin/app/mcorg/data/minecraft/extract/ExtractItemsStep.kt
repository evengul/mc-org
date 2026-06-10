package app.mcorg.data.minecraft.extract

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import app.mcorg.data.minecraft.failure.ExtractionFailure
import org.slf4j.LoggerFactory

data object ExtractItemsStep : Step<ExtractionContext, ExtractionFailure, List<Item>> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override suspend fun process(input: ExtractionContext): Result<ExtractionFailure, List<Item>> {
        if (input.itemIds.isEmpty()) {
            logger.warn("No item IDs in the lang registry for version {}", input.version)
        }

        return Result.success(
            input.itemIds.map { Item(it, input.nameOf(it)) }
        )
    }
}
