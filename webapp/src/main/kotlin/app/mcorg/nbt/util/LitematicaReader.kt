package app.mcorg.nbt.util

import app.mcorg.domain.model.minecraft.Litematica
import app.mcorg.domain.pipeline.Result
import app.mcorg.nbt.io.BinaryNbtDeserializer
import app.mcorg.nbt.io.CompressionType
import app.mcorg.nbt.tag.*
import app.mcorg.pipeline.failure.AppFailure
import java.io.InputStream
import kotlin.math.absoluteValue

object LitematicaReader {
    fun readLitematica(inputStream: InputStream): Result<AppFailure, Litematica> {
        val content = inputStream.readBytes()
        val compressionType = CompressionType.detect(content)

        if (compressionType is Result.Failure) {
            return Result.failure(AppFailure.customValidationError("file", "Could not detect compression type"))
        }

        val deserializer = BinaryNbtDeserializer<Any>(compressionType.getOrThrow())

        val result = deserializer.fromBytes(content)

        if (result is Result.Failure) {
            return Result.failure(AppFailure.customValidationError("file", "Could not deserialize Litematica file") )
        }

        val root = result.getOrThrow().tag.value

        if (root !is Map<*, *>) {
            return Result.failure(AppFailure.customValidationError("file", "Invalid Litematica file structure"))
        }

        val metadata = (root["Metadata"] as? CompoundTag)?.extractMetadata()
            ?: return Result.failure(AppFailure.customValidationError("file", "Missing Metadata in Litematica file"))

        val regionData = (root["Regions"] as? CompoundTag)?.extractRegionData()
            ?: return Result.failure(AppFailure.customValidationError("file", "Missing Regions in Litematica file"))

        val litematica = Litematica(
            name = metadata.name,
            author = metadata.author,
            description = metadata.description ?: "",
            size = metadata.size,
            items = regionData.items
        )

        return Result.success(litematica)
    }

    data class LitematicaMetadata(
        val name: String,
        val author: String,
        val description: String?,
        val size: Triple<Int, Int, Int>,
    )

    data class LitematicaRegionData(
        val items: Map<String, Int>
    )

    private fun CompoundTag.extractMetadata(): LitematicaMetadata {
        val content = this.value

        val name = (content["Name"] as? StringTag)?.value ?: "Unnamed"
        val author = (content["Author"] as? StringTag)?.value ?: "Unknown"
        val description = (content["Description"] as? StringTag)?.value

        val size = (content["EnclosingSize"] as? CompoundTag)?.value
        val x = (size?.get("x") as? IntTag)?.value ?: 0
        val y = (size?.get("y") as? IntTag)?.value ?: 0
        val z = (size?.get("z") as? IntTag)?.value ?: 0

        return LitematicaMetadata(
            name = name,
            author = author,
            description = description,
            size = Triple(x, y, z)
        )
    }

    private fun CompoundTag.extractRegionData(): LitematicaRegionData {
        val items = mutableMapOf<String, Int>()

        this.value.forEach { (_, tag) ->
            if (tag is CompoundTag) {
                val regionItems = tag.extractSingleRegionData()
                regionItems.forEach { (itemName, itemCount) ->
                    items[itemName] = items.getOrDefault(itemName, 0) + itemCount
                }
            }
        }

        return LitematicaRegionData(
            items = items
        )
    }

    private fun CompoundTag.extractSingleRegionData(): Map<String, Int> {
        // Get the palette (list of block types)
        val palette = this.getBlockStatePalette()

        // Get the size of the region
        val size = this.value["Size"] as? CompoundTag
        val x = (size?.value?.get("x") as? IntTag)?.value?.absoluteValue ?: return emptyMap()
        val y = (size.value["y"] as? IntTag)?.value?.absoluteValue ?: return emptyMap()
        val z = (size.value["z"] as? IntTag)?.value?.absoluteValue ?: return emptyMap()
        val totalBlocks = x * y * z

        // Get the packed block states
        val blockStatesTag = this.value["BlockStates"] as? LongListTag
        if (blockStatesTag == null || palette.isEmpty()) {
            return emptyMap()
        }

        val blockStates = blockStatesTag.value

        // Calculate bits per block (minimum bits needed to represent palette indices)
        val bitsPerBlock = maxOf(2, 32 - Integer.numberOfLeadingZeros(palette.size - 1))

        // Decode the packed block states
        val blockIndices = mutableListOf<Int>()
        var bitIndex = 0

        repeat(totalBlocks) {
            val longIndex = bitIndex / 64
            val bitOffset = bitIndex % 64

            if (longIndex >= blockStates.size) return@repeat

            val paletteIndex = if (bitOffset + bitsPerBlock <= 64) {
                // The value fits within a single long
                ((blockStates[longIndex] ushr bitOffset) and ((1L shl bitsPerBlock) - 1)).toInt()
            } else {
                // The value spans two longs
                val bitsFromFirst = 64 - bitOffset
                val bitsFromSecond = bitsPerBlock - bitsFromFirst
                val firstPart = (blockStates[longIndex] ushr bitOffset) and ((1L shl bitsFromFirst) - 1)
                val secondPart = if (longIndex + 1 < blockStates.size) {
                    (blockStates[longIndex + 1] and ((1L shl bitsFromSecond) - 1)) shl bitsFromFirst
                } else {
                    0L
                }
                (firstPart or secondPart).toInt()
            }

            blockIndices.add(paletteIndex)
            bitIndex += bitsPerBlock
        }

        // Count occurrences of each palette index
        val indexCounts = blockIndices.groupingBy { it }.eachCount()

        // Map palette indices to block names and aggregate counts
        val blockCounts = mutableMapOf<String, Int>()
        indexCounts.forEach { (paletteIndex, count) ->
            if (paletteIndex < palette.size) {
                val blockName = palette[paletteIndex]
                blockCounts[blockName] = blockCounts.getOrDefault(blockName, 0) + count
            }
        }

        getItemsInInventories()
            .forEach { item ->
                blockCounts[item.key] = blockCounts.getOrDefault(item.key, 0) + item.value
            }

        return blockCounts
    }

    private fun CompoundTag.getItemsInInventories(): Map<String, Int> {
        val items = mutableMapOf<String, Int>()

        val tileEntities = this.value["TileEntities"]
        if (tileEntities is ListTag<*>) {
            tileEntities.value.forEach { entry ->
                if (entry is CompoundTag) {
                    val itemsTag = entry.value["Items"]
                    if (itemsTag != null && itemsTag is ListTag<*> && itemsTag.value.isNotEmpty()) {
                        itemsTag.value.forEach { itemTag ->
                            if (itemTag is CompoundTag) {
                                val id = itemTag.value["id"] as? StringTag
                                val count = itemTag.value["Count"] as? ByteTag

                                if (id != null && count != null) {
                                    items[id.value] = items.getOrDefault(id.value, 0) + count.value.toInt()
                                }
                            }
                        }
                    }
                }
            }
        }

        return items
    }

    private fun CompoundTag.getBlockStatePalette(): List<String> {
        val blockNames = mutableListOf<String>()
        val paletteTag = this.value["BlockStatePalette"]
        if (paletteTag is ListTag<*>) {
            paletteTag.value.forEach { entry ->
                if (entry is CompoundTag) {
                    val nameTag = entry.value["Name"]
                    if (nameTag is StringTag) {
                        blockNames.add(nameTag.value)
                    }
                }
            }
        }
        return blockNames
    }
}