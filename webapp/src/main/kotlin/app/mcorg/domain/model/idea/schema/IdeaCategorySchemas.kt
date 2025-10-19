package app.mcorg.domain.model.idea.schema

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.schema.builders.ideaCategory

/**
 * Single Source of Truth for Idea Category Schemas.
 * All category-specific fields, filters, and validation rules are defined here.
 *
 * This configuration drives:
 * - Database JSONB structure
 * - Form generation and validation
 * - Search and filter UI generation
 * - API request/response handling
 */
object IdeaCategorySchemas {

    val FARM = ideaCategory(IdeaCategory.FARM) {
        // Farm version tracking
        textField("farmVersion") {
            label = "Farm Version"
            searchable = true
            helpText = "Version identifier for this farm design (e.g., v3.2, Mark-IV)"
        }

        // Production rates
        rateField("productionRate") {
            label = "Production Rate"
            filterable = true
            required = true
            unit = "items/hour"
            min = 0.0
        }

        listField("alternativeRates") {
            label = "Alternative Rates (Modes/Switches)"
            helpText = "Different production rates for various modes"
            itemLabel = "Rate Configuration"
        }

        rateField("consumptionRate") {
            label = "Consumption Rate"
            filterable = true
            unit = "items/hour"
            min = 0.0
        }

        // Physical dimensions
        dimensionsField("size") {
            label = "Size (X × Y × Z)"
            filterable = true
            required = true
        }

        booleanField("stackable") {
            label = "Stackable"
            filterable = true
            defaultValue = false
        }

        booleanField("tileable") {
            label = "Tileable"
            filterable = true
            defaultValue = false
        }

        // Location requirements
        numberField("yLevel") {
            label = "Required Y-Level"
            filterable = true
            helpText = "Specific Y-level requirement (if any)"
        }

        booleanField("subChunkAligned") {
            label = "Sub-chunk Aligned"
            filterable = true
            defaultValue = false
            helpText = "Must be aligned to 16×16×16 sub-chunk boundaries"
        }

        multiSelectField("biomes") {
            label = "Compatible Biomes"
            filterable = true
            options = listOf(
                "Plains", "Forest", "Taiga", "Swamp", "Desert", "Savanna",
                "Jungle", "Badlands", "Mushroom Fields", "Ocean", "River",
                "Nether Wastes", "Crimson Forest", "Warped Forest", "Soul Sand Valley",
                "Basalt Deltas", "The End", "Any"
            )
        }

        mapField("mobRequirements") {
            label = "Mob Requirements"
            keyLabel = "Mob Type"
            valueLabel = "Amount Required"
            helpText = "Specific mob spawning requirements"
        }

        // Player requirements
        listField("playerSetup") {
            label = "Player Setup Requirements"
            allowedValues = listOf(
                "Looting III Sword", "Fire Aspect II", "Sweeping Edge III",
                "Strength II Potion", "Speed II Potion", "Regeneration",
                "Full Diamond Armor", "Full Netherite Armor", "Elytra",
                "Trident with Loyalty", "Bow with Power V", "Shield"
            )
        }

        listField("beaconSetup") {
            label = "Beacon Setup Requirements"
            allowedValues = listOf(
                "Speed I", "Speed II", "Haste I", "Haste II",
                "Strength I", "Strength II", "Jump Boost I", "Jump Boost II",
                "Regeneration I", "Regeneration II", "Resistance I", "Resistance II"
            )
        }

        // Usage information
        textField("howToUse") {
            label = "How to Use"
            searchable = true
            multiline = true
            required = true
            helpText = "Instructions for operating the farm"
        }

        booleanField("afkable") {
            label = "AFK-able"
            filterable = true
            defaultValue = false
            helpText = "Can be used while AFK"
        }

        selectField("playersRequired") {
            label = "Players Required"
            filterable = true
            required = true
            options = listOf("0 (Automatic)", "1", "2", "3+")
            defaultValue = "1"
        }

        listField("pros") {
            label = "Pros"
            itemLabel = "Advantage"
            helpText = "List of advantages"
        }

        listField("cons") {
            label = "Cons"
            itemLabel = "Disadvantage"
            helpText = "List of disadvantages"
        }

        booleanField("directional") {
            label = "Directional"
            filterable = true
            defaultValue = false
            helpText = "Must face a specific direction"
        }

        booleanField("locational") {
            label = "Locational"
            filterable = true
            defaultValue = false
            helpText = "Must be built in a specific location type"
        }

        // Tree farm subcategory
        subcategory("treeFarm") {
            multiSelectField("treeTypes") {
                label = "Tree Types"
                filterable = true
                required = true
                options = listOf(
                    "Warped", "Crimson", "Birch", "Oak", "Dark Oak",
                    "Spruce", "Cherry", "Azalea", "Jungle", "Acacia",
                    "Brown Mushrooms", "Red Mushrooms", "Mangrove"
                )
            }
        }
    }

