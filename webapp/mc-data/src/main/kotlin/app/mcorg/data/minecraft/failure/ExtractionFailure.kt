package app.mcorg.data.minecraft.failure

import app.mcorg.domain.model.minecraft.MinecraftVersion
import kotlinx.serialization.json.JsonElement
import java.nio.file.Path

sealed interface ExtractionFailure {
    object ServerListNotFound : ExtractionFailure

    data class ServerExtractionFailed(val version: MinecraftVersion) : ExtractionFailure
    data class NoServerFilesFound(val version: MinecraftVersion) : ExtractionFailure

    object ServerFileDeletionFailed : ExtractionFailure

    data class BasePathNotFound(val basePath: Path, val version: MinecraftVersion) : ExtractionFailure
    data class FilePathExtractionFailed(val basePath: Path, val version: MinecraftVersion) : ExtractionFailure

    data class Multiple(val failures: List<ExtractionFailure>) : ExtractionFailure

    sealed interface JsonFailure : ExtractionFailure {
        data class NotAnObject(val json: JsonElement, val filename: String) : JsonFailure
        data class NotAnArray(val json: JsonElement, val filename: String) : JsonFailure
        data class NotAPrimitive(val json: JsonElement, val filename: String) : JsonFailure

        data class KeyNotFound(val json: JsonElement, val key: String, val filename: String) : JsonFailure

        data class UnknownValue(val value: String, val key: String, val json: JsonElement, val filename: String) : JsonFailure
        data class UnsupportedType(val type: String, val key: String, val json: JsonElement, val filename: String) : JsonFailure

        data class ParseError(val input: String, val filename: String) : JsonFailure
    }

    data class FileReadFailure(val filename: String) : JsonFailure

    data class ItemExtractionFailed(val version: MinecraftVersion) : ExtractionFailure

    data class MissingFile(val filename: String, val version: MinecraftVersion) : ExtractionFailure
    data class MissingContent(val filename: String) : ExtractionFailure
}
