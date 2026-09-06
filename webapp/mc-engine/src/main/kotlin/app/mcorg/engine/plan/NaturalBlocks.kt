package app.mcorg.engine.plan

/**
 * Which blocks world generation places, from the committed `natural-blocks.txt` snapshot.
 *
 * It answers one question, and it is the one [isSelfBlockLoot] could not answer for itself:
 * **is breaking this block mining, or is it picking up something a player put down?**
 *
 * A beacon is only ever placed, so breaking one recovers what you carried there. Terracotta is
 * what a badlands is *made of*, and breaking it is how you get it. Before this, the gate decided
 * between them by asking whether the block had a recipe — which is a question about crafting, not
 * about the world, and it got terracotta wrong in a way that removed mining as an option
 * altogether (MCO-527).
 *
 * ## Two sections, both natural
 *
 * [terrain] is the surface rules: what the ground is made of. [feature] is what worldgen puts
 * into it — ores, disks, vegetation, trees. Kept apart in the file because they are different
 * claims and a later reader may want only one; [isNatural] is the union.
 *
 * ## What is deliberately not here
 *
 * **Structure blocks.** A bookshelf in a woodland mansion is *findable* but not natural, and the
 * two are priced differently: structure membership carries a rarity and a blocks-per-visit
 * ([StructureDensity]), while natural terrain is priced by the block's own effort. A block that
 * generates only inside a structure should keep reading as re-collection when it has a recipe,
 * because the honest answer for it is "find the structure", not "go mining".
 *
 * **The banded badlands colours.** `minecraft:bandlands` is a surface-rule type whose terracotta
 * palette lives in code rather than in the JSON, so `red_`, `yellow_`, `brown_` and
 * `light_gray_terracotta` are absent while plain, white and orange are present. This costs
 * nothing in practice — dyed terracotta is crafted from plain, so pricing the plain block prices
 * the rest through the chain — but it is a limit of the data rather than of the extraction, and
 * the snapshot header says so.
 */
internal object NaturalBlocks {

    private const val RESOURCE = "/minecraft/natural-blocks.txt"

    /** The Minecraft version the snapshot was taken from. */
    val version: String

    /** Blocks the surface rules make the ground out of. */
    val terrain: Set<String>

    /** Blocks worldgen places into the ground: ores, disks, vegetation, trees. */
    val features: Set<String>

    init {
        val text = NaturalBlocks::class.java.getResourceAsStream(RESOURCE)
            ?.bufferedReader()?.use { it.readText() }
            ?: error("$RESOURCE missing — regenerate with scripts/dump-structure-density.py")

        var readVersion: String? = null
        val parsedTerrain = LinkedHashSet<String>()
        val parsedFeatures = LinkedHashSet<String>()
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
                "terrain" -> parsedTerrain.add(line)
                "feature" -> parsedFeatures.add(line)
            }
        }

        version = readVersion ?: error("$RESOURCE has no version= line")
        terrain = parsedTerrain
        features = parsedFeatures
    }

    /**
     * Does world generation place [blockId] (`minecraft:terracotta`) anywhere outside a structure?
     *
     * False is the ordinary answer for anything a player builds, and it is what keeps the
     * self-block-loot gate doing its job.
     */
    fun isNatural(blockId: String): Boolean = blockId in terrain || blockId in features
}
