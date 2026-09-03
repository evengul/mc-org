package app.mcorg.data.minecraft.extract

import app.mcorg.data.minecraft.ServerPathResolvers
import app.mcorg.data.minecraft.failure.ExtractionFailure
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.minecraft.MinecraftVersionRange
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.Result
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Immutable per-version extraction context: the display names from the lang file and the
 * item/block tag definitions, loaded once up front by [ExtractionContextFactory] and passed
 * to every extraction step. Replaces the former ExtractNamesStep/ExtractTagsStep global
 * caches and their priming choreography.
 */
data class ExtractionContext(
    val version: MinecraftVersion.Release,
    val root: Path,
    val names: Map<String, String>,
    val tags: Map<String, List<String>>,
    /** The version's item registry, derived from the lang file's item/block keys. */
    val itemIds: Set<String>,
) {
    /**
     * Display name for an item id, falling back to the id itself. Carries the historical
     * special cases: charged-creeper head drops, and the 1.20–1.20.1 smithing templates whose
     * lang keys predate the per-template ids.
     */
    fun nameOf(id: String): String {
        if (id.startsWith("minecraft:charged_creeper/")) {
            val entityId = id.removePrefix("minecraft:charged_creeper/")
            return names["minecraft:${entityId}_skull"] ?: names["minecraft:${entityId}_head"] ?: entityId
        }

        if (id.contains("_armor_trim_smithing_template") && EARLY_SMITHING_TEMPLATE_VERSIONS.contains(version)) {
            return names["minecraft:armor_trim_${id.removePrefix("minecraft:").removeSuffix("_armor_trim_smithing_template")}"] ?: id
        }

        if (id.contains("_upgrade_smithing_template") && EARLY_SMITHING_TEMPLATE_VERSIONS.contains(version)) {
            return names["minecraft:${id.removePrefix("minecraft:").removeSuffix("_smithing_template")}"] ?: id
        }

        return names[id] ?: id
    }

    /**
     * Recursively resolves a tag (`#minecraft:planks`) to the item ids it contains.
     *
     * A tag already being resolved further up the recursion contributes **nothing** the second
     * time — the repeated edge is dropped, not the tag. That is the deliberate half of the
     * choice: a self-referential tag with no other members resolves to nothing (there is no
     * member to name), while a tag that reaches itself *and* lists real items keeps those items.
     * Resolving to empty wholesale would silently blank a tag over one bad edge, and Mojang's
     * data has no cycle whose members we would want to discard. It also matches how a subset
     * relation already behaves: the second mention adds no new information either way.
     *
     * Without this a self-referential tag recursed until the stack went (MCO-488). With it,
     * depth is bounded by the number of distinct tags, so resolving every tag in the registry
     * terminates for any version — including a future one that ships a cycle we have not seen.
     */
    fun contentOfTag(tag: String): List<String> = contentOfTag(tag, HashSet())

    /** [resolving] is the path currently being expanded, so diamonds still expand twice — only cycles break. */
    private fun contentOfTag(tag: String, resolving: MutableSet<String>): List<String> {
        if (!resolving.add(tag)) return emptyList()
        val content = tags[tag]?.flatMap {
            if (it.startsWith("#")) contentOfTag(it, resolving) else listOf(it)
        } ?: emptyList()
        resolving.remove(tag)
        return content
    }

    companion object {
        private val EARLY_SMITHING_TEMPLATE_VERSIONS = MinecraftVersionRange.Bounded(
            from = MinecraftVersion.release(20, 0),
            to = MinecraftVersion.release(20, 1)
        )

        /**
         * Human-readable name for a tag *id*: `#minecraft:wooden_slabs` -> `Wooden Slabs`.
         *
         * The last-resort label only. A tag with members is named by them — see [tagChoiceName],
         * and MCO-489 for why an id makes a bad question.
         */
        fun tagDisplayName(tag: String): String {
            val cleaned = tag.replace("_", " ").replace("#minecraft:", "")

            return cleaned.mapIndexed { index, ch ->
                if (index == 0 || cleaned[index - 1] == ' ') ch.uppercaseChar() else ch
            }.joinToString("")
        }
    }
}

/** Resolves display names and tag contents on a parsed source's item references. */
fun ResourceSource.withNames(context: ExtractionContext): ResourceSource = copy(
    requiredItems = requiredItems.map { (id, quantity) -> id.withName(context) to quantity },
    producedItems = producedItems.map { (id, quantity) -> id.withName(context) to quantity },
)