    val STORAGE = ideaCategory(IdeaCategory.STORAGE) {
        // Complete storage systems
        subcategory("completeSystem") {
            percentageField("hopperLockPercentage") {
                label = "Hopper Lock Percentage"
                filterable = true
                min = 0.0
                max = 1.0
            }

            numberField("idleMspt") {
                label = "Idle MSPT Usage"
                filterable = true
                min = 0.0
                helpText = "Milliseconds per tick when idle"
            }

            numberField("activeMspt") {
                label = "Active MSPT Usage"
                filterable = true
                min = 0.0
                helpText = "Milliseconds per tick when active"
            }

            rateField("inputSpeed") {
                label = "Input Speed"
                filterable = true
                unit = "items/hour"
            }

            numberField("inputBufferSize") {
                label = "Input Buffer Size"
                filterable = true
                min = 0.0
            }

            numberField("chestHallSpaceItemAmount") {
                label = "Chest Hall Space Item Amount"
                filterable = true
            }

            numberField("chestHallSpacePerItem") {
                label = "Chest Hall Space Per Item"
                filterable = true
            }

            numberField("bulkItemAmount") {
                label = "Bulk Item Amount"
                filterable = true
            }

            numberField("bulkSpacePerItem") {
                label = "Bulk Space Per Item"
                filterable = true
            }

            numberField("multiItemCategories") {
                label = "Multi Item Categories"
                filterable = true
            }

            booleanField("unstackableSorter") {
                label = "Unstackable Sorter"
                filterable = true
            }

            booleanField("parallelUnloader") {
                label = "Parallel Unloader"
                filterable = true
            }

            multiSelectField("peripherals") {
                label = "Peripherals"
                filterable = true
                options = listOf(
                    "Furnace Array", "Crafting Station", "Nano Farms",
                    "Auto-Smelting", "Auto-Crafting", "Potion Station"
                )
            }

            booleanField("debugHelp") {
                label = "Debug Help"
                filterable = true
                helpText = "Includes debugging features"
            }
        }

        // Box loader
        subcategory("boxLoader") {
            selectField("tileable") {
                label = "Tileable Configuration"
                filterable = true
                options = listOf("1", "2", "3", "AB", "ABC", "Not Tileable")
            }

            booleanField("noToggle") {
                label = "No Toggle"
                filterable = true
            }

            booleanField("noBuffer") {
                label = "No Buffer"
                filterable = true
            }

            numberField("prefillNeeded") {
                label = "Prefill Needed"
                filterable = true
                min = 0.0
            }

            rateField("speed") {
                label = "Speed"
                filterable = true
                unit = "items/hour"
            }
        }

        // Box unloader
        subcategory("boxUnloader") {
            rateField("speed") {
                label = "Speed"
                filterable = true
                unit = "items/hour"
            }
        }

        // Box sorters
        subcategory("boxSorter") {
            selectField("tilability") {
                label = "Tilability"
                filterable = true
                options = listOf("1", "2", "3", "AB", "ABC", "Not Tileable")
            }

            rateField("speed") {
                label = "Speed"
                filterable = true
                unit = "items/hour"
            }

            percentageField("lockedHoppers") {
                label = "Locked Hoppers Percentage"
                filterable = true
            }
        }

        // Item transport
        subcategory("itemTransport") {
            textField("type") {
                label = "Transport Type"
                searchable = true
                required = true
                helpText = "E.g., Aligner, Instant downward dropper, etc."
            }
        }

        // Fixed item sorters
        subcategory("fixedItemSorter") {
            rateField("speed") {
                label = "Speed"
                filterable = true
                unit = "items/hour"
            }

            selectField("tilability") {
                label = "Tilability"
                filterable = true
                options = listOf("1", "2", "3", "AB", "ABC", "Not Tileable")
            }
        }

        // Multi item sorter
        subcategory("multiItemSorter") {
            numberField("amountPerSlice") {
                label = "Amount Per Slice"
                filterable = true
            }

            booleanField("compatible64_16") {
                label = "64/16 Compatible"
                filterable = true
            }

            rateField("speed") {
                label = "Speed"
                filterable = true
                unit = "items/hour"
            }
        }

        // Unstackable sorter
        subcategory("unstackableSorter") {
            textField("whatItSorts") {
                label = "What It Sorts"
                searchable = true
                required = true
            }
        }

        // Chest hall
        subcategory("chestHall") {
            numberField("hoppersPerSlice") {
                label = "Hoppers Per Slice"
                filterable = true
            }

            percentageField("lockedPercentage") {
                label = "Locked Percentage"
                filterable = true
            }

            numberField("capacityForItemType") {
                label = "Capacity For Item Type"
                filterable = true
            }

            numberField("prefillAmount") {
                label = "Prefill Amount"
                filterable = true
            }

            numberField("itemTypesPerSlice") {
                label = "Item Types Per Slice"
                filterable = true
            }
        }

        // Bulk storage
        subcategory("bulkStorage") {
            percentageField("hopperLock") {
                label = "Hopper Lock Percentage"
                filterable = true
            }

            numberField("capacity") {
                label = "Capacity"
                filterable = true
            }

            numberField("buffer") {
                label = "Buffer"
                filterable = true
            }
        }

        // Temporary storage
        subcategory("temporaryStorage") {
            numberField("capacity") {
                label = "Capacity"
                filterable = true
            }

            percentageField("lockPercentage") {
                label = "Lock Percentage"
                filterable = true
            }
        }

        // Peripherals
        subcategory("peripheral") {
            textField("type") {
                label = "Peripheral Type"
                searchable = true
                required = true
                helpText = "E.g., crafting, smelting, brewing, etc."
            }
        }
    }

