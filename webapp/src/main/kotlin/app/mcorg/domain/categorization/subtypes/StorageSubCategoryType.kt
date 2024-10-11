package app.mcorg.domain.categorization.subtypes

enum class StorageSubCategoryType(override val displayName: String) : SubCategoryType {
    COMPLETE_SYSTEM("Complete system"),
    BOX_LOADER("Box loader"),
    BOX_UNLOADER("Box unloader"),
    BOX_SORTER("Box sorter"),
    BOX_DISPLAY("Box display"),
    BOX_PROCESSOR("Box processor"),
    ITEM_TRANSPORT("Item transport"),
    FIXED_ITEM_SORTER("Fixed item sorter"),
    MULTI_ITEM_SORTER("Multi item sorter"),
    VARIABLE_ITEM_SORTER("Variable item sorter"),
    UNSTACKABLE_SORTER("Unstackable stack sorter"),
    ENCODED_TECH("Encoded tech"),
    CHEST_HALL("Chest hall"),
    ITEM_CALL("Item call"),
    BULK_STORAGE("Bulk storage"),
    TEMPORARY_STORAGE("Temporary storage"),
    INTERFACES("Interfaces"),
    PERIPHERALS("Peripherals"),
}