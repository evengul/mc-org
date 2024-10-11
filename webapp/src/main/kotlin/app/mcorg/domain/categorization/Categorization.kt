package app.mcorg.domain.categorization

import app.mcorg.domain.*
import app.mcorg.domain.categorization.subtypes.*
import app.mcorg.domain.categorization.value.AllowedListValue
import app.mcorg.domain.categorization.value.BooleanValue

fun createCategories() = categories {
    common {
        boolean("common.locational", "Locational")
        boolean("common.directional", "Directional")
        authors()
        versions()
        testResults()
        enum<DifficultyLevel>("common.difficulty-level", "Difficulty level")
    }
    farms {
        boolean("farm.stackable", "Stackable")
        boolean("farm.tileable", "Tileable")
        boolean("farm.requires-sub-chunk-alignment", "Requires sub-chunk alignment")
        boolean("farm.afkable", "Can be AFKed")
        size()
        rates()
        consumption()
        number("farm.required-players", "Required amount of players")
        text("farm.pros", "Pros")
        text("farm.cons", "Cons")
        text("farm.how-to.use", "How to use")
        text("farm.how-to.setup", "How to setup")
        mobs() // TODO: Distinct values. From an API?
        textList("farm.biomes", "Biome") // TODO: Distinct values
        enumList<PlayerSetup>("farm.player.enchantments", "Player enchantments")
        enumList<BeaconSetup>("farm.beacon-effects", "Beacon effects")
        enumList<YLevel>("farm.y-level", "Y level")
        FarmSubcategoryType.values().forEach { when(it) {
            FarmSubcategoryType.TREE_FARM -> subCategory(FarmSubcategoryType.TREE_FARM) {
                value(AllowedListValue<TreeFarmType>("farm.tree.type", "Type"))
                value(AllowedListValue<TreeFarmSize>("farm.tree.type", "Tree size"))
            }
            FarmSubcategoryType.SHULKER_FARM -> subCategory(FarmSubcategoryType.SHULKER_FARM) {
                value(BooleanValue("farm.shulker.requires-portal", "Requires portal"))
            }
            else -> subCategory(it)
        } }
    }
    storage {
        StorageSubCategoryType.values().forEach { when(it) {
            StorageSubCategoryType.COMPLETE_SYSTEM -> subCategory(StorageSubCategoryType.COMPLETE_SYSTEM) {
                number("storage.complete-system.hopper-lock-percentage", "Hopper lock percentage")
                number("storage.complete-system.mspt.idle", "Idle mspt")
                number("storage.complete-system.mspt.active", "Active mspt")
                number("storage.complete-system.input.buffer", "Input buffer")
                number("storage.complete-system.input.speed", "Items per hour")
                number("storage.complete-system.space.chest-hall.total", "Chest hall space")
                number("storage.complete-system.space.chest-hall.item-types", "Chest hall item types")
                number("storage.complete-system.space.chest-hall.per-item", "Chest hall space per item")
                number("storage.complete-system.space.bulk.total", "Bulk space")
                number("storage.complete-system.space.bulk.item-types", "Bulk item types")
                number("storage.complete-system.space.bulk.per-item", "Bulk space per item")
                number("storage.complete-system.space.multi.total", "Multi item sorter space")
                number("storage.complete-system.space.multi.item-types", "Multi item sorter item types")
                number("storage.complete-system.space.multi.per-item", "Multi item sorter space per item")
                boolean("storage.complete-system.has-unstackable-sorter", "Has unstackable sorter")
                boolean("storage.complete-system.has-parallel-unloader", "Has parallel unloader")
                enumList<SortingSystemPeripherals>("storage.complete-system.peripherals", "Peripherals")
                boolean("storage.complete-system.debugger", "Has debug information")
                boolean("storage.complete-system.error-helper", "Has error helper")
            }
            StorageSubCategoryType.CHEST_HALL -> subCategory(StorageSubCategoryType.CHEST_HALL) {
                number("storage.chest-hall.hoppers-per-slice", "Hoppers per slice")
                number("storage.chest-hall.hopper-lock-percentage", "Hopper lock percentage")
                number("storage.chest-hall.slice.total", "Total space per slice")
                number("storage.chest-hall.slice.item-types", "Item types per slice")
                number("storage.chest-hall.slice.per-item", "Space per item type in slice")
            }
            StorageSubCategoryType.BULK_STORAGE -> subCategory(StorageSubCategoryType.BULK_STORAGE) {
                number("storage.bulk.hopper-lock-percentage", "Hopper lock percentage")
                number("storage.bulk.space.total", "Total item space")
                number("storage.bulk.space.item-types", "Total item types")
                number("storage.bulk.space.per-item", "Space per item")
            }
            StorageSubCategoryType.TEMPORARY_STORAGE -> subCategory(StorageSubCategoryType.TEMPORARY_STORAGE) {
                number("storage.temp.hopper-lock-percentage", "Hopper lock percentage")
                number("storage.temp.space.total", "Total item space")
                number("storage.temp.space.item-types", "Total item types")
                number("storage.temp.space.per-item", "Space per item")
            }
            StorageSubCategoryType.BOX_LOADER -> subCategory(StorageSubCategoryType.BOX_LOADER) {
                enumList<Tileability>("storage.box-loader.tileability", "Tileability")
                boolean("storage.box-loader.has-toggle", "Has toggle state")
                boolean("storage.box-loader.has-buffer", "Has buffer")
                number("storage.box-loader.buffer", "Prefill / buffer required")
                number("storage.box-loader.speed", "Items per hour")
            }
            StorageSubCategoryType.BOX_UNLOADER -> subCategory(StorageSubCategoryType.BOX_UNLOADER) {
                number("storage.box-unloader.speed", "Items per hour")
            }
            StorageSubCategoryType.BOX_SORTER -> subCategory(StorageSubCategoryType.BOX_SORTER) {
                enumList<Tileability>("storage.box-sorter.tileability", "Tileability")
                number("storage.box-sorter.speed", "Items per hour")
                number("storage.box-sorter.hopper-lock-percentage", "Hopper lock percentage")
            }
            StorageSubCategoryType.ITEM_TRANSPORT -> subCategory(StorageSubCategoryType.ITEM_TRANSPORT) {
                number("storage.item-transport.speed", "Items per hour")
            }
            StorageSubCategoryType.UNSTACKABLE_SORTER -> subCategory(StorageSubCategoryType.UNSTACKABLE_SORTER) {
                text("storage.unstackable-sorter.what", "What it sorts") // Should be a prefilled list
            }
            StorageSubCategoryType.PERIPHERALS -> subCategory(StorageSubCategoryType.PERIPHERALS) {
                enumList<SortingSystemPeripherals>("storage.peripherals.type", "Type")
            }
            else -> subCategory(it)
        }}
    }
    cartTech {
        CartTechSubCategoryType.values().forEach { subCategory(it) }
    }
    tntTech {
        TntTechSubCategoryType.values().forEach { when(it) {
            TntTechSubCategoryType.DUPERS -> subCategory(it) {
                boolean("tnt.duper.movable", "Movable")
                boolean("tnt.duper.flat", "Flat / Not Y Tilable")
                number("tnt.duper.amount", "Amount of TNT")
                number("tnt.duper.delay", "Delay")
                number("tnt.duper.clock", "Clock (gt)")
                number("tnt.duper.stack-size", "Stack size")
            }
            TntTechSubCategoryType.COMPRESSORS -> subCategory(it) {
                number("tnt.compressor.amount", "Amount of TNT")
            }
            else -> subCategory(it)
        } }
    }
    slimestone {
        SlimestoneSubCategoryType.values().forEach { when(it) {
            SlimestoneSubCategoryType.ENGINE -> subCategory(it) {
                enumList<SlimestoneEngineType>("slimestone.engine.directions", "Directions")
                number("slimestone.engine.clock", "Clock (gt)")
            }
            SlimestoneSubCategoryType.ONE_WAY_EXTENSION -> subCategory(it) {
                number("slimestone.one-way-extension.timings", "Clock (gt)")
            }
            SlimestoneSubCategoryType.TWO_WAY_EXTENSION -> subCategory(it) {
                number("slimestone.two-way-extension.timings", "Clock (gt)")
            }
            SlimestoneSubCategoryType.RETURN_STATION -> subCategory(it) {
                number("slimestone.return-station.movable-directions", "Movable directions")
            }
            SlimestoneSubCategoryType.QUARRY -> subCategory(it) {
                number("slimestone.quarry.speed", "Minutes / slice")
                number("slimestone.quarry.efficiency", "Efficiency (%)")
                number("slimestone.quarry.trench.under", "Trench height underneath")
                number("slimestone.quarry.trench.side", "Trench width sides")
                number("slimestone.quarry.trench.front", "Trench length front")
            }
            SlimestoneSubCategoryType.TRENCHER -> subCategory(it) {
                number("slimestone.trencher.width", "Width")
            }
            SlimestoneSubCategoryType.WORLD_EATER -> subCategory(it) {
                number("slimestone.world-eater.trench.main", "Main trench width")
                number("slimestone.world-eater.trench.side", "Side trench width")
                boolean("slimestone.world-eater.nether", "Can be run in nether")
            }
            SlimestoneSubCategoryType.BEDROCK_BREAKER -> subCategory(it) {
                number("slimestone.bedrock-breaker.overhead-north", "Overhead north")
                number("slimestone.bedrock-breaker.overhead-east", "Overhead east")
                number("slimestone.bedrock-breaker.overhead-west", "Overhead west")
                number("slimestone.bedrock-breaker.overhead-west", "Overhead west")
                boolean("slimestone.bedrock-breaker.lossless", "Lossless")
            }
            else -> subCategory(it)
        } }
    }
    other {
        OtherSubCategoryType.values().forEach { subCategory(it) }
    }
}