    val CART_TECH = ideaCategory(IdeaCategory.CART_TECH) {
        selectField("cartTechType") {
            label = "Cart Tech Type"
            filterable = true
            required = true
            options = listOf(
                "Storage", "Transport", "Computational", "Farm Collection",
                "Furnace Arrays", "Item Whitelisters", "Loading/Unloading",
                "Stackers and Unstackers", "Stationary", "Villagers++", "Other"
            )
        }

        textField("description") {
            label = "Description"
            searchable = true
            multiline = true
            required = true
        }
    }

    val TNT = ideaCategory(IdeaCategory.TNT) {
        // TNT Dupers
        subcategory("tntDuper") {
            booleanField("movable") {
                label = "Movable"
                filterable = true
            }

            booleanField("flatNotYTileable") {
                label = "Flat / Not Y Tileable"
                filterable = true
            }

            numberField("amountOfTnt") {
                label = "Amount of TNT"
                filterable = true
                min = 1.0
            }

            numberField("delayClock") {
                label = "Delay Clock (ticks)"
                filterable = true
                min = 0.0
            }

            numberField("stackSize") {
                label = "Stack Size"
                filterable = true
                min = 1.0
            }
        }

        // TNT Compressors
        subcategory("tntCompressor") {
            numberField("amountOfTnt") {
                label = "Amount of TNT"
                filterable = true
                required = true
                min = 1.0
            }
        }

        // Other TNT types
        selectField("tntType") {
            label = "TNT Type"
            filterable = true
            options = listOf(
                "TNT Duper", "TNT Compressor", "Transport Cannon",
                "Digging Cannon", "Arrow Cannon", "Weaponry", "Other"
            )
        }

        textField("description") {
            label = "Description"
            searchable = true
            multiline = true
        }
    }

