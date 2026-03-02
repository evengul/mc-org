package app.mcorg.nbt.util

import app.mcorg.domain.model.minecraft.Litematica
import app.mcorg.nbt.failure.NBTFailure
import app.mcorg.pipeline.Result
import app.mcorg.nbt.io.BinaryNbtDeserializer
import app.mcorg.nbt.io.CompressionType
import app.mcorg.nbt.tag.ByteTag
import app.mcorg.nbt.tag.CompoundTag
import app.mcorg.nbt.tag.IntTag
import app.mcorg.nbt.tag.ListTag
import app.mcorg.nbt.tag.LongListTag
import app.mcorg.nbt.tag.StringTag
import java.io.InputStream
import kotlin.collections.get
import kotlin.math.absoluteValue

object LitematicaReader {
    private object Keys {
        const val METADATA = "Metadata"
        const val REGIONS = "Regions"
        const val NAME = "Name"
        const val AUTHOR = "Author"
        const val DESCRIPTION = "Description"
        const val ENCLOSING_SIZE = "EnclosingSize"
        const val SIZE = "Size"
        const val BLOCK_STATES = "BlockStates"
        const val BLOCK_STATE_PALETTE = "BlockStatePalette"
        const val TILE_ENTITIES = "TileEntities"
        const val ITEMS = "Items"
        const val ID = "id"
        const val COUNT = "Count"
    }

    fun readLitematica(stream: InputStream): Result<NBTFailure, Litematica> {
        val content = stream.readBytes()
        return readLitematica(content)
    }

    fun readLitematica(content: ByteArray): Result<NBTFailure, Litematica> {
        val compressionType = CompressionType.detect(content)

        if (compressionType is Result.Failure) {
            return Result.failure(NBTFailure.UnknownCompressionType)
        }

        val deserializer = BinaryNbtDeserializer<Any>(compressionType.getOrThrow())

        val result = deserializer.fromBytes(content)

        if (result is Result.Failure) {
            return Result.failure(NBTFailure.DeserializeError(result.error.toString()))
        }

        val root = result.getOrThrow().tag.value

        if (root !is Map<*, *>) {
            return Result.failure(NBTFailure.InvalidStructure)
        }

        val metadata = (root[Keys.METADATA] as? CompoundTag)?.extractMetadata()
            ?: return Result.failure(NBTFailure.MissingData(listOf(Keys.METADATA)))

        val regionData = (root[Keys.REGIONS] as? CompoundTag)?.extractRegionData()
            ?: return Result.failure(NBTFailure.MissingData(listOf(Keys.REGIONS)))

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

        val name = (content[Keys.NAME] as? StringTag)?.value ?: "Unnamed"
        val author = (content[Keys.AUTHOR] as? StringTag)?.value ?: "Unknown"
        val description = (content[Keys.DESCRIPTION] as? StringTag)?.value

        val size = (content[Keys.ENCLOSING_SIZE] as? CompoundTag)?.value
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
        // Litematica dimensions can be negative (indicating region direction); absolute value gives block count
        val size = this.value[Keys.SIZE] as? CompoundTag
        val x = (size?.value?.get("x") as? IntTag)?.value?.absoluteValue ?: return emptyMap()
        val y = (size?.value?.get("y") as? IntTag)?.value?.absoluteValue ?: return emptyMap()
        val z = (size?.value?.get("z") as? IntTag)?.value?.absoluteValue ?: return emptyMap()
        val totalBlocks = x.toLong() * y * z
        if (totalBlocks > Int.MAX_VALUE) return emptyMap()
        val totalBlocksInt = totalBlocks.toInt()

        // Get the packed block states
        val blockStatesTag = this.value[Keys.BLOCK_STATES] as? LongListTag
        if (blockStatesTag == null || palette.isEmpty()) {
            return emptyMap()
        }

        val blockStates = blockStatesTag.value

        // Calculate bits per block (minimum bits needed to represent palette indices)
        // palette.size >= 1 guaranteed by guard above
        val bitsPerBlock = maxOf(2, 32 - Integer.numberOfLeadingZeros(palette.size - 1))

        // Decode the packed block states and count directly
        val counts = IntArray(palette.size)
        var bitIndex = 0

        repeat(totalBlocksInt) {
            val longIndex = bitIndex / 64
            val bitOffset = bitIndex % 64

            if (longIndex >= blockStates.size) return@repeat

            val paletteIndex = if (bitOffset + bitsPerBlock <= 64) {
                ((blockStates[longIndex] ushr bitOffset) and ((1L shl bitsPerBlock) - 1)).toInt()
            } else {
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

            if (paletteIndex in counts.indices) {
                counts[paletteIndex]++
            }
            bitIndex += bitsPerBlock
        }

        val blockCounts = mutableMapOf<String, Int>()
        counts.forEachIndexed { index, count ->
            if (count > 0) {
                blockCounts[palette[index]] = blockCounts.getOrDefault(palette[index], 0) + count
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

        val tileEntities = this.value[Keys.TILE_ENTITIES]
        if (tileEntities is ListTag<*>) {
            tileEntities.value.forEach { entry ->
                if (entry is CompoundTag) {
                    val itemsTag = entry.value[Keys.ITEMS]
                    if (itemsTag != null && itemsTag is ListTag<*> && itemsTag.value.isNotEmpty()) {
                        itemsTag.value.forEach { itemTag ->
                            if (itemTag is CompoundTag) {
                                val id = itemTag.value[Keys.ID] as? StringTag
                                val count = itemTag.value[Keys.COUNT] as? ByteTag

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
        val paletteTag = this.value[Keys.BLOCK_STATE_PALETTE]
        if (paletteTag is ListTag<*>) {
            paletteTag.value.forEach { entry ->
                if (entry is CompoundTag) {
                    val nameTag = entry.value[Keys.NAME]
                    if (nameTag is StringTag) {
                        blockNames.add(nameTag.value)
                    }
                }
            }
        }
        return blockNames
    }
}
