package app.mcorg.domain.model.idea.schema

import app.mcorg.domain.model.idea.IdeaCategory
import app.mcorg.domain.model.idea.schema.builders.IdeaCategorySchemaBuilder
import app.mcorg.domain.model.idea.schema.builders.ideaCategory
import app.mcorg.presentation.templated.dsl.SearchableSelectOption

/**
 * Single Source of Truth for Idea Category Schemas.
 *
 * This configuration drives:
 * - Database JSONB structure
 * - Form generation and validation
 * - Search and filter UI generation
 *
 * **Deliberately loose (MCO-204).** Every category keeps only the handful of fields that are
 * universal, genuinely filterable, or consumed by the product (item requirements, size, version).
 * Everything else goes in the free-form [specs] block. Promote a spec to a first-class typed
 * field when it shows up consistently in real submissions — not before. Designing a tight schema
 * from a handful of hand-entered ideas is guessing, and guessing costs import speed.
 *
 * Note that name, description, author, labels, difficulty and version range live on the Idea
 * itself — never duplicate them here.
 */
object IdeaCategorySchemas {

    /**
     * Free-form label -> value block. Replaces the long tail of speculative typed fields.
     * Not filterable by design: there is no fixed key set to filter on.
     */
    private fun IdeaCategorySchemaBuilder.specs() = typedMapField("specs") {
        label = "Specs"
        helpText = "Anything measurable about this design, e.g. \"TNT per piston: 10\" or \"Remaining fuse: 21gt\""
        types {
            textKey {
                label = "Spec"
                placeholder = "e.g., TNT per piston"
            }
            textValue {
                label = "Value"
                placeholder = "e.g., 10"
            }
        }
    }

    /** Videos, schematic downloads, forum threads — wherever this design came from. */
    private fun IdeaCategorySchemaBuilder.references() = listField("references") {
        label = "Reference Links"
        itemLabel = "Link"
        helpText = "Comma-separated URLs"
    }

    /**
     * Optional footprint. Sub-fields are optional too, so a partially known size still saves.
     */
    private fun IdeaCategorySchemaBuilder.sizeField() = structField("size") {
        label = "Size (X × Y × Z)"
        fields {
            numberField("x") { label = "X Dimension" }
            numberField("y") { label = "Y Dimension" }
            numberField("z") { label = "Z Dimension" }
        }
    }

    private fun IdeaCategorySchemaBuilder.tileable() = booleanField("tileable") {
        label = "Tileable"
        filterable = true
        defaultValue = false
    }

    private fun IdeaCategorySchemaBuilder.directional() = booleanField("directional") {
        label = "Directional"
        filterable = true
        defaultValue = false
        helpText = "Must face a specific direction"
    }

    val FARM = ideaCategory(IdeaCategory.FARM) {
        sizeField()

        /**
         * The one structured field the engine will consume: MCO-294 matches bulk raw demand
         * against farm output. Flat item -> rate; the old Mode -> (Item -> Rate) nesting was
         * the single most tedious thing in the form and never got filled.
         */
        typedMapField("productionRate") {
            label = "Production Rate"
            helpText = "What this farm produces, per hour"
            types {
                selectKey {
                    label = "Item"
                    dynamicOptionsConfig = DynamicOptionsConfig.items()
                }
                rateValue {
                    label = "Rate"
                    min = 0.0
                }
            }
        }

        tileable()
        directional()

        booleanField("afkable") {
            label = "AFK-able"
            filterable = true
            defaultValue = false
            helpText = "Can be used while AFK"
        }

        specs()
        references()
    }

    val STORAGE = ideaCategory(IdeaCategory.STORAGE) {
        selectField("storageType") {
            label = "Storage Type"
            filterable = true
            options = listOf(
                SearchableSelectOption(value = "complete_system", label = "Complete System"),
                SearchableSelectOption(value = "box_loader", label = "Box Loader"),
                SearchableSelectOption(value = "box_unloader", label = "Box Unloader"),
                SearchableSelectOption(value = "box_sorter", label = "Box Sorter"),
                SearchableSelectOption(value = "item_transport", label = "Item Transport"),
                SearchableSelectOption(value = "fixed_item_sorter", label = "Fixed Item Sorter"),
                SearchableSelectOption(value = "multi_item_sorter", label = "Multi Item Sorter"),
                SearchableSelectOption(value = "unstackable_sorter", label = "Unstackable Sorter"),
                SearchableSelectOption(value = "chest_hall", label = "Chest Hall"),
                SearchableSelectOption(value = "bulk_storage", label = "Bulk Storage"),
                SearchableSelectOption(value = "temporary_storage", label = "Temporary Storage"),
                SearchableSelectOption(value = "peripheral", label = "Peripheral"),
                SearchableSelectOption(value = "other", label = "Other"),
            )
        }

        sizeField()
        tileable()
        directional()

        specs()
        references()
    }