    val SLIMESTONE = ideaCategory(IdeaCategory.SLIMESTONE) {
        // Engine
        subcategory("engine") {
            numberField("directions") {
                label = "Directions (1-4)"
                filterable = true
                min = 1.0
                max = 4.0
                helpText = "Number of directions the engine can move"
            }

            numberField("gtEngine") {
                label = "GT Engine"
                filterable = true
                helpText = "Game tick engine count"
            }
        }

        // 1-way extensions
        subcategory("oneWayExtension") {
            numberField("timings") {
                label = "Timings"
                filterable = true
            }

            numberField("gameTicks") {
                label = "Game Ticks"
                filterable = true
            }
        }

        // 2-way extensions
        subcategory("twoWayExtension") {
            numberField("timings") {
                label = "Timings"
                filterable = true
            }
        }

        // Return station
        subcategory("returnStation") {
            numberField("directions") {
                label = "Directions (1-3)"
                filterable = true
                min = 1.0
                max = 3.0
            }
        }

        // Quarry
        subcategory("quarry") {
            numberField("speedPerSlice") {
                label = "Speed Per Slice (minutes)"
                filterable = true
                min = 0.0
                suffix = "minutes"
            }

            percentageField("collectionRate") {
                label = "Collection Rate"
                filterable = true
                helpText = "Percentage of items collected"
            }

            numberField("trenchesUnder") {
                label = "Trenches Under"
                filterable = true
            }

            numberField("trenchesSide") {
                label = "Trenches Side"
                filterable = true
            }

            numberField("trenchesMain") {
                label = "Trenches Main"
                filterable = true
            }
        }

        // Trenchers
        subcategory("trencher") {
            numberField("width") {
                label = "Width"
                filterable = true
                required = true
            }
        }

        // World Eater
        subcategory("worldEater") {
            numberField("mainTrenches") {
                label = "Main Trenches"
                filterable = true
                required = true
            }

            numberField("sideTrenches") {
                label = "Side Trenches"
                filterable = true
                required = true
            }

            booleanField("handlesAncientDebris") {
                label = "Handles Ancient Debris"
                filterable = true
            }
        }

        // Bedrock breaker
        subcategory("bedrockBreaker") {
            booleanField("overhead") {
                label = "Overhead"
                filterable = true
            }

            booleanField("lossless") {
                label = "Lossless"
                filterable = true
            }
        }

        selectField("slimestoneType") {
            label = "Slimestone Type"
            filterable = true
            options = listOf(
                "Engine", "1-Way Extension", "2-Way Extension", "Return Station",
                "Quarry", "Trencher", "Tunnel Bore", "World Eater",
                "Liquid Sweeper", "Bedrock Breaker", "Building Machine", "Other"
            )
        }

        textField("description") {
            label = "Description"
            searchable = true
            multiline = true
        }
    }

    val OTHER = ideaCategory(IdeaCategory.OTHER) {
        selectField("otherType") {
            label = "Contraption Type"
            filterable = true
            options = listOf(
                "Entity Transport", "Furnaces", "Brewers",
                "Instant Wires", "Logic Computation", "Other"
            )
        }

        // Furnaces
        numberField("furnaceAmount") {
            label = "Amount of Furnaces"
            filterable = true
            min = 1.0
        }

        // Brewers
        numberField("brewingSpeed") {
            label = "Brewing Speed"
            filterable = true
            helpText = "Potions per hour or similar metric"
        }

        textField("purpose") {
            label = "Purpose"
            searchable = true
            required = true
            multiline = true
            helpText = "Describe what this contraption does"
        }

        multiSelectField("tags") {
            label = "Custom Tags"
            filterable = true
            options = listOf(
                "Decoration", "Redstone", "Command Blocks", "Data Packs",
                "Aesthetics", "Utility", "Experiment", "Proof of Concept"
            )
        }
    }

    val BUILD = ideaCategory(IdeaCategory.BUILD) {
        dimensionsField("dimensions") {
            label = "Dimensions"
            searchable = true
            helpText = "Approximate size of the build"
        }

        selectField("buildStyle") {
            label = "Build Style"
            filterable = true
            options = listOf(
                "Medieval", "Modern", "Fantasy", "Steampunk", "Sci-Fi",
                "Oriental", "Victorian", "Rustic", "Industrial", "Organic"
            )
        }

        multiSelectField("materials") {
            label = "Primary Materials"
            filterable = true
            options = listOf(
                "Stone", "Wood", "Concrete", "Terracotta", "Glass",
                "Metal", "Prismarine", "Nether Materials", "End Materials", "Other"
            )
        }
    }

    // Schema registry
    private val schemas = mapOf(
        IdeaCategory.FARM to FARM,
        IdeaCategory.STORAGE to STORAGE,
        IdeaCategory.CART_TECH to CART_TECH,
        IdeaCategory.TNT to TNT,
        IdeaCategory.SLIMESTONE to SLIMESTONE,
        IdeaCategory.OTHER to OTHER,
        IdeaCategory.BUILD to BUILD
    )

    fun getSchema(category: IdeaCategory): IdeaCategorySchema =
        schemas[category] ?: error("No schema defined for category: $category")

    fun getAllSchemas(): Map<IdeaCategory, IdeaCategorySchema> = schemas
}

