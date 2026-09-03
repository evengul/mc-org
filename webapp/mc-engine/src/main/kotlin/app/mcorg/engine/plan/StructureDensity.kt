package app.mcorg.engine.plan

/**
 * How rare each Minecraft structure is, and which blocks it places — read from the
 * snapshot at `minecraft/structure-density.txt`, which is derived from a server jar by
 * `webapp/scripts/dump-structure-density.py`.
 *
 * ## What this is for
 *
 * [EffortTable] prices a block-loot source by the action *and* by what it takes to reach
 * it. Ore is curated in `BLOCK_FINDING` because availability is not in Mojang's data.
 * Structure loot is not curated, because it is:
 *
 * - **Density** comes from `worldgen/structure_set`: a `random_spread` structure occurs
 *   once per `spacing x spacing` chunk region, times `frequency`, so chunks searched per
 *   occurrence is `spacing^2 / frequency`.
 * - **Membership** comes from the `structure/<name>.nbt` templates: each names the blocks it
 *   places, and its top directory is the structure family.
 *
 * Together they answer the question a curated boolean could not. MCO-501 opened proposing
 * a "does this block generate naturally?" flag, and it fails on its own motivating case:
 * an ender chest **does** generate, in End city ships. What makes breaking one an absurd
 * way to get obsidian is not that it is unnatural, it is that an End city costs a dead
 * dragon and a void crossing. That is a price, not a predicate.
 *
 * ## What is still felt
 *
 * Density is how *rare* a structure is; access is how hard it is to *reach*, and only the
 * first is in the jar. Buried treasure is the densest structure in the game and needs a
 * map from a shipwreck first. So [EffortTable] keeps one curated multiplier per structure
 * *class* — five numbers, each an arguable claim — rather than one per structure.
 *
 * ## Why a snapshot and not an extraction step
 *
 * Placements have not moved across 1.20 -> 26.2, so there is nothing for a per-version
 * step to track; and 1.18/1.19 ship no structure data at all, so a step would need this
 * fallback anyway. Mirrors the committed `item-ids.txt` that `ItemGlyphTest` pins against.
 */
object StructureDensity {

    /** A structure set's placement. [chunksPerOccurrence] is null for a non-spread placement. */
    data class Placement(
        val set: String,
        val chunksPerOccurrence: Double?,
        val kind: String,
    )

    private const val RESOURCE = "/minecraft/structure-density.txt"

    /** The Minecraft version the snapshot was taken from. */
    val version: String

    /** Every structure set in the snapshot, by set name. */
    val placements: Map<String, Placement>

    private val membership: Map<String, Set<String>>

    /**
     * The density every other structure is expressed as a multiple of. Villages are the
     * natural baseline: the one structure a player is likely to already be standing in,
     * and the one the curated chest table already treated as the cheap case.
     */
    val baselineChunks: Double

    init {
        val text = StructureDensity::class.java.getResourceAsStream(RESOURCE)
            ?.bufferedReader()?.use { it.readText() }
            ?: error("$RESOURCE missing — regenerate with scripts/dump-structure-density.py")

        var readVersion: String? = null
        val parsedPlacements = LinkedHashMap<String, Placement>()
        val parsedMembership = LinkedHashMap<String, Set<String>>()
        var section = ""

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("[")) {
                section = line.trim('[', ']')
                continue
            }
            when (section) {
                "" -> if (line.startsWith("version=")) readVersion = line.removePrefix("version=")

                "placement" -> {
                    val (set, chunks, kind) = line.split('|').let {
                        Triple(it[0], it.getOrNull(1).orEmpty(), it.getOrNull(2).orEmpty())
                    }
                    parsedPlacements[set] = Placement(set, chunks.toDoubleOrNull(), kind)
                }

                "membership" -> {
                    val parts = line.split('|')
                    if (parts.size == 2 && parts[1].isNotEmpty()) {
                        parsedMembership[parts[0]] = parts[1].split(',').toSet()
                    }
                }
            }
        }

        version = readVersion ?: error("$RESOURCE has no version= line")
        placements = parsedPlacements
        membership = parsedMembership
        baselineChunks = parsedPlacements["villages"]?.chunksPerOccurrence
            ?: error("$RESOURCE has no villages placement to normalise against")
    }

    /**
     * The structure sets known to place [blockId] (`minecraft:bookshelf`), or empty.
     *
     * Empty means one of three different things, and the caller cannot tell them apart:
     * the block generates nowhere (`soul_campfire`), or it generates only in one of the
     * seven structures built in code rather than from templates (`sponge`, in an ocean
     * monument), or it is not a structure block at all (`iron_ore`). Only the first is a
     * case for charging construction, which is why that decision is made against a
     * *recipe* as well, not against this alone.
     */
    fun setsContaining(blockId: String): Set<String> = membership[blockId].orEmpty()

    /**
     * How much rarer than a village this structure is, by density alone. 1.0 is a village.
     *
     * A structure with no derived density (`strongholds`, placed on concentric rings
     * rather than a spread) answers 1.0, leaving its curated access multiplier to carry
     * the whole price rather than inventing a spacing it does not have.
     */
    fun densityRatio(set: String): Double {
        val chunks = placements[set]?.chunksPerOccurrence ?: return 1.0
        return chunks / baselineChunks
    }
}
