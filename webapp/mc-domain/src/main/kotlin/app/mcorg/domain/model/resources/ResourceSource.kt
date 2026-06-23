package app.mcorg.domain.model.resources

import app.mcorg.domain.model.minecraft.MinecraftId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

data class ResourceSource(
    val type: SourceType,
    val filename: String,
    val requiredItems: List<Pair<MinecraftId, ResourceQuantity>> = emptyList(),
    val producedItems: List<Pair<MinecraftId, ResourceQuantity>> = emptyList()
) {

    @Serializable(with = SourceType.Companion.SourceTypeSerializer::class)
    data class SourceType(val id: String, val name: String, val score: Int = 0) {

        fun isRecipe(): Boolean = this in RECIPE_TYPES

        fun isLoot(): Boolean = this in LOOT_TYPES

        fun isTrade(): Boolean = this in TRADE_TYPES

        /**
         * A "constructive" source is a deliberate way to *make* the item — a recipe or an
         * in-world transform (place concrete powder by water). Used by selection to decide
         * whether breaking the item's own placed block is circular ("re-collect what you
         * built"): when a constructive alternative exists, the self-block loot is penalized.
         */
        fun isConstructive(): Boolean = isRecipe() || this == MechanicTypes.IN_WORLD_TRANSFORM

        private object TypeSets {
            val RECIPE_TYPES = setOf(
                RecipeTypes.CRAFTING_SHAPED, RecipeTypes.CRAFTING_SHAPELESS, RecipeTypes.CRAFTING_TRANSMUTE,
                RecipeTypes.CRAFTING_IMBUE,
                RecipeTypes.SMELTING, RecipeTypes.BLASTING, RecipeTypes.SMOKING,
                RecipeTypes.CAMPFIRE_COOKING, RecipeTypes.STONECUTTING, RecipeTypes.SMITHING_TRANSFORM
            )
            val LOOT_TYPES = setOf(
                LootTypes.BLOCK, LootTypes.ENTITY, LootTypes.SHEARING,
                LootTypes.BLOCK_INTERACT, LootTypes.ENTITY_INTERACT,
                LootTypes.BARTER, LootTypes.FISHING, LootTypes.CHEST,
                LootTypes.ARCHAEOLOGY, LootTypes.EQUIPMENT, LootTypes.GIFT
            )
            val TRADE_TYPES = setOf(
                TradeTypes.ARMORER, TradeTypes.BUTCHER, TradeTypes.CARTOGRAPHER,
                TradeTypes.CLERIC, TradeTypes.FARMER, TradeTypes.FISHERMAN,
                TradeTypes.FLETCHER, TradeTypes.LEATHERWORKER, TradeTypes.LIBRARIAN,
                TradeTypes.MASON, TradeTypes.SHEPHERD, TradeTypes.SMITH,
                TradeTypes.TOOLSMITH, TradeTypes.WEAPONSMITH, TradeTypes.WANDERING_TRADER
            )
        }

        private val RECIPE_TYPES get() = TypeSets.RECIPE_TYPES
        private val LOOT_TYPES get() = TypeSets.LOOT_TYPES
        private val TRADE_TYPES get() = TypeSets.TRADE_TYPES

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

        data object TradeTypes {
            val ARMORER = SourceType("minecraft:trade/armorer", "Armorer Trade", 70)
            val BUTCHER = SourceType("minecraft:trade/butcher", "Butcher Trade", 70)
            val CARTOGRAPHER = SourceType("minecraft:trade/cartographer", "Cartographer Trade", 70)
            val CLERIC = SourceType("minecraft:trade/cleric", "Cleric Trade", 70)
            val FARMER = SourceType("minecraft:trade/farmer", "Farmer Trade", 70)
            val FISHERMAN = SourceType("minecraft:trade/fisherman", "Fisherman Trade", 70)
            val FLETCHER = SourceType("minecraft:trade/fletcher", "Fletcher Trade", 70)
            val LEATHERWORKER = SourceType("minecraft:trade/leatherworker", "Leatherworker Trade", 70)
            val LIBRARIAN = SourceType("minecraft:trade/librarian", "Librarian Trade", 70)
            val MASON = SourceType("minecraft:trade/mason", "Mason Trade", 70)
            val SHEPHERD = SourceType("minecraft:trade/shepherd", "Shepherd Trade", 70)
            val SMITH = SourceType("minecraft:trade/smith", "Smith Trade", 70)
            val TOOLSMITH = SourceType("minecraft:trade/toolsmith", "Toolsmith Trade", 70)
            val WEAPONSMITH = SourceType("minecraft:trade/weaponsmith", "Weaponsmith Trade", 70)
            val WANDERING_TRADER = SourceType("minecraft:trade/wandering_trader", "Wandering Trader", 65)
        }

        data object RecipeTypes {
            val CRAFTING_SHAPELESS = SourceType("minecraft:crafting_shapeless", "Crafting", 95)
            val CRAFTING_SHAPED = SourceType("minecraft:crafting_shaped", "Crafting", 95)
            val CRAFTING_TRANSMUTE = SourceType("minecraft:crafting_transmute", "Crafting", 95)
            val CRAFTING_IMBUE = SourceType("minecraft:crafting_imbue", "Crafting", 95)
            val STONECUTTING = SourceType("minecraft:stonecutting", "Stonecutting", 90)
            val SMELTING = SourceType("minecraft:smelting", "Smelting", 85)
            val CAMPFIRE_COOKING = SourceType("minecraft:campfire_cooking", "Campfire Cooking", 80)
            val SMOKING = SourceType("minecraft:smoking", "Smoking", 80)
            val BLASTING = SourceType("minecraft:blasting", "Blasting", 80)
            val SMITHING_TRANSFORM = SourceType("minecraft:smithing_transform", "Smithing Table", 75)
            val IGNORED = SourceType("mcorg:ignored_recipe", "Ignored Recipe", 0)
        }

        /**
         * Synthetic source types for acquisitions Mojang's JSON doesn't describe — see
         * the SyntheticSources registry in mc-data. [COLLECT] fills a bucket from a world
         * fluid (or breaks ice for water); [IN_WORLD_TRANSFORM] is a deliberate in-world
         * transform such as placing concrete powder next to water.
         */
        data object MechanicTypes {
            val COLLECT = SourceType("mcorg:collect", "Collect", 100)
            val IN_WORLD_TRANSFORM = SourceType("mcorg:in_world_transform", "In-world transformation", 90)
        }

        companion object {
            val UNKNOWN = SourceType("mcorg:unknown", "Unknown", 0)

            /**
             * Resolves a raw source-type id to its [SourceType], returning [UNKNOWN] for
             * unrecognized ids. This is a pure lookup — callers at an extraction boundary
             * (where file/version context is available) are responsible for logging an
             * [UNKNOWN] result if it warrants a diagnostic.
             */
            fun of(id: String): SourceType {
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
                    RecipeTypes.CRAFTING_IMBUE.id -> RecipeTypes.CRAFTING_IMBUE
                    RecipeTypes.SMITHING_TRANSFORM.id -> RecipeTypes.SMITHING_TRANSFORM
                    RecipeTypes.SMELTING.id -> RecipeTypes.SMELTING
                    RecipeTypes.BLASTING.id -> RecipeTypes.BLASTING
                    RecipeTypes.SMOKING.id -> RecipeTypes.SMOKING
                    RecipeTypes.CAMPFIRE_COOKING.id -> RecipeTypes.CAMPFIRE_COOKING
                    RecipeTypes.STONECUTTING.id -> RecipeTypes.STONECUTTING
                    RecipeTypes.IGNORED.id -> RecipeTypes.IGNORED
                    MechanicTypes.COLLECT.id -> MechanicTypes.COLLECT
                    MechanicTypes.IN_WORLD_TRANSFORM.id -> MechanicTypes.IN_WORLD_TRANSFORM
                    TradeTypes.ARMORER.id -> TradeTypes.ARMORER
                    TradeTypes.BUTCHER.id -> TradeTypes.BUTCHER
                    TradeTypes.CARTOGRAPHER.id -> TradeTypes.CARTOGRAPHER
                    TradeTypes.CLERIC.id -> TradeTypes.CLERIC
                    TradeTypes.FARMER.id -> TradeTypes.FARMER
                    TradeTypes.FISHERMAN.id -> TradeTypes.FISHERMAN
                    TradeTypes.FLETCHER.id -> TradeTypes.FLETCHER
                    TradeTypes.LEATHERWORKER.id -> TradeTypes.LEATHERWORKER
                    TradeTypes.LIBRARIAN.id -> TradeTypes.LIBRARIAN
                    TradeTypes.MASON.id -> TradeTypes.MASON
                    TradeTypes.SHEPHERD.id -> TradeTypes.SHEPHERD
                    TradeTypes.SMITH.id -> TradeTypes.SMITH
                    TradeTypes.TOOLSMITH.id -> TradeTypes.TOOLSMITH
                    TradeTypes.WEAPONSMITH.id -> TradeTypes.WEAPONSMITH
                    TradeTypes.WANDERING_TRADER.id -> TradeTypes.WANDERING_TRADER
                    else -> UNKNOWN
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
                RecipeTypes.CRAFTING_IMBUE,
                RecipeTypes.SMITHING_TRANSFORM,
                RecipeTypes.SMELTING,
                RecipeTypes.BLASTING,
                RecipeTypes.SMOKING,
                RecipeTypes.CAMPFIRE_COOKING,
                RecipeTypes.STONECUTTING,
                RecipeTypes.IGNORED,
                MechanicTypes.COLLECT,
                MechanicTypes.IN_WORLD_TRANSFORM,
                TradeTypes.ARMORER,
                TradeTypes.BUTCHER,
                TradeTypes.CARTOGRAPHER,
                TradeTypes.CLERIC,
                TradeTypes.FARMER,
                TradeTypes.FISHERMAN,
                TradeTypes.FLETCHER,
                TradeTypes.LEATHERWORKER,
                TradeTypes.LIBRARIAN,
                TradeTypes.MASON,
                TradeTypes.SHEPHERD,
                TradeTypes.SMITH,
                TradeTypes.TOOLSMITH,
                TradeTypes.WEAPONSMITH,
                TradeTypes.WANDERING_TRADER
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
                    return of(decoder.decodeString())
                }
            }

        }
    }

}