package app.mcorg.data.minecraft.extract.loot

import app.mcorg.pipeline.Result
import app.mcorg.data.minecraft.extract.arrayResult
import app.mcorg.data.minecraft.extract.getResult
import app.mcorg.data.minecraft.extract.objectResult
import app.mcorg.data.minecraft.failure.ExtractionFailure
import kotlinx.serialization.json.JsonArray
import org.slf4j.LoggerFactory

class PoolParser {
    lateinit var entryParser: EntryParser
    private val logger = LoggerFactory.getLogger(PoolParser::class.java)

    suspend fun parsePool(pools: JsonArray, filename: String): Result<ExtractionFailure, Set<String>> {
        val itemIds = mutableSetOf<String>()
        pools.forEach { pool ->
            val poolResult = pool.objectResult(filename)
                .flatMap { it.getResult("entries", filename) }
                .flatMap { it.arrayResult(filename) }
                .flatMap { entryParser.parseEntries(it, filename) }
            if (poolResult is Result.Failure) {
                logger.warn("Error parsing pool entries from loot file: $filename")
                return poolResult
            }
            itemIds.addAll(poolResult.getOrThrow())
        }
        return Result.success(itemIds)
    }
}
