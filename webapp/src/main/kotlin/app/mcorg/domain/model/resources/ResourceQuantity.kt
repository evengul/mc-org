package app.mcorg.domain.model.resources

sealed interface ResourceQuantity {
    object Unknown : ResourceQuantity

    data class ItemQuantity(val itemQuantity: Int) : ResourceQuantity {
        init {
            require(itemQuantity > 0) { "ItemQuantity must be positive" }
        }
    }
}