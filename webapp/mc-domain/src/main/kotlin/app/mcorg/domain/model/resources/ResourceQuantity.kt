package app.mcorg.domain.model.resources

sealed interface ResourceQuantity {
    /** The parser could not determine a quantity (e.g. tag-based input with no count). */
    object Unknown : ResourceQuantity

    /**
     * The quantity is deliberately not static — Minecraft computes it at runtime from game state
     * (e.g. the librarian enchanted-book trade whose emerald price is derived from the rolled
     * enchantment). We track the dependency but should not display a misleading number.
     */
    object RuntimeCalculation : ResourceQuantity

    data class ItemQuantity(val itemQuantity: Int) : ResourceQuantity {
        init {
            require(itemQuantity > 0) { "ItemQuantity must be positive" }
        }
    }

    /**
     * Average amount produced per attempt, derived from loot-table rolls, entry
     * weights, and count functions — e.g. 0.33 sticks per witch kill. Lets the
     * planner reason in attempts needed instead of assuming one item per action.
     */
    data class ExpectedYield(val expected: Double) : ResourceQuantity {
        init {
            require(expected > 0) { "ExpectedYield must be positive" }
        }
    }
}