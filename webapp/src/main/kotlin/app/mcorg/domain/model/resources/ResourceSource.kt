package app.mcorg.domain.model.resources

import app.mcorg.domain.model.minecraft.MinecraftId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.slf4j.LoggerFactory

data class ResourceSource(
    val type: SourceType,
    val filename: String,
    val requiredItems: List<MinecraftId> = emptyList(),
    val producedItems: List<MinecraftId> = emptyList()
) {

    @Serializable(with = SourceType.Companion.SourceTypeSerializer::class)
    data class SourceType(val id: String, val name: String, val score: Int = 0) {

        data object LootTypes {
            val BLOCK = SourceType("minecraft:block", "Break the block yourself", 100)
            val ENTITY = SourceType("minecraft:entity", "Entity", 100)
            val SHEARING = SourceType("minecraft:shearing", "Shearing", 90)
            val BLOCK_INTERACT = SourceType("minecraft:block_interact", "Interact with block", 90)
            val ENTITY_INTERACT = SourceType("minecraft:entity_interact", "Interact with entity", 90)
            val BARTER = SourceType("minecraft:barter", "Piglin Bartering", 80)
            val FISHING = SourceType("minecraft:fishing", "Fishing", 70)
            val CHEST = SourceType("minecraft:chest", "Chest", 60)
            val ARCHAEOLOGY = SourceType("minecraft:archaeology", "Archaeology", 60)
            val EQUIPMENT = SourceType("minecraft:equipment", "Equipment", 60)
            val GIFT = SourceType("minecraft:gift", "Gift/Random Mob Drop", 50)
        }

        data object RecipeTypes {
            val CRAFTING_SHAPELESS = SourceType("minecraft:crafting_shapeless", "Crafting", 95)
            val CRAFTING_SHAPED = SourceType("minecraft:crafting_shaped", "Crafting", 95)
            val CRAFTING_TRANSMUTE = SourceType("minecraft:crafting_transmute", "Crafting", 95)
            val STONECUTTING = SourceType("minecraft:stonecutting", "Stonecutting", 90)
            val SMELTING = SourceType("minecraft:smelting", "Smelting", 85)
            val CAMPFIRE_COOKING = SourceType("minecraft:campfire_cooking", "Campfire Cooking", 80)
            val SMOKING = SourceType("minecraft:smoking", "Smoking", 80)
            val BLASTING = SourceType("minecraft:blasting", "Blasting", 80)
            val SMITHING_TRANSFORM = SourceType("minecraft:smithing_transform", "Smithing Table", 75)
            val IGNORED = SourceType("mcorg:ignored_recipe", "Ignored Recipe", 0)
        }

        companion object {
            private val logger = LoggerFactory.getLogger(ResourceSource::class.java)

            val UNKNOWN = SourceType("mcorg:unknown", "Unknown", 0)

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
                    RecipeTypes.CRAFTING_SHAPED.id -> RecipeTypes.CRAFTING_SHAPED
                    RecipeTypes.CRAFTING_SHAPELESS.id -> RecipeTypes.CRAFTING_SHAPELESS
                    RecipeTypes.CRAFTING_TRANSMUTE.id -> RecipeTypes.CRAFTING_TRANSMUTE
                    RecipeTypes.SMITHING_TRANSFORM.id -> RecipeTypes.SMITHING_TRANSFORM
                    RecipeTypes.SMELTING.id -> RecipeTypes.SMELTING
                    RecipeTypes.BLASTING.id -> RecipeTypes.BLASTING
                    RecipeTypes.SMOKING.id -> RecipeTypes.SMOKING
                    RecipeTypes.CAMPFIRE_COOKING.id -> RecipeTypes.CAMPFIRE_COOKING
                    RecipeTypes.STONECUTTING.id -> RecipeTypes.STONECUTTING
                    RecipeTypes.IGNORED.id -> RecipeTypes.IGNORED
                    else -> {
                        logger.warn("Unknown ResourceSource.Type id: $id")
                        UNKNOWN
                    }
                }
            }

            fun all(): Set<SourceType> = setOf(
                LootTypes.ARCHAEOLOGY,
                LootTypes.FISHING,
                LootTypes.BLOCK,
                LootTypes.BLOCK_INTERACT,
                LootTypes.BARTER,
                LootTypes.ENTITY,
                LootTypes.ENTITY_INTERACT,
                LootTypes.CHEST,
                LootTypes.GIFT,
                LootTypes.EQUIPMENT,
                LootTypes.SHEARING,
                RecipeTypes.CRAFTING_SHAPED,
                RecipeTypes.CRAFTING_SHAPELESS,
                RecipeTypes.CRAFTING_TRANSMUTE,
                RecipeTypes.SMITHING_TRANSFORM,
                RecipeTypes.SMELTING,
                RecipeTypes.BLASTING,
                RecipeTypes.SMOKING,
                RecipeTypes.CAMPFIRE_COOKING,
                RecipeTypes.STONECUTTING,
                RecipeTypes.IGNORED
            )

            data object SourceTypeSerializer : KSerializer<SourceType> {
                override val descriptor: SerialDescriptor
                    get() = String.serializer().descriptor

                override fun serialize(
                    encoder: Encoder,
                    value: SourceType
                ) {
                    encoder.encodeString(value.id)
                }

                override fun deserialize(decoder: Decoder): SourceType {
                    return of(decoder.decodeString()) ?: UNKNOWN
                }
            }

        }
    }

}