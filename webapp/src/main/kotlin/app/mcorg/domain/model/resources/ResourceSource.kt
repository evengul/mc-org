package app.mcorg.domain.model.resources

import app.mcorg.domain.model.minecraft.Item
import org.slf4j.LoggerFactory

// TODO: Extract and store in DB. All names and IDs need to be stored in the names table.
data class ResourceSource(
    val type: SourceType,
    val requiredItems: List<Item> = emptyList(),
    val producedItems: List<Item> = emptyList()
) {

    data class SourceType(val id: String, val name: String) {
        data object LootTypes {
            val ARCHAEOLOGY = SourceType("minecraft:archaeology", "Archaeology")
            val FISHING = SourceType("minecraft:fishing", "Fishing")
            val BLOCK = SourceType("minecraft:block", "Block")
            val BLOCK_INTERACT = SourceType("minecraft:block_interact", "Interact with block")
            val BARTER = SourceType("minecraft:barter", "Piglin Bartering")
            val ENTITY = SourceType("minecraft:entity", "Entity")
            val ENTITY_INTERACT = SourceType("minecraft:entity_interact", "Interact with entity")
            val CHEST = SourceType("minecraft:chest", "Chest")
            val GIFT = SourceType("minecraft:gift", "Gift/Random Mob Drop")
            val EQUIPMENT = SourceType("minecraft:equipment", "Equipment")
            val SHEARING = SourceType("minecraft:shearing", "Shearing")
        }

        companion object {
            private val logger = LoggerFactory.getLogger(ResourceSource::class.java)

            val UNKNOWN = SourceType("mcorg:unknown", "Unknown")

            fun of(id: String): SourceType? {
                return when (id) {
                    LootTypes.ARCHAEOLOGY.id -> LootTypes.ARCHAEOLOGY
                    LootTypes.FISHING.id -> LootTypes.FISHING
                    LootTypes.BLOCK.id -> LootTypes.BLOCK
                    LootTypes.BLOCK_INTERACT.id -> LootTypes.BLOCK_INTERACT
                    LootTypes.BARTER.id -> LootTypes.BARTER
                    LootTypes.ENTITY.id -> LootTypes.ENTITY
                    LootTypes.ENTITY_INTERACT.id -> LootTypes.ENTITY_INTERACT
                    LootTypes.CHEST.id -> LootTypes.CHEST
                    LootTypes.GIFT.id -> LootTypes.GIFT
                    LootTypes.EQUIPMENT.id -> LootTypes.EQUIPMENT
                    LootTypes.SHEARING.id -> LootTypes.SHEARING
                    else -> {
                        logger.warn("Unknown ResourceSource.Type id: $id")
                        UNKNOWN
                    }
                }
            }
        }
    }

}