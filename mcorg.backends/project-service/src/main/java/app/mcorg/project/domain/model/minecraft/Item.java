package app.mcorg.project.domain.model.minecraft;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

import static app.mcorg.project.domain.model.minecraft.ItemCategory.*;
import static app.mcorg.project.domain.model.minecraft.ItemType.*;

@Getter
public enum Item {
    NETHER_BRICKS(BUILDING_BLOCKS, "Nether Bricks"),
    SLAB(BUILDING_BLOCKS, STONE_VARIANTS, "Quartz Slab", "Smooth Stone Slab"),
    GLASS(BUILDING_BLOCKS, COLOR, "White Stained Glass", "Black Stained Glass", "Blue Stained Glass"),
    OBSERVER(REDSTONE, "Observer"),
    LEAVES(NATURE, ItemType.WOOD, "Oak Leaves", "Spruce Leaves"),
    IRON_BLOCK(BUILDING_BLOCKS, "Block of Iron"),
    REPEATER(REDSTONE, "Redstone Repeater"),
    STICKY_PISTON(REDSTONE, "Sticky Piston"),
    PISTON(REDSTONE, "Piston"),
    REDSTONE_DUST(REDSTONE, "Redstone Dust"),
    TRAPDOOR(BUILDING_BLOCKS, PRESSURE_INPUT, "Dark Oak Trapdoor", "Iron Trapdoor", "Oak Trapdoor"),
    POWERED_RAIL(REDSTONE, "Powered Rail"),
    SLIME_BLOCK(REDSTONE, "Slime Block"),
    HOPPER(REDSTONE, "Hopper"),
    NOTE_BLOCK(REDSTONE, "Noteblock"),
    BUTTON(REDSTONE, PRESSURE_INPUT, "Stone Button"),
    PRESSURE_PLATE(REDSTONE, PRESSURE_INPUT, "Stone Pressure Plate"),
    COMPARATOR(REDSTONE, "Redstone Comparator"),
    STAIRS(BUILDING_BLOCKS, STONE_VARIANTS, "Quartz Stairs", "Prismarine Brick Stairs"),
    MAGMA_BLOCK(BUILDING_BLOCKS, "Magma Block"),
    DROPPER(REDSTONE, "Dropper"),
    DISPENSER(REDSTONE, "Dispenser"),
    ANDESITE(BUILDING_BLOCKS, STONE_VARIANTS, "Polished Andesite"),
    OBSIDIAN(BUILDING_BLOCKS, "Obsidian"),
    REDSTONE_BLOCK(REDSTONE, "Block of Redstone"),
    TURTLE_EGG(NATURE, "Turtle Egg"),
    CAULDRON(REDSTONE, "Cauldron"),
    SAND(BUILDING_BLOCKS, "Sand"),
    SNOW(BUILDING_BLOCKS, "Snow"),
    CACTUS(NATURE, "Cactus"),
    CAKE(REDSTONE, "Cake"),
    ANVIL(BUILDING_BLOCKS, "Anvil"),
    ENCHANTING_TABLE(BUILDING_BLOCKS, "Enchanting Table"),
    TNT(REDSTONE, "TNT"),
    DEAD_CORAL_FAN(BUILDING_BLOCKS, COLOR, "Dead Fire Coral Fan"),
    FENCE_GATE(BUILDING_BLOCKS, ItemType.WOOD, "Oak Fence Gate", "Acacia Fence Gate"),
    DETECTOR_RAIL(REDSTONE, "Detector Rail"),
    WALL(BUILDING_BLOCKS, STONE_VARIANTS, "Cobblestone Walls"),
    WOOD(NATURE, ItemType.WOOD, "Spruce Wood"),
    ENDER_CHEST(OTHER, "Ender Chest"),
    CHEST(OTHER, ItemType.WOOD, "Chest"),
    SIGN(BUILDING_BLOCKS, ItemType.WOOD, "Oak Sign", "Dark Oak Sign"),
    IRON_BARS(BUILDING_BLOCKS, "Iron Bars"),
    COMMAND_BLOCK(REDSTONE, "Command Block"),
    PLAYER_HEAD(OTHER, "Player Head", "Wither Skeleton Skull"),
    BLUE_ICE(BUILDING_BLOCKS, "Blue Ice"),
    LEVER(REDSTONE, "Lever"),
    REDSTONE_TORCH(REDSTONE, "Redstone Torch"),
    SHULKER_BOX(OTHER, COLOR, "Green Shulker Box"),
    REDSTONE_LAMP(REDSTONE, "Redstone Lamp"),
    LAVA_BUCKET(OTHER, "Lava Bucket"),
    WATER_BUCKET(OTHER, "Water Bucket"),
    ICE(OTHER, "Ice"),
    REPEATING_COMMAND_BLOCK(REDSTONE, "Repeating Command Block"),
    WOOL(BUILDING_BLOCKS, COLOR, "Brown Wool"),
    GLASS_PANE(BUILDING_BLOCKS, COLOR, "White Stained Glass Pane");

    Item(ItemCategory category, String... possibleNames) {
        this.category = category;
        this.possibleNames = possibleNames;
        this.itemType = null;
    }

    Item(ItemCategory category, ItemType itemType, String... possibleNames) {
        this.category = category;
        this.possibleNames = possibleNames;
        this.itemType = itemType;
    }

    public static Optional<Item> fromName(String name) {
        return Arrays.stream(Item.values())
                .filter(value -> Arrays.asList(value.possibleNames).contains(name))
                .findFirst();
    }

    private final ItemCategory category;

    private final ItemType itemType;

    private final String[] possibleNames;
}