    val CART_TECH = ideaCategory(IdeaCategory.CART_TECH) {
        selectField("cartTechType") {
            label = "Cart Tech Type"
            filterable = true
            required = true
            options = listOf(
                SearchableSelectOption(value = "storage", label = "Storage"),
                SearchableSelectOption(value = "transport", label = "Transport"),
                SearchableSelectOption(value = "computational", label = "Computational"),
                SearchableSelectOption(value = "farm_collection", label = "Farm Collection"),
                SearchableSelectOption(value = "furnace_arrays", label = "Furnace Arrays"),
                SearchableSelectOption(value = "item_whitelisters", label = "Item Whitelisters"),
                SearchableSelectOption(value = "loading_unloading", label = "Loading/Unloading"),
                SearchableSelectOption(value = "stackers_unstackers", label = "Stackers and Unstackers"),
                SearchableSelectOption(value = "stationary", label = "Stationary"),
                SearchableSelectOption(value = "villagers_plus_plus", label = "Villagers++"),
                SearchableSelectOption(value = "other", label = "Other"),
            )
        }

        sizeField()
        tileable()
        directional()

        specs()
        references()
    }

    val TNT = ideaCategory(IdeaCategory.TNT) {
        selectField("tntType") {
            label = "TNT Type"
            filterable = true
            options = listOf(
                SearchableSelectOption(value = "tnt_duper", label = "TNT Duper"),
                SearchableSelectOption(value = "tnt_compressor", label = "TNT Compressor"),
                SearchableSelectOption(value = "transport_cannon", label = "Transport Cannon"),
                SearchableSelectOption(value = "digging_cannon", label = "Digging Cannon"),
                SearchableSelectOption(value = "arrow_cannon", label = "Arrow Cannon"),
                SearchableSelectOption(value = "weaponry", label = "Weaponry"),
                SearchableSelectOption(value = "other", label = "Other"),
            )
        }

        sizeField()
        tileable()
        directional()

        specs()
        references()
    }

    val SLIMESTONE = ideaCategory(IdeaCategory.SLIMESTONE) {
        selectField("slimestoneType") {
            label = "Slimestone Type"
            filterable = true
            options = listOf(
                SearchableSelectOption(value = "engine", label = "Engine"),
                SearchableSelectOption(value = "one_way_extension", label = "1-Way Extension"),
                SearchableSelectOption(value = "two_way_extension", label = "2-Way Extension"),
                SearchableSelectOption(value = "return_station", label = "Return Station"),
                SearchableSelectOption(value = "quarry", label = "Quarry"),
                SearchableSelectOption(value = "trencher", label = "Trencher"),
                SearchableSelectOption(value = "world_eater", label = "World Eater"),
                SearchableSelectOption(value = "bedrock_breaker", label = "Bedrock Breaker"),
                SearchableSelectOption(value = "other", label = "Other"),
            )
        }

        sizeField()
        tileable()
        directional()

        specs()
        references()
    }

    val OTHER = ideaCategory(IdeaCategory.OTHER) {
        selectField("otherType") {
            label = "Contraption Type"
            filterable = true
            options = listOf(
                SearchableSelectOption(value = "entity_transport", label = "Entity Transport"),
                SearchableSelectOption(value = "furnaces", label = "Furnaces"),
                SearchableSelectOption(value = "brewers", label = "Brewers"),
                SearchableSelectOption(value = "instant_wires", label = "Instant Wires"),
                SearchableSelectOption(value = "logic_computation", label = "Logic Computation"),
                SearchableSelectOption(value = "other", label = "Other"),
            )
        }

        sizeField()
        tileable()
        directional()

        specs()
        references()
    }

    val BUILD = ideaCategory(IdeaCategory.BUILD) {
        sizeField()

        selectField("buildStyle") {
            label = "Build Style"
            filterable = true
            options = listOf(
                SearchableSelectOption(value = "modern", label = "Modern"),
                SearchableSelectOption(value = "medieval", label = "Medieval"),
                SearchableSelectOption(value = "fantasy", label = "Fantasy"),
                SearchableSelectOption(value = "rustic", label = "Rustic"),
                SearchableSelectOption(value = "industrial", label = "Industrial"),
                SearchableSelectOption(value = "other", label = "Other"),
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

        specs()
        references()
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
