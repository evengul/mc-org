package app.mcorg.domain.minecraft

import java.util.*

data class Item(
    val name: String,
    val labels: MutableSet<ItemLabel> = mutableSetOf()
)

interface ItemLabel {
    val name: String
}

fun ItemLabel.displayName(): String {
    return name.split("_")
        .joinToString(" ")
            { it -> it.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } }
}

interface VariantLabel : ItemLabel

enum class Group : ItemLabel {
    DIRT,
    ORE_BLOCK,
    STONE,
    WOOD,
    SAND,
    SAPLING
}

enum class Plant : ItemLabel {
    PLANT,
    DYE_PLANT,
    FOOD_PLANT,
    SAPLING,
    GROWS_NATURALLY,
    GROWS_FORCED
}

enum class Shape : ItemLabel {
    FULL_BLOCK,
    HALF_SLAB,
    STAIRS,
    WALL,
    OTHER
}

enum class UseCaseLabel : ItemLabel {
    BUILDING,
    REDSTONE,
    LIGHTING,
    EATABLE,
    RIDEABLE,
    SMELTABLE,
    HOEABLE
}

enum class ToolLabel : ItemLabel {
    SHOVEL,
    AXE,
    SWORD,
    PICKAXE,
    HOE
}

enum class ToolMaterialLabel : VariantLabel {
    WOOD,
    STONE,
    IRON,
    DIAMOND,
    NETHERITE
}

enum class ArmorLabel : ItemLabel {
    HELMET,
    CHESTPLATE,
    ELYTRA,
    LEGGINGS,
    BOOTS,
    HORSE
}

enum class ArmorMaterialLabel : VariantLabel {
    SCUTE,
    LEATHER,
    CHAIN_MAIL,
    IRON,
    DIAMOND,
    NETHERITE
}

enum class RedstoneLabel : ItemLabel {
    EMITS_POWER,
    TRANSMITS_POWER,
    RAIL,
    MINECART,
    READS_POWER,
    EMITS_ITEM,
    TRANSMITS_ITEM,
    TRANSPARENT,
    STICKY,
    GLAZED,
    IMMOVABLE,
    ENTITY_INTERACTION,
    PLAYER_INTERACTION,
    REACTS
}

enum class TransportationLabel : ItemLabel {
    MINECART,
    ELYTRA,
    BOAT,
    CARROT_ON_STICK,
    WARPED_FUNGUS_ON_STICK
}

enum class StoneVariant : VariantLabel {
    COBBLED,
    SMOOTH,
    BRICKS,
    PILLAR,
    TILES,
    MOSSY,
    CRACKED,
    POLISHED,
    STRIPPED,
    CHISELED,
    CUT,
    INFESTED
}

enum class TerracottaVariantLabel : VariantLabel {GLAZED}
enum class ConcreteVariantLabel : VariantLabel {POWDER}
enum class CoralVariantLabel : VariantLabel {DEAD}
enum class BlackstoneVariantLabel : VariantLabel {GILDED}

enum class FoodVariantLabel : VariantLabel {RAW, COOKED, SEED, ROTTEN, POISONOUS, GOLDEN}
enum class BucketVariantLabel : VariantLabel {EMPTY, LAVA, WATER, MILK, POWDER_SNOW, WATER_WITH_ENTITY}
enum class CampFireVariantLabel : VariantLabel {NORMAL, SOUL}

enum class CopperOxidisation : VariantLabel {
    WEATHERED,
    OXIDIZED,
    EXPOSED,
    WAXED
}

enum class AmethystSize : VariantLabel {
    SMALL,
    MEDIUM,
    LARGE
}

enum class FroglightVariant : VariantLabel {
    OCHRE,
    VERDANT,
    PEARLESCENT
}

enum class GlassVariantLabel : VariantLabel {
    BLOCK,
    PANE,
    STAINED_BLOCK,
    STAINED_PANE
}

enum class IceVariantLabel : VariantLabel {
    NORMAL,
    PACKED,
    BLUE
}

enum class OreVariant : VariantLabel {
    COAL,
    IRON,
    COPPER,
    GOLD,
    REDSTONE,
    EMERALD,
    LAPIS_LAZULI,
    DIAMOND,
    QUARTZ,
    ANCIENT_DEBRIS
}

enum class WoodVariant : VariantLabel {
    PLANKS,
    LOG,
    STRIPPED,
    WOOD,
    SAPLING
}

enum class WoodType : VariantLabel {
    CRIMSON,
    WARPED,
    OAK,
    SPRUCE,
    BIRCH,
    JUNGLE,
    ACACIA,
    DARK_OAK,
    MANGROVE,
    AZALEA
}

enum class FlowerHeightLabel : ItemLabel {
    ONE_HIGH,
    TWO_HIGH,
    THREE_PLUS_HIGH
}

enum class Color : VariantLabel {
    WHITE,
    BLACK,
    RED,
    ORANGE,
    MAGENTA,
    BLUE,
    LIGHT_BLUE,
    YELLOW,
    LIME,
    PINK,
    GRAY,
    LIGHT_GRAY,
    CYAN,
    PURPLE,
    BROWN,
    GREEN,
    TINTED,
    DARK
}

enum class AnvilDamageLabel : ItemLabel {
    NOT_DAMAGED,
    CHIPPED,
    DAMAGED
}

enum class VillagerLabel : ItemLabel {
    WORKSTATION,
    SUMMONS
}

enum class ObtainableLabel : ItemLabel {
    NOT_OBTAINABLE,
    MANUAL_WORK,
    TRADING,
    BARTERING,
    ACTIVE_FARM,
    AFK_FARM,
    CHUNK_LOADED_FARM
}

enum class StackableLabel : ItemLabel {
    ONE,
    SIXTEEN,
    SIXTY_FOUR
}