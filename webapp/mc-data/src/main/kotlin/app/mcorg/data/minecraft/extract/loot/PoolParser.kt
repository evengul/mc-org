package app.mcorg.data.minecraft.extract.loot

import app.mcorg.pipeline.Result
import app.mcorg.data.minecraft.extract.arrayResult
import app.mcorg.data.minecraft.extract.getResult
import app.mcorg.data.minecraft.extract.objectResult
import app.mcorg.data.minecraft.failure.ExtractionFailure
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

class PoolParser {
    lateinit var entryParser: EntryParser
    private val logger = LoggerFactory.getLogger(PoolParser::class.java)

    /**
     * Aggregates a table's pools into expected yield per item id:
     * each roll selects one entry weighted by entry weight, so an item's yield is
     * `E[rolls] * (entryWeight / totalWeight) * E[countPerSelection]`, summed
     * across pools. Null yield = obtainable, amount unknown (unrecognized number
     * provider, conditional alternative, nested unknown).
     */
    suspend fun parsePools(pools: JsonArray, filename: String): Result<ExtractionFailure, Map<String, Double?>> {
        val yields = mutableMapOf<String, Double?>()
        pools.forEach { pool ->
            val entriesResult = pool.objectResult(filename)
                .flatMap { it.getResult("entries", filename) }
                .flatMap { it.arrayResult(filename) }
                .flatMap { entryParser.parseEntries(it, filename) }
            if (entriesResult is Result.Failure) {
                logger.warn("Error parsing pool entries from loot file: $filename")
                return entriesResult
            }

            val entries = entriesResult.getOrThrow()
            val rollsAverage = LootNumbers.average((pool as? JsonObject)?.get("rolls")) ?: 1.0
            val totalWeight = entries.sumOf { it.weight }.takeIf { it > 0 } ?: 1.0

            for (entry in entries) {
                val share = entry.weight / totalWeight
                for (drop in entry.drops) {
                    val contribution = drop.countPerSelection?.let { count -> rollsAverage * share * count }
                    yields[drop.itemId] = if (drop.itemId in yields) {
                        LootNumbers.combine(yields.getValue(drop.itemId), contribution)
                    } else {
                        contribution
                    }
                }
            }
        }
        return Result.success(yields)
    }
}
