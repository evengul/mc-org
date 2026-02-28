package app.mcorg.data.minecraft.extract

import app.mcorg.data.minecraft.failure.ExtractionFailure
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.Result
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
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
        return cache[version]?.get(tag)?.flatMap {
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
    ): Result<ExtractionFailure, Pair<String, List<String>>> {
        val tagId = filenameToTagId(filename)
        if (cache[version]?.containsKey(tagId) == true) {
            return Result.success(
                tagId to (cache[version]?.get(tagId) ?: emptyList())
            )
        }

        if (content.isEmpty()) {
            logger.warn("Empty tag file: $filename")
            return Result.failure(ExtractionFailure.MissingContent(filename))
        }

        mutexMap.computeIfAbsent(version) { Mutex() }.withLock {
            val versionCache = cache.computeIfAbsent(version) { mutableMapOf() }
            versionCache.computeIfAbsent(tagId) {
                val json = try {
                    Json.parseToJsonElement(content)
                } catch (e: Exception) {
                    logger.error("Error parsing JSON from tag file $content", e)
                    return@computeIfAbsent emptyList()
                }

                json.jsonObject["values"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            }
        }

        return Result.success(
            tagId to (cache[version]?.get(tagId) ?: emptyList())
        )
    }

    private fun filenameToTagId(filename: String) = "#minecraft:${filename.substringAfterLast('/').substringBeforeLast('.')}"
}