private fun MinecraftId.withName(context: ExtractionContext): MinecraftId = when (this) {
    is Item -> copy(name = context.nameOf(id))
    is MinecraftTag -> {
        // A synthetic tag (e.g. an inline-alternatives choice tag) is not in the version's tag
        // registry but carries its own members — those are the truth for it, the registry's for
        // everything else.
        val fromRegistry = context.contentOfTag(id)
        val memberIds = if (fromRegistry.isEmpty() && content.isNotEmpty()) content.map { it.id } else fromRegistry

        copy(
            // Named by its members wherever it has any, so the same set reads the same under
            // every id it is known by, and a question names the things it asks you to choose
            // between (MCO-489). The id is the fallback for a tag with no members at all.
            name = tagChoiceName(memberIds) ?: ExtractionContext.tagDisplayName(id),
            content = memberIds.map { memberId -> Item(memberId, context.nameOf(memberId)) },
        )
    }
}

object ExtractionContextFactory {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val whiteListedKeyPrefixes = listOf(
        "item.minecraft.",
        "block.minecraft.",
        "trim_pattern.minecraft.",
        "upgrade.minecraft."
    )

    suspend fun create(version: MinecraftVersion.Release, root: Path): Result<ExtractionFailure, ExtractionContext> {
        val rawNames = loadNames(version, root)
        if (rawNames is Result.Failure) {
            return rawNames
        }

        val tags = loadTags(version, root)
        if (tags is Result.Failure) {
            return tags
        }

        return Result.success(
            ExtractionContext(
                version = version,
                root = root,
                names = cleanNames(rawNames.getOrThrow()),
                tags = tags.getOrThrow(),
                itemIds = registryIds(rawNames.getOrThrow()),
            )
        )
    }

    /**
     * The version's item registry, read off the lang file: every dot-free
     * `item.minecraft.X` / `block.minecraft.X` key is an id. Dotted suffixes
     * (`item.minecraft.splash_potion.effect.luck`) are auxiliary strings, not items.
     *
     * Then pruned of the two ways that heuristic over-reads — see [RENAMED_ITEMS] and
     * [NON_ITEM_KEYS] (MCO-313).
     */
    internal fun registryIds(raw: Map<String, String>): Set<String> {
        val ids = buildSet {
            raw.keys.forEach { key ->
                for (prefix in listOf("item.minecraft.", "block.minecraft.")) {
                    if (key.startsWith(prefix)) {
                        val id = key.removePrefix(prefix)
                        if (!id.contains('.')) {
                            add("minecraft:$id")
                        }
                    }
                }
            }
        }

        // Drop a legacy id only when its replacement is present. That makes the rule
        // self-versioning with no version ranges to maintain: 1.18 has no short_grass, so it
        // keeps `grass`; 26.2 has both, so `grass` goes.
        val supersededLegacyIds = RENAMED_ITEMS
            .filter { (_, current) -> current in ids }
            .keys

        return ids - supersededLegacyIds - NON_ITEM_KEYS
    }

    /**
     * Legacy id -> the id that replaced it. Mojang's lang file keeps the old key around long
     * after a rename, so both spellings look like registry entries while only the new one is
     * produced by any recipe or loot table — leaving the old one permanently BLOCKED.
     *
     * Only ever prunes the legacy id when the replacement exists in the same version, so
     * versions predating a rename are untouched.
     */
    private val RENAMED_ITEMS = mapOf(
        "minecraft:chain" to "minecraft:iron_chain",       // 1.21.9, when copper chains arrived
        "minecraft:grass" to "minecraft:short_grass",      // 1.20.3
        "minecraft:scute" to "minecraft:turtle_scute",     // 1.20.5
        "minecraft:sign" to "minecraft:oak_sign",          // 1.14
    )

    /**
     * Dot-free `item.`/`block.` lang keys that were never items — generic labels shared by a
     * family (`item.minecraft.smithing_template` is the "Smithing Template" heading used by
     * every template) or UI messages that happen to sit under the block namespace.
     */
    private val NON_ITEM_KEYS = setOf(
        "minecraft:smithing_template",
        "minecraft:harness",
        "minecraft:set_spawn",
    )

