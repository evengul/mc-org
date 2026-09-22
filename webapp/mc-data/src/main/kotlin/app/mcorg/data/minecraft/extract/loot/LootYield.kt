package app.mcorg.data.minecraft.extract.loot

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * One parsed loot entry: its selection [weight] within the pool, and the items
 * it drops when selected.
 */
data class LootEntry(
    val weight: Double,
    val drops: List<LootDrop>
) {
    companion object {
        val EMPTY = LootEntry(weight = 1.0, drops = emptyList())
    }
}

/**
 * One item an entry can drop. [countPerSelection] is the average amount when
 * the entry is selected (rolls and weights are applied at the pool level);
 * null means the amount or probability could not be determined — the item is
 * still recorded as obtainable, just without a yield estimate.
 */
data class LootDrop(
    val itemId: String,
    val countPerSelection: Double?
)

internal object LootNumbers {

    /**
     * Average of a loot-table number provider: a plain number, a {min,max}
     * range (uniform), or a {type: constant|uniform, ...} object. Null when the
     * shape is unrecognized (e.g. binomial, score-based).
     */
    fun average(element: JsonElement?): Double? = when (element) {
        null -> null
        is JsonPrimitive -> element.doubleOrNull
        is JsonObject -> {
            val value = (element["value"] as? JsonPrimitive)?.doubleOrNull
            val min = (element["min"] as? JsonPrimitive)?.doubleOrNull
            val max = (element["max"] as? JsonPrimitive)?.doubleOrNull
            when {
                value != null -> value
                min != null && max != null -> (min + max) / 2.0
                else -> null
            }
        }
        else -> null
    }

    /**
     * Average drop count after an entry's `set_count` modifiers (applied in
     * order; `add: true` adds to the running count). 1.0 without modifiers;
     * null when a count provider is unrecognized.
     *
     * 26.3 renamed the holder `functions` -> `modifier`, renamed the discriminator
     * inside it `function` -> `type`, and let a lone modifier be written inline as an
     * object instead of a one-element list (724 of 1098 holders in 26.3's data are
     * objects). Both spellings are read, and the holder key picks its own
     * discriminator rather than the two being tried interchangeably.
     *
     * **This one fails silently, which is why it is read rather than rejected**
     * (MCO-567): an unread holder is not a parse error, it just leaves the count at
     * the 1.0 default. 26.3 has 557 `set_count` modifiers, so reading only
     * `functions` would have quietly reported a flat 1.0 yield for every one of them
     * while the suite stayed green on the two loud failures either side of it.
     */
    fun countAfterFunctions(entry: JsonElement): Double? {
        val obj = entry as? JsonObject ?: return 1.0
        val (holder, discriminator) = when {
            obj["modifier"] != null -> obj.getValue("modifier") to "type"        // 26.3+
            obj["functions"] != null -> obj.getValue("functions") to "function"  // <= 26.2
            else -> return 1.0
        }
        val modifiers = when (holder) {
            is JsonArray -> holder
            is JsonObject -> listOf(holder)
            else -> return 1.0
        }
        var count = 1.0
        for (modifier in modifiers) {
            val modifierObj = modifier as? JsonObject ?: continue
            val id = (modifierObj[discriminator] as? JsonPrimitive)?.content ?: continue
            if (id != "minecraft:set_count") continue
            val avg = average(modifierObj["count"]) ?: return null
            val add = (modifierObj["add"] as? JsonPrimitive)?.content == "true"
            count = if (add) count + avg else avg
        }
        return count
    }

    fun weightOf(entry: JsonElement): Double =
        ((entry as? JsonObject)?.get("weight") as? JsonPrimitive)?.doubleOrNull ?: 1.0

    /** Sums yield contributions; an unknown contribution keeps any known signal. */
    fun combine(a: Double?, b: Double?): Double? = when {
        a == null -> b
        b == null -> a
        else -> a + b
    }
}
