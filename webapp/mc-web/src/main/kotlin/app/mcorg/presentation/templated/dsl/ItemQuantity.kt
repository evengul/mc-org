package app.mcorg.presentation.templated.dsl

/**
 * Formats an item count using Minecraft units (shulker boxes / stacks / items).
 * Keeps output compact for use in labels and cards.
 *
 * Examples:
 *   1_000_000 → "578 sb + 3 stacks + 32"
 *   128       → "2 stacks"
 *   100       → "1 stack + 36"
 *   50        → "50"
 */
fun formatItemCount(n: Int): String = when {
    n >= 1728 -> {
        val sb = n / 1728
        val rem = n % 1728
        val stacks = rem / 64
        val items = rem % 64
        buildString {
            append("$sb sb")
            if (stacks > 0) append(" + $stacks stacks")
            if (items > 0) append(" + $items")
        }
    }
    n >= 64 -> {
        val stacks = n / 64
        val items = n % 64
        buildString {
            append("$stacks stack${if (stacks == 1) "" else "s"}")
            if (items > 0) append(" + $items")
        }
    }
    else -> "$n"
}
