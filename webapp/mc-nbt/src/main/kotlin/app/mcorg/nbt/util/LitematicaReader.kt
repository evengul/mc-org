package app.mcorg.nbt.util

import app.mcorg.domain.model.minecraft.Litematica
import app.mcorg.domain.model.minecraft.LitematicaRegion
import app.mcorg.nbt.failure.NBTFailure
import app.mcorg.pipeline.Result
import app.mcorg.nbt.io.BinaryNbtDeserializer
import app.mcorg.nbt.io.BoundedInputStream
import app.mcorg.nbt.io.CompressionType
import app.mcorg.nbt.io.NbtLimits
import app.mcorg.nbt.io.NbtSizeLimitExceeded
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

    // Air is empty space, not a material — it pads every schematic's palette and
    // must never surface as a required block in the extracted material list.
    private val AIR_BLOCKS = setOf("minecraft:air", "minecraft:cave_air", "minecraft:void_air")

    /**
     * Reads at most [NbtLimits.MAX_DECOMPRESSED_BYTES] from [stream]; a longer stream is refused
     * rather than buffered (MCO-345). Callers that already hold the bytes should prefer the
     * [ByteArray] overload and cap the upload before it reaches memory at all.
     */
    fun readLitematica(stream: InputStream): Result<NBTFailure, Litematica> {
        val content = try {
            BoundedInputStream(stream).readBytes()
        } catch (e: NbtSizeLimitExceeded) {
            return Result.failure(NBTFailure.DeserializeError(e.message ?: "Litematica file is too large"))
        }
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
            items = regionData.items,
            regions = regionData.regions,
            containerItems = regionData.containerItems,
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
        val items: Map<String, Int>,
        val regions: List<LitematicaRegion>,
        val containerItems: Map<String, Int> = emptyMap(),
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

    /**
     * Reads every subregion, keeping each one's name alongside the flattened total.
     *
     * The name is the compound's own key — Litematica stores regions as a map of name to
     * region — and it used to be discarded here (MCO-398). A build routinely separates its
     * functional part from a decorative shell, and dropping the names left the import review
     * with one flat list of hundreds of rows and no way to strike a whole section.
     *
     * Regions keep file order; a region contributing nothing (empty, or all air) is skipped so
     * the review does not offer an empty group.
     */
    private fun CompoundTag.extractRegionData(): LitematicaRegionData {
        val items = mutableMapOf<String, Int>()
        val containerItems = mutableMapOf<String, Int>()
        val regions = mutableListOf<LitematicaRegion>()

        this.value.forEach { (regionName, tag) ->
            if (tag is CompoundTag) {
                val region = tag.extractSingleRegionData()
                if (region.items.isNotEmpty()) {
                    regions.add(LitematicaRegion(regionName, region.items, region.containerItems))
                }
                region.items.forEach { (itemName, itemCount) ->
                    items[itemName] = items.getOrDefault(itemName, 0) + itemCount
                }
                region.containerItems.forEach { (itemName, itemCount) ->
                    containerItems[itemName] = containerItems.getOrDefault(itemName, 0) + itemCount
                }
            }
        }

        return LitematicaRegionData(
            items = items,
            regions = regions,
            containerItems = containerItems,
        )
    }

    /**
     * One region's counts, with the two sources kept apart (MCO-322).
     *
     * [items] is the total — placed blocks plus container contents — because that is what the
     * material list has always meant and every caller already reads it that way. [containerItems]
     * says how much of that total is what the build is stocked with rather than what it places,
     * so the review screen can mark it. Both are wanted; they are just different kinds of ask.
     */
    private data class RegionCounts(
        val items: Map<String, Int>,
        val containerItems: Map<String, Int>,
    )

    private fun CompoundTag.extractSingleRegionData(): RegionCounts {
        // Get the palette (list of block types)
        val palette = this.getBlockStatePalette()

        // Get the size of the region
        // Litematica dimensions can be negative (indicating region direction); absolute value gives block count
        val size = this.value[Keys.SIZE] as? CompoundTag
        val x = (size?.value?.get("x") as? IntTag)?.value?.absoluteValue ?: return RegionCounts(emptyMap(), emptyMap())
        val y = (size?.value?.get("y") as? IntTag)?.value?.absoluteValue ?: return RegionCounts(emptyMap(), emptyMap())
        val z = (size?.value?.get("z") as? IntTag)?.value?.absoluteValue ?: return RegionCounts(emptyMap(), emptyMap())
        val totalBlocks = x.toLong() * y * z
        if (totalBlocks > Int.MAX_VALUE) return RegionCounts(emptyMap(), emptyMap())
        val totalBlocksInt = totalBlocks.toInt()

        // Get the packed block states
        val blockStatesTag = this.value[Keys.BLOCK_STATES] as? LongListTag
        if (blockStatesTag == null || palette.isEmpty()) {
            return RegionCounts(emptyMap(), emptyMap())
        }

        val blockStates = blockStatesTag.value

        // Calculate bits per block (minimum bits needed to represent palette indices)
        // palette.size >= 1 guaranteed by guard above
        val bitsPerBlock = maxOf(2, 32 - Integer.numberOfLeadingZeros(palette.size - 1))

        // Decode the packed block states and count directly.
        //
        // Iterate over what the data can actually address, not over what Size claims (MCO-345).
        // Size is attacker-declared: a ~200 byte file claiming 2000x2000x500 asks for two billion
        // iterations, and the old `return@repeat` was a continue rather than a break, so every one
        // of them ran and did nothing on a Netty worker thread. A block's index is only readable
        // if its bits are present, so the packed array length is the real ceiling.
        val counts = IntArray(palette.size)
        var bitIndex = 0

        val addressableBlocks = (blockStates.size.toLong() * 64) / bitsPerBlock
        val blocksToRead = minOf(totalBlocksInt.toLong(), addressableBlocks).toInt()

        repeat(blocksToRead) {
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
            if (count > 0 && palette[index] !in AIR_BLOCKS) {
                blockCounts[palette[index]] = blockCounts.getOrDefault(palette[index], 0) + count
            }
        }

        // Container contents are folded into the total — that is what the material list has
        // always meant, and a stocked container is normally part of the build rather than
        // clutter. The map is kept so the review screen can say which part of a row is stock
        // the build carries rather than a block it places (MCO-322).
        val containerItems = getItemsInInventories()
        containerItems.forEach { (itemName, itemCount) ->
            blockCounts[itemName] = blockCounts.getOrDefault(itemName, 0) + itemCount
        }

        return RegionCounts(items = blockCounts, containerItems = containerItems)
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
