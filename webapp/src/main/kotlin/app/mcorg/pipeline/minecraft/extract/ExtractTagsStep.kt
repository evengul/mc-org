package app.mcorg.pipeline.minecraft.extract

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.failure.AppFailure
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

data object ExtractTagsStep : ParseFilesRecursivelyStep<Pair<String, List<String>>>() {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val cache = ConcurrentHashMap<MinecraftVersion.Release, MutableMap<String, List<String>>>()
    private val mutexMap = ConcurrentHashMap<MinecraftVersion.Release, Mutex>()

    fun getContentOfTag(
        version: MinecraftVersion.Release,
        tag: String
    ): List<String> {
        return cache[version]?.get(tag.removePrefix("#minecraft:") + ".json")?.flatMap {
            if (it.startsWith("#")) getContentOfTag(version, it) else listOf(it)
        } ?: emptyList()
    }

    fun getNameOfTag(
        tag: String
    ): String {
        val cleaned = tag.replace("_", " ").replace("#minecraft:", "")

        return cleaned.mapIndexed { index, ch ->
            if (index == 0 || cleaned[index - 1] == ' ') ch.uppercaseChar() else ch
        }.joinToString("")
    }

    override suspend fun parseFile(
        content: String,
        filename: String
    ): Result<AppFailure, List<Pair<String, List<String>>>> {
        if (cache[version]?.containsKey(filename) == true) {
            return Result.success(
                listOf(
                    filename to (cache[version]?.get(filename) ?: emptyList())
                )
            )
        }

        if (content.isEmpty()) {
            logger.warn("Empty recipe file: $filename")
            return Result.success(emptyList())
        }

        mutexMap.computeIfAbsent(version) { Mutex() }.withLock {
            val versionCache = cache.computeIfAbsent(version) { mutableMapOf() }
            versionCache.computeIfAbsent(filename) {
                val json = try {
                    kotlinx.serialization.json.Json.parseToJsonElement(content)
                } catch (e: Exception) {
                    logger.error("Error parsing JSON from tag file $content", e)
                    return@computeIfAbsent emptyList()
                }

                json.jsonObject["values"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            }
        }

        return Result.success(
            listOf(
                filename to (cache[version]?.get(filename) ?: emptyList())
            )
        )
    }
}