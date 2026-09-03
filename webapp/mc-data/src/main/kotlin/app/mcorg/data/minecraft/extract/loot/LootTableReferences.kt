package app.mcorg.data.minecraft.extract.loot

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Finds the loot tables a table pulls in **at less than a whole roll** — the ones whose own
 * numbers are conditional on the referring table selecting them.
 *
 * [LootTableParser.parseLootTable] already composes a reference into the referring table,
 * multiplying the child's yields by the referring entry's share of its pool. So
 * `gameplay/fishing.json` stores the real 0.0083 per-cast chance of a nautilus shell while
 * `gameplay/fishing/treasure.json` stores 0.1667 — true only of the 5% of casts that roll the
 * treasure pool. It is that scaling, and only that scaling, which makes the child's stored row
 * a lie when read on its own. [ExtractLootTables.dropInlinedSubTables] drops what this finds.
 *
 * **Two reference shapes are deliberately *not* reported, because they are not scaled:**
 *
 *  - **A `minecraft:alternatives` dispatch.** `shearing/sheep.json` picks a child by the
 *    sheep's colour, and the colour is the player's choice, not a roll: shearing a white sheep
 *    always gives 2 white wool, which is exactly what `shearing/sheep/white.json` says. The
 *    parent is the lossy one here — `parseAlternatives` can only keep the last child's yield
 *    and marks the other fifteen unknown — so dropping the children would replace fifteen exact
 *    numbers with fifteen shrugs. Same for the `entities/sheep/` and `shearing/mooshroom/`
 *    colour tables, and 26.x's `charged_creeper/` ones.
 *  - **A reference that carries its pool's whole weight.** `equipment/trial_chamber_melee.json`
 *    includes `equipment/trial_chamber.json` outright, and pre-1.21.2 data has each
 *    `entities/sheep/<colour>.json` including the shared `entities/sheep.json` that way. The
 *    composition is lossless in both directions, so the child is an honest standalone source —
 *    killing a sheep really does drop mutton.
 *
 * Dilution is inherited downwards: a reference nested inside an entry that already lost the
 * weight comparison is diluted too, however deep it sits.
 */
internal object LootTableReferences {

    private const val LOOT_TABLE_ENTRY = "minecraft:loot_table"

    /** Every table this one scales down, as a path relative to the version's loot-table root. */
    fun dilutedIn(json: JsonElement): Set<String> = buildSet { walk(json, diluted = false, into = this) }

    /**
     * Mirrors `LootTableParser.findLootTableFilePath`: the same id-to-path spelling the parser
     * uses to *read* the referenced file, so what is collected here and what is walked by
     * `parseJsonFilesRecursively` are the same strings.
     */
    fun pathOf(reference: String): String =
        reference.removePrefix("minecraft:").replace(":", "/") + ".json"

    private fun walk(node: JsonElement, diluted: Boolean, into: MutableSet<String>) {
        when (node) {
            is JsonObject -> {
                if (diluted) referenceOf(node)?.let(into::add)
                (node["pools"] as? JsonArray)?.forEach { walkPool(it, diluted, into) }
                for ((key, value) in node) if (key != "pools") walk(value, diluted, into)
            }
            is JsonArray -> node.forEach { walk(it, diluted, into) }
            else -> Unit
        }
    }

    /**
     * An entry is diluted when it does not carry its pool's whole weight, or when the pool
     * rolls less than once. Nothing in 1.18–26.2 uses the second, but it is the same scaling
     * and costs one comparison to be right about.
     */
    private fun walkPool(pool: JsonElement, inherited: Boolean, into: MutableSet<String>) {
        val entries = (pool as? JsonObject)?.get("entries") as? JsonArray ?: return
        val totalWeight = entries.sumOf { LootNumbers.weightOf(it) }
        val fractionalRolls = (LootNumbers.average(pool["rolls"]) ?: 1.0) < 1.0
        for (entry in entries) {
            val carriesWholePool = totalWeight <= 0.0 || LootNumbers.weightOf(entry) >= totalWeight
            walk(entry, inherited || !carriesWholePool || fractionalRolls, into)
        }
    }

    /**
     * The table a `minecraft:loot_table` entry names, or null. A `value`/`name` without a `/`
     * is a bare item id, which the parser reads as a drop and which names no file; an object
     * `value` is an inlined table, which has no file of its own either.
     */
    private fun referenceOf(node: JsonObject): String? {
        if ((node["type"] as? JsonPrimitive)?.content != LOOT_TABLE_ENTRY) return null
        val reference = (node["value"] ?: node["name"]) as? JsonPrimitive ?: return null
        return reference.content.takeIf { it.contains('/') }?.let(::pathOf)
    }
}
