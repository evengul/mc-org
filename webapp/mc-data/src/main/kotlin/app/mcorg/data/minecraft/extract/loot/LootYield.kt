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
     * Average drop count after an entry's `set_count` functions (applied in
     * order; `add: true` adds to the running count). 1.0 without functions;
     * null when a count provider is unrecognized.
     */
    fun countAfterFunctions(entry: JsonElement): Double? {
        val functions = (entry as? JsonObject)?.get("functions") ?: return 1.0
        var count = 1.0
        val list = (functions as? JsonArray) ?: return 1.0
        for (function in list) {
            val obj = function as? JsonObject ?: continue
            val id = (obj["function"] as? JsonPrimitive)?.content ?: continue
            if (id != "minecraft:set_count") continue
            val avg = average(obj["count"]) ?: return null
            val add = (obj["add"] as? JsonPrimitive)?.content == "true"
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
