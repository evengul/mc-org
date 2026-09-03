package app.mcorg.data.minecraft.extract.loot

import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.pipeline.Result
import app.mcorg.data.minecraft.ServerPathResolvers
import app.mcorg.data.minecraft.extract.ExtractionContext
import app.mcorg.data.minecraft.extract.getResult
import app.mcorg.data.minecraft.extract.objectResult
import app.mcorg.data.minecraft.extract.parseJsonFilesRecursively
import app.mcorg.data.minecraft.extract.primitiveResult
import app.mcorg.data.minecraft.extract.withNames
import app.mcorg.data.minecraft.failure.ExtractionFailure
import app.mcorg.domain.pipeline.Step
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Infested blocks' `minecraft:block` loot tables list a Silk Touch drop of the *base* block
 * (e.g. `infested_stone_bricks.json` drops `minecraft:stone_bricks`), but `InfestedBlock`
 * overrides destroy handling in game code: without Silk Touch it drops nothing (spawns a
 * silverfish instead), and with Silk Touch it drops the infested block itself, never the base
 * block. The loot-table entry is a phantom the JSON data can't reveal, and its filename
 * (`infested_stone_bricks`) doesn't match the item it phantom-drops (`stone_bricks`), so
 * without this guard it reads as a legitimate raw-gather source and wins over crafting.
 */
internal fun isPhantomInfestedBlockLoot(filename: String): Boolean {
    val stem = filename.substringAfterLast('/').substringBeforeLast('.')
    return stem.startsWith("infested_")
}

/** One walked loot-table file: what it produces, and the tables it scales down (see [LootTableReferences]). */
internal data class ParsedLootTable(val source: ResourceSource, val dilutedReferences: Set<String>)

/**
 * Drops every table that another table rolls as *part* of a pool, keeping only the ones a
 * player can actually attempt directly (MCO-491).
 *
 * Such a sub-table's numbers are **conditional probabilities stored as though they were
 * unconditional**. `gameplay/fishing/treasure.json` says a nautilus shell is 1-in-6, and that
 * is true — of the 5% of casts that roll the treasure pool at all. The parent already carries
 * the composed truth (0.0083 per cast, from `parseLootTable` multiplying the child's yields by
 * the referring entry's pool share), so with both stored, any consumer taking the best source
 * per item reads the sub-table's number and overstates the drop by 20x.
 *
 * **Dropping the child rather than restating it with the composed number**, for three reasons:
 *  - Nothing becomes unobtainable. Every dropped table is composed into at least one parent at
 *    the correct rate, so its file adds no reachability and no information.
 *  - There is not always *one* composed probability. `gameplay/fishing/fish.json` is pulled in
 *    by `gameplay/fishing.json` (weight 85 of 100), `entities/guardian.json` and
 *    `entities/elder_guardian.json` — three different compositions of the same file, in every
 *    version from 1.18 on. A restated child would have to pick one arbitrarily.
 *  - A restated child would be an exact numeric duplicate of what the parent already stores,
 *    under a second filename: two candidates the scorer must tie-break, for no new information.
 *
 * What is dropped is decided by [LootTableReferences] and turns on the *scaling*, not on the
 * reference or the directory nesting. A colour dispatch (`shearing/sheep/white.json`) and a
 * whole-pool inclusion (`equipment/trial_chamber.json`, and pre-1.21.2's shared
 * `entities/sheep.json`) survive; measuring the first draft of this against the real graph is
 * how that came out, because dropping the dispatch children replaced fifteen exact wool yields
 * with fifteen unknowns and doubled the modelled cost of coloured wool.
 */
internal fun dropInlinedSubTables(parsed: List<ParsedLootTable>): List<ResourceSource> {
    val diluted = parsed.flatMapTo(mutableSetOf()) { it.dilutedReferences }
    return parsed.filterNot { it.source.filename in diluted }.map { it.source }
}

data object ExtractLootTables : Step<ExtractionContext, ExtractionFailure, List<ResourceSource>> {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override suspend fun process(input: ExtractionContext): Result<ExtractionFailure, List<ResourceSource>> {
        val lootTableParser = LootTableParser(input.root, input.version)

        return parseJsonFilesRecursively(input.version, ServerPathResolvers.resolveLootTablesPath(input.root, input.version)) { content, filename ->
            parseFile(lootTableParser, content, filename)
        }
            .map { parsed ->
                dropInlinedSubTables(parsed)
                    .map { it.withNames(input) }
                    .filter { it.type != ResourceSource.SourceType.RecipeTypes.IGNORED && it.producedItems.isNotEmpty() }
            }
    }

    private suspend fun parseFile(lootTableParser: LootTableParser, content: String, filename: String): Result<ExtractionFailure, ParsedLootTable> {
        val json = try {
            Json.parseToJsonElement(content)
        } catch (e: Exception) {
            logger.error("Error parsing JSON from tag file $content", e)
            return Result.failure(ExtractionFailure.JsonFailure.ParseError(content, filename))
        }

        val type = json.objectResult(filename)
            .flatMap { it.getResult("type", filename) }
            .flatMap { it.primitiveResult(filename) }
            .mapSuccess { it.content }

        if (type is Result.Failure) {
            logger.warn("Error parsing type from loot file: $filename")
            return type
        }

        val source = when (val stringType = type.getOrThrow()) {
            "minecraft:block" -> {
                if (isPhantomInfestedBlockLoot(filename)) {
                    logger.debug("Dropping phantom infested-block loot table: $filename")
                    Result.success(
                        ResourceSource(
                            type = ResourceSource.SourceType.LootTypes.BLOCK,
                            filename = filename,
                            producedItems = emptyList()
                        )
                    )
                } else {
                    lootTableParser.parse(json, filename)
                }
            }
            "minecraft:archaeology",
            "minecraft:fishing",
            "minecraft:block_interact",
            "minecraft:barter",
            "minecraft:entity",
            "minecraft:entity_interact",
            "minecraft:chest",
            "minecraft:gift",
            "minecraft:equipment",
            "minecraft:shearing" -> {
                lootTableParser.parse(json, filename)
            }
            else -> {
                logger.warn("Unknown loot table type: $type")
                Result.failure(ExtractionFailure.JsonFailure.UnknownValue(stringType, "type", json, filename))
            }
        }

        // Collected from the raw JSON rather than from the parsed source: the phantom-infested
        // branch above never reaches the parser, and a table's references have to be known even
        // when it produces nothing.
        return source.mapSuccess { ParsedLootTable(it, LootTableReferences.dilutedIn(json)) }
    }
}
