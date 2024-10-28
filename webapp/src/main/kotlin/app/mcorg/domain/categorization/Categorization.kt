package app.mcorg.domain.categorization

import app.mcorg.domain.*
import app.mcorg.domain.categorization.subtypes.*
import app.mcorg.domain.minecraft.BiomeApi
import app.mcorg.domain.minecraft.MobApi

fun createCategories(
    biomeApi: BiomeApi,
    mobApi: MobApi
) = categories {
    common {
        enum("common.difficulty-level", "Difficulty level", DifficultyLevel::class.java)
        versions()
        authors()
        credits()
        boolean("common.locational", "Locational")
        boolean("common.directional", "Directional")
        testResults()
    }
    farms {
        rates()
        consumption()
        boolean("farm.afkable", "Can be AFKed")
        nonNegativeInteger("farm.required-players", "Required amount of players")
        textList("farm.biomes", "Biome", biomeApi.getBiomes().map { it.name })
        text("farm.pros", "Pros", canBeFiltered = false)
        text("farm.cons", "Cons", canBeFiltered = false)
        text("farm.how-to.use", "How to use", longText = true, canBeFiltered = false)
        text("farm.how-to.setup", "How to setup", longText = true, canBeFiltered = false)
        boolean("farm.stackable", "Stackable")
        boolean("farm.tileable", "Tileable")
        size()
        mobs(mobApi.getMobs())
        enumList("farm.player.enchantments", "Player enchantments", PlayerSetup::class.java)
        enumList("farm.beacon-effects", "Beacon effects", BeaconSetup::class.java)
        enumList("farm.y-level", "Y level", YLevel::class.java)
        boolean("farm.requires-sub-chunk-alignment", "Requires sub-chunk alignment")
        FarmSubcategoryType.values().forEach { when(it) {
            FarmSubcategoryType.TREE_FARM -> subCategory(FarmSubcategoryType.TREE_FARM) {
                enumList("farm.tree.type", "Type", TreeFarmType::class.java)
                enumList("farm.tree.size", "Size", TreeFarmSize::class.java)
            }
            FarmSubcategoryType.SHULKER_FARM -> subCategory(FarmSubcategoryType.SHULKER_FARM) {
                boolean("farm.shulker.requires-portal", "Requires portal")
            }
            else -> subCategory(it)
        } }
    }
    storage {
        StorageSubCategoryType.values().forEach { when(it) {
            StorageSubCategoryType.COMPLETE_SYSTEM -> subCategory(StorageSubCategoryType.COMPLETE_SYSTEM) {
                percentage("storage.complete-system.hopper-lock-percentage", "Hopper lock percentage")
                nonNegativeDouble("storage.complete-system.mspt.idle", "Idle mspt")
                nonNegativeDouble("storage.complete-system.mspt.active", "Active mspt")
                nonNegativeInteger("storage.complete-system.input.buffer", "Input buffer")
                nonNegativeInteger("storage.complete-system.input.speed", "Items per hour")
                nonNegativeInteger("storage.complete-system.space.chest-hall.total", "Chest hall space")
                nonNegativeInteger("storage.complete-system.space.chest-hall.item-types", "Chest hall item types")
                nonNegativeInteger("storage.complete-system.space.chest-hall.per-item", "Chest hall space per item")
                nonNegativeInteger("storage.complete-system.space.bulk.total", "Bulk space")
                nonNegativeInteger("storage.complete-system.space.bulk.item-types", "Bulk item types")
                nonNegativeInteger("storage.complete-system.space.bulk.per-item", "Bulk space per item")
                nonNegativeInteger("storage.complete-system.space.multi.total", "Multi item sorter space")
                nonNegativeInteger("storage.complete-system.space.multi.item-types", "Multi item sorter item types")
                nonNegativeInteger("storage.complete-system.space.multi.per-item", "Multi item sorter space per item")
                boolean("storage.complete-system.has-unstackable-sorter", "Has unstackable sorter")
                boolean("storage.complete-system.has-parallel-unloader", "Has parallel unloader")
                enumList("storage.complete-system.peripherals", "Peripherals", SortingSystemPeripherals::class.java)
                boolean("storage.complete-system.debugger", "Has debug information")
                boolean("storage.complete-system.error-helper", "Has error helper")
            }
            StorageSubCategoryType.CHEST_HALL -> subCategory(StorageSubCategoryType.CHEST_HALL) {
                nonNegativeInteger("storage.chest-hall.hoppers-per-slice", "Hoppers per slice")
                percentage("storage.chest-hall.hopper-lock-percentage", "Hopper lock percentage")
                nonNegativeInteger("storage.chest-hall.slice.total", "Total space per slice")
                nonNegativeInteger("storage.chest-hall.slice.item-types", "Item types per slice")
                nonNegativeInteger("storage.chest-hall.slice.per-item", "Space per item type in slice")
            }
            StorageSubCategoryType.BULK_STORAGE -> subCategory(StorageSubCategoryType.BULK_STORAGE) {
                percentage("storage.bulk.hopper-lock-percentage", "Hopper lock percentage")
                nonNegativeInteger("storage.bulk.space.total", "Total item space")
                nonNegativeInteger("storage.bulk.space.item-types", "Total item types")
                nonNegativeInteger("storage.bulk.space.per-item", "Space per item")
            }
            StorageSubCategoryType.TEMPORARY_STORAGE -> subCategory(StorageSubCategoryType.TEMPORARY_STORAGE) {
                percentage("storage.temp.hopper-lock-percentage", "Hopper lock percentage")
                nonNegativeInteger("storage.temp.space.total", "Total item space")
                nonNegativeInteger("storage.temp.space.item-types", "Total item types")
                nonNegativeInteger("storage.temp.space.per-item", "Space per item")
            }
            StorageSubCategoryType.BOX_LOADER -> subCategory(StorageSubCategoryType.BOX_LOADER) {
                enumList("storage.box-loader.tileability", "Tileability", Tileability::class.java)
                boolean("storage.box-loader.has-toggle", "Has toggle state")
                boolean("storage.box-loader.has-buffer", "Has buffer")
                nonNegativeInteger("storage.box-loader.buffer", "Prefill / buffer required")
                nonNegativeInteger("storage.box-loader.speed", "Items per hour")
            }
            StorageSubCategoryType.BOX_UNLOADER -> subCategory(StorageSubCategoryType.BOX_UNLOADER) {
                nonNegativeInteger("storage.box-unloader.speed", "Items per hour")
            }
            StorageSubCategoryType.BOX_SORTER -> subCategory(StorageSubCategoryType.BOX_SORTER) {
                enumList("storage.box-sorter.tileability", "Tileability", Tileability::class.java)
                nonNegativeInteger("storage.box-sorter.speed", "Items per hour")
                percentage("storage.box-sorter.hopper-lock-percentage", "Hopper lock percentage")
            }
            StorageSubCategoryType.ITEM_TRANSPORT -> subCategory(StorageSubCategoryType.ITEM_TRANSPORT) {
                nonNegativeInteger("storage.item-transport.speed", "Items per hour")
            }
            StorageSubCategoryType.UNSTACKABLE_SORTER -> subCategory(StorageSubCategoryType.UNSTACKABLE_SORTER) {
                text("storage.unstackable-sorter.what", "What it sorts") // Should be a prefilled list
            }
            StorageSubCategoryType.PERIPHERALS -> subCategory(StorageSubCategoryType.PERIPHERALS) {
                enumList("storage.peripherals.type", "Type", SortingSystemPeripherals::class.java)
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
                nonNegativeInteger("tnt.duper.amount", "Amount of TNT")
                nonNegativeInteger("tnt.duper.delay", "Delay")
                nonNegativeInteger("tnt.duper.clock", "Clock (gt)")
                nonNegativeInteger("tnt.duper.stack-size", "Stack size")
            }
            TntTechSubCategoryType.COMPRESSORS -> subCategory(it) {
                nonNegativeInteger("tnt.compressor.amount", "Amount of TNT")
            }
            else -> subCategory(it)
        } }
    }
    slimestone {
        SlimestoneSubCategoryType.values().forEach { when(it) {
            SlimestoneSubCategoryType.ENGINE -> subCategory(it) {
                enumList("slimestone.engine.directions", "Directions", SlimestoneEngineType::class.java)
                nonNegativeInteger("slimestone.engine.clock", "Clock (gt)")
            }
            SlimestoneSubCategoryType.ONE_WAY_EXTENSION -> subCategory(it) {
                nonNegativeInteger("slimestone.one-way-extension.timings", "Clock (gt)")
            }
            SlimestoneSubCategoryType.TWO_WAY_EXTENSION -> subCategory(it) {
                nonNegativeInteger("slimestone.two-way-extension.timings", "Clock (gt)")
            }
            SlimestoneSubCategoryType.RETURN_STATION -> subCategory(it) {
                nonNegativeInteger("slimestone.return-station.movable-directions", "Movable directions")
            }
            SlimestoneSubCategoryType.QUARRY -> subCategory(it) {
                nonNegativeInteger("slimestone.quarry.speed", "Minutes / slice")
                percentage("slimestone.quarry.efficiency", "Efficiency (%)")
                nonNegativeInteger("slimestone.quarry.trench.under", "Trench height underneath")
                nonNegativeInteger("slimestone.quarry.trench.side", "Trench width sides")
                nonNegativeInteger("slimestone.quarry.trench.front", "Trench length front")
            }
            SlimestoneSubCategoryType.TRENCHER -> subCategory(it) {
                nonNegativeInteger("slimestone.trencher.width", "Width")
            }
            SlimestoneSubCategoryType.WORLD_EATER -> subCategory(it) {
                nonNegativeInteger("slimestone.world-eater.trench.main", "Main trench width")
                nonNegativeInteger("slimestone.world-eater.trench.side", "Side trench width")
                boolean("slimestone.world-eater.nether", "Can be run in nether")
            }
            SlimestoneSubCategoryType.BEDROCK_BREAKER -> subCategory(it) {
                nonNegativeInteger("slimestone.bedrock-breaker.overhead-north", "Overhead north")
                nonNegativeInteger("slimestone.bedrock-breaker.overhead-east", "Overhead east")
                nonNegativeInteger("slimestone.bedrock-breaker.overhead-west", "Overhead west")
                nonNegativeInteger("slimestone.bedrock-breaker.overhead-west", "Overhead west")
                boolean("slimestone.bedrock-breaker.lossless", "Lossless")
            }
            else -> subCategory(it)
        } }
    }
    other {
        OtherSubCategoryType.values().forEach { subCategory(it) }
    }
}