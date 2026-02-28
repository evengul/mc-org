package app.mcorg.data.minecraft.extract

import app.mcorg.data.minecraft.failure.ExtractionFailure
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.pipeline.Result
import app.mcorg.domain.pipeline.Step
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

object ExtractNamesStep : Step<Pair<MinecraftVersion.Release, Path>, ExtractionFailure, Map<String, String>> {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val cache = ConcurrentHashMap<MinecraftVersion.Release, Map<String, String>>()
    private val mutexMap = ConcurrentHashMap<MinecraftVersion.Release, Mutex>()

    private val whiteListedKeyPrefixes = listOf(
        "item.minecraft.",
        "block.minecraft.",
        "trim_pattern.minecraft.",
        "upgrade.minecraft."
    )

    suspend fun getName(input: Pair<MinecraftVersion.Release, Path>, id: String): String {
        val names = getNames(input)

        if (id.startsWith("minecraft:charged_creeper/")) {
            val entityId = id.removePrefix("minecraft:charged_creeper/")
            val baseName = names["minecraft:${entityId}_skull"] ?: names["minecraft:${entityId}_head"] ?: entityId
            return baseName
        }

        if (id.contains("_armor_trim_smithing_template") && MinecraftVersionRange.Bounded(
            from = MinecraftVersion.release(20, 0),
            to = MinecraftVersion.release(20, 1)
        ).contains(input.first)) {
            return names["minecraft:armor_trim_${id.removePrefix("minecraft:").removeSuffix("_armor_trim_smithing_template")}"] ?: id
        }

        if (id.contains("_upgrade_smithing_template") && MinecraftVersionRange.Bounded(
            from = MinecraftVersion.release(20, 0),
            to = MinecraftVersion.release(20, 1)
        ).contains(input.first)) {
            return names["minecraft:${id.removePrefix("minecraft:").removeSuffix("_smithing_template")}"] ?: id
        }

        return names[id] ?: id
    }

    suspend fun getNames(input: Pair<MinecraftVersion.Release, Path>): Map<String, String> {
        return (process(input).getOrNull() ?: emptyMap())
    }

    override suspend fun process(input: Pair<MinecraftVersion.Release, Path>): Result<ExtractionFailure, Map<String, String>> {
        cache[input.first]?.let {
            return Result.success(it)
        }

        val mutex = mutexMap.computeIfAbsent(input.first) { Mutex() }

        return mutex.withLock {
            cache[input.first]?.let {
                return@withLock Result.success(it)
            }

            extractAndCacheNames(input)

        }
    }

    private fun extractAndCacheNames(input: Pair<MinecraftVersion.Release, Path>): Result<ExtractionFailure, Map<String, String>> {
        try {
            logger.debug("Extracting names for version {}", input.first)
            // input.second is the directory, we need to read lang/en_us.json from within it
            val enUsFile = input.second.resolve("lang").resolve("en_us.json")
            if (!enUsFile.toFile().exists()) {
                logger.error("en_us.json file does not exist at path: {}", enUsFile)
                return Result.failure(ExtractionFailure.MissingFile("en_us.json", version = input.first))
            }

            val content = enUsFile.toFile().readText()

            val map = Json.decodeFromString<LinkedHashMap<String, String>>(content)

            if (map.isEmpty()) {
                logger.warn("No item names extracted from en_us.json for version {}", input.first)
                return Result.failure(ExtractionFailure.MissingContent("en_us.json"))
            }

            val cleaned = cleanNames(map)

            cache[input.first] = cleaned

            logger.debug("Extracted {} names for version {}", cleaned.size, input.first)

            return Result.success(cleaned)
        } catch (e: Exception) {
            logger.error("Failed to extract items for version {}: {}", input.first, e.message, e)
            return Result.failure(ExtractionFailure.ItemExtractionFailed(input.first))
        }
    }

    private fun cleanNames(names: Map<String, String>): Map<String, String> {
        return names.filterKeys {
            key -> whiteListedKeyPrefixes.any { prefix -> key.startsWith(prefix) }
        }.mapValues { entry ->
            entry.value + if (entry.key.contains("item.minecraft.")) {
                " (Item)"
            } else if (entry.key.contains("block.minecraft.")) {
                " (Block)"
            } else {
                ""
            }
        }.mapKeys { entry ->
            entry.key
                .replace("item.minecraft.", "minecraft:")
                .replace("block.minecraft.", "minecraft:")
                .replace("trim_pattern.minecraft.", "minecraft:armor_trim_")
                .replace("upgrade.minecraft.", "minecraft:")
        }
    }


}
