package app.mcorg.engine.renewability

import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource

/**
 * Everything [Renewability] reads about one version, as text: the version's sources, its item
 * registry and its tags. `renewability-diagnostics snapshot` writes one per reviewed version, and
 * `RenewabilitySnapshotTest` rebuilds the graph from it, so the snapshot is checked against real
 * data without a database.
 *
 * Tab-separated, one record per line, sorted so a re-ingest diffs cleanly:
 * ```
 * S <type> <filename>     a source; the P/C/T lines after it belong to it
 * P <item>                it produces this item
 * C <item>                it consumes this item
 * T <tag>                 it consumes one of this tag
 * M <tag> <item>          tag membership
 * I <item>                the item registry
 * ```
 * Quantities and names are left out: renewability reads neither.
 */
data class RenewabilityFixture(
    val sources: List<ResourceSource>,
    val registry: Set<String>,
    val tags: Map<String, Set<String>>,
) {

    fun write(): String = buildString {
        for (source in sources.sortedWith(compareBy({ it.type.id }, { it.filename }))) {
            append("S\t").append(source.type.id).append('\t').append(source.filename).append('\n')
            source.producedItems.map { it.first }.filterNot { it is MinecraftTag }.map { it.id }.sorted()
                .forEach { append("P\t").append(it).append('\n') }
            val required = source.requiredItems.map { it.first }
            required.filterNot { it is MinecraftTag }.map { it.id }.sorted().forEach { append("C\t").append(it).append('\n') }
            required.filterIsInstance<MinecraftTag>().map { it.id }.sorted().forEach { append("T\t").append(it).append('\n') }
        }
        for ((tag, members) in tags.toSortedMap()) {
            members.sorted().forEach { append("M\t").append(tag).append('\t').append(it).append('\n') }
        }
        registry.sorted().forEach { append("I\t").append(it).append('\n') }
    }

    companion object {
        fun read(text: String): RenewabilityFixture {
            data class Pending(val type: String, val filename: String) {
                val produces = mutableListOf<String>()
                val consumes = mutableListOf<String>()
                val consumedTags = mutableListOf<String>()
            }

            val pending = mutableListOf<Pending>()
            val tags = sortedMapOf<String, MutableSet<String>>()
            val registry = linkedSetOf<String>()
            for (line in text.lineSequence().filter { it.isNotBlank() }) {
                val f = line.split('\t')
                when (f[0]) {
                    "S" -> pending.add(Pending(f[1], f[2]))
                    "P" -> pending.last().produces.add(f[1])
                    "C" -> pending.last().consumes.add(f[1])
                    "T" -> pending.last().consumedTags.add(f[1])
                    "M" -> tags.getOrPut(f[1]) { linkedSetOf() }.add(f[2])
                    "I" -> registry.add(f[1])
                    else -> error("Unknown renewability fixture record: $line")
                }
            }
            fun tag(id: String) = MinecraftTag(id, id, tags[id].orEmpty().map { Item(it, it) })
            fun one(id: MinecraftId): Pair<MinecraftId, ResourceQuantity> = id to ResourceQuantity.ItemQuantity(1)
            val sources = pending.map { p ->
                ResourceSource(
                    type = ResourceSource.SourceType.of(p.type),
                    filename = p.filename,
                    requiredItems = p.consumes.map { one(Item(it, it)) } + p.consumedTags.map { one(tag(it)) },
                    producedItems = p.produces.map { one(Item(it, it)) },
                )
            }
            return RenewabilityFixture(sources, registry, tags)
        }
    }
}