    private fun loadNames(version: MinecraftVersion.Release, root: Path): Result<ExtractionFailure, Map<String, String>> {
        try {
            val enUsFile = root.resolve("lang").resolve("en_us.json")
            if (!enUsFile.toFile().exists()) {
                logger.error("en_us.json file does not exist at path: {}", enUsFile)
                return Result.failure(ExtractionFailure.MissingFile("en_us.json", version = version))
            }

            val raw = Json.decodeFromString<LinkedHashMap<String, String>>(enUsFile.toFile().readText())

            if (raw.isEmpty()) {
                logger.warn("No item names extracted from en_us.json for version {}", version)
                return Result.failure(ExtractionFailure.MissingContent("en_us.json"))
            }

            logger.debug("Extracted {} names for version {}", raw.size, version)

            return Result.success(raw)
        } catch (e: Exception) {
            logger.error("Failed to extract names for version {}: {}", version, e.message, e)
            return Result.failure(ExtractionFailure.ItemExtractionFailed(version))
        }
    }

    /**
     * Normalizes lang keys to `minecraft:` ids and suffixes the display name with its kind.
     * `item.minecraft.X` and `block.minecraft.X` can both normalize to `minecraft:X`; entries
     * are applied in fixed precedence order (item last, so it wins) instead of relying on the
     * lang file's iteration order.
     */
    private fun cleanNames(raw: Map<String, String>): Map<String, String> {
        val names = mutableMapOf<String, String>()

        fun add(prefix: String, replacement: String, suffix: String) {
            raw.forEach { (key, value) ->
                if (key.startsWith(prefix)) {
                    names["$replacement${key.removePrefix(prefix)}"] = value + suffix
                }
            }
        }

        add("upgrade.minecraft.", "minecraft:", "")
        add("trim_pattern.minecraft.", "minecraft:armor_trim_", "")
        add("block.minecraft.", "minecraft:", " (Block)")
        add("item.minecraft.", "minecraft:", " (Item)")

        return names
    }

    /**
     * Loads item tags, then fills in block tags whose names no item tag uses. Item and block
     * tags share a name namespace here (`#minecraft:<path below tags/item|block>`) but can have
     * different contents (e.g. `banners` in 26.1+); recipes and loot reference item tags, so
     * those win deterministically instead of racing on parse order. Within one namespace the ids
     * are unique — see [filenameToTagId], which is what makes that true.
     */
    private suspend fun loadTags(version: MinecraftVersion.Release, root: Path): Result<ExtractionFailure, Map<String, List<String>>> {
        val itemTags = loadTagDirectory(version, ServerPathResolvers.resolveItemTagsPath(root, version))
        if (itemTags is Result.Failure) {
            return itemTags
        }
        val blockTags = loadTagDirectory(version, ServerPathResolvers.resolveBlockTagsPath(root, version))
        if (blockTags is Result.Failure) {
            return blockTags
        }
        return Result.success(blockTags.getOrThrow() + itemTags.getOrThrow())
    }

    private suspend fun loadTagDirectory(
        version: MinecraftVersion.Release,
        path: Path
    ): Result<ExtractionFailure, Map<String, List<String>>> {
        return parseJsonFilesRecursively(version, path) { content, filename ->
            parseTagFile(content, filename)
        }.map { it.toMap() }
    }

    private fun parseTagFile(content: String, filename: String): Result<ExtractionFailure, Pair<String, List<String>>> {
        if (content.isEmpty()) {
            logger.warn("Empty tag file: $filename")
            return Result.failure(ExtractionFailure.MissingContent(filename))
        }

        val json = try {
            Json.parseToJsonElement(content)
        } catch (e: Exception) {
            logger.error("Error parsing JSON from tag file $filename", e)
            return Result.failure(ExtractionFailure.JsonFailure.ParseError(content, filename))
        }

        val values = json.jsonObject["values"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: return Result.failure(ExtractionFailure.JsonFailure.KeyNotFound(json, "values", filename))

        return Result.success(filenameToTagId(filename) to values)
    }

    /**
     * A tag's id is its **whole path** below `tags/<item|block>/`, not its base filename.
     *
     * Vanilla ships `foot_armor.json` and `enchantable/foot_armor.json` as two different tags
     * with two different member lists (1.20.5+, 18 of the 31 ingested versions). Keyed by base
     * filename they collapsed into one entry that whichever file parsed last decided — and since
     * the enchantable one's single member is `#minecraft:foot_armor`, the survivor was often a
     * tag whose only member was itself. 34 ids move as a result, all of them behavioural tags
     * under `enchantable` and `mineable` that no recipe or loot table in any version references and
     * that ingestion therefore never stored (MCO-488).
     *
     * The path is also Mojang's own spelling — `minecraft:enchantable/foot_armor` is the id the
     * game uses — so this is the correct key, not a disambiguating invention.
     */
    internal fun filenameToTagId(filename: String) = "#minecraft:${filename.removeSuffix(".json")}"
}
