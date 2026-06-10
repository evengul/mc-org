package app.mcorg.data.minecraft.extract

import app.mcorg.data.minecraft.ServerPathResolvers
import app.mcorg.data.minecraft.extract.recipe.MinecraftIdFactory
import app.mcorg.data.minecraft.failure.ExtractionFailure
import app.mcorg.domain.model.minecraft.Item
import app.mcorg.domain.model.minecraft.MinecraftId
import app.mcorg.domain.model.minecraft.MinecraftTag
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.resources.ResourceQuantity
import app.mcorg.domain.model.resources.ResourceSource
import app.mcorg.domain.pipeline.Step
import app.mcorg.pipeline.Result
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Extracts villager trades from `data/minecraft/villager_trade/<profession>/<level>/<name>.json`.
 *
 * Introduced in Minecraft 26.1. Versions prior to 26.1 had trade data hard-coded in
 * `VillagerTrades.class` and returned nothing from the data-driven path, so this step simply
 * returns an empty list for them (directory absent).
 *
 * Each trade JSON is mapped to a single `ResourceSource`:
 * - `wants` and optional `additional_wants` become `requiredItems`
 * - `gives` becomes the single `producedItems` entry
 * - `given_item_modifiers` are ignored (this tool tracks resource flow, not enchantments)
 * - A `count` of `0` signals runtime-calculated price and is stored as
 *   [ResourceQuantity.RuntimeCalculation]
 *
 * The first path segment (profession directory name) determines which
 * [ResourceSource.SourceType.TradeTypes] value is used.
 */
data object ExtractVillagerTradesStep : Step<Pair<MinecraftVersion.Release, Path>, ExtractionFailure, List<ResourceSource>> {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val professionToType: Map<String, ResourceSource.SourceType> = mapOf(
        "armorer" to ResourceSource.SourceType.TradeTypes.ARMORER,
        "butcher" to ResourceSource.SourceType.TradeTypes.BUTCHER,
        "cartographer" to ResourceSource.SourceType.TradeTypes.CARTOGRAPHER,
        "cleric" to ResourceSource.SourceType.TradeTypes.CLERIC,
        "farmer" to ResourceSource.SourceType.TradeTypes.FARMER,
        "fisherman" to ResourceSource.SourceType.TradeTypes.FISHERMAN,
        "fletcher" to ResourceSource.SourceType.TradeTypes.FLETCHER,
        "leatherworker" to ResourceSource.SourceType.TradeTypes.LEATHERWORKER,
        "librarian" to ResourceSource.SourceType.TradeTypes.LIBRARIAN,
        "mason" to ResourceSource.SourceType.TradeTypes.MASON,
        "shepherd" to ResourceSource.SourceType.TradeTypes.SHEPHERD,
        "smith" to ResourceSource.SourceType.TradeTypes.SMITH,
        "toolsmith" to ResourceSource.SourceType.TradeTypes.TOOLSMITH,
        "weaponsmith" to ResourceSource.SourceType.TradeTypes.WEAPONSMITH,
        "wandering_trader" to ResourceSource.SourceType.TradeTypes.WANDERING_TRADER,
    )

    override suspend fun process(input: Pair<MinecraftVersion.Release, Path>): Result<ExtractionFailure, List<ResourceSource>> {
        val version = input.first
        val basePath = input.second
        val tradesPath = ServerPathResolvers.resolveVillagerTradesPath(basePath)

        // Versions prior to 26.1 simply don't ship this directory — return an empty list.
        if (!Files.exists(tradesPath)) {
            logger.debug("No villager_trade directory for version $version at $tradesPath — skipping trades")
            return Result.success(emptyList())
        }

        // Prime the name cache so withNames() can resolve display names below, even if trades
        // run before recipes in the future.
        ExtractNamesStep.getNames(input)

        return parseJsonFilesRecursively(version, tradesPath) { content, filename ->
            parseFile(content, filename)
        }
            .map { sources ->
                sources.map { it.withNames(input) }
                    .filter { it.producedItems.isNotEmpty() }
            }
    }

    private suspend fun parseFile(
        content: String,
        filename: String
    ): Result<ExtractionFailure, ResourceSource> {
        if (content.isEmpty()) {
            logger.warn("Empty villager trade file: $filename")
            return Result.failure(ExtractionFailure.MissingContent(filename))
        }

        val json = try {
            Json.parseToJsonElement(content)
        } catch (e: Exception) {
            logger.error("Error parsing JSON from villager trade file $filename", e)
            return Result.failure(ExtractionFailure.JsonFailure.ParseError(content, filename))
        }

        val profession = filename.substringBefore('/')
        val type = professionToType[profession]
        if (type == null) {
            logger.warn("Unknown villager trade profession: $profession in file $filename")
            return Result.failure(
                ExtractionFailure.JsonFailure.UnknownValue(profession, "profession", json, filename)
            )
        }

        val obj = when (val objResult = json.objectResult(filename)) {
            is Result.Success -> objResult.value
            is Result.Failure -> return Result.failure(objResult.error)
        }

        val wants = when (val w = parseItemStack(obj, "wants", filename)) {
            is Result.Success -> w.value
            is Result.Failure -> return Result.failure(w.error)
        }

        val additionalWants = parseItemStack(obj, "additional_wants", filename).getOrNull()

        val gives = when (val g = parseItemStack(obj, "gives", filename)) {
            is Result.Success -> g.value
            is Result.Failure -> return Result.failure(g.error)
        }

        val requiredItems = buildList {
            add(wants)
            if (additionalWants != null) add(additionalWants)
        }

        return Result.success(
            ResourceSource(
                type = type,
                filename = filename,
                requiredItems = requiredItems,
                producedItems = listOf(gives)
            )
        )
    }

    /**
     * Parses a `wants` / `additional_wants` / `gives` entry of the form
     * `{ "id": "minecraft:xxx", "count": N }`.
     *
     * - Missing `count` → default to 1.
     * - `count == 0` → [ResourceQuantity.RuntimeCalculation] (signals the real price is computed
     *   at runtime, e.g. librarian enchanted-book trades).
     */
    private suspend fun parseItemStack(
        obj: JsonObject,
        key: String,
        filename: String
    ): Result<ExtractionFailure, Pair<MinecraftId, ResourceQuantity>> {
        val element = obj[key] ?: return Result.failure(
            ExtractionFailure.JsonFailure.KeyNotFound(obj, key, filename)
        )
        val stack = when (val r = element.objectResult(filename)) {
            is Result.Success -> r.value
            is Result.Failure -> return Result.failure(r.error)
        }

        val idResult = stack.getResult("id", filename)
            .flatMap { it.primitiveResult(filename).mapSuccess { p -> p.content } }
        val id = when (idResult) {
            is Result.Success -> idResult.value
            is Result.Failure -> return Result.failure(idResult.error)
        }

        val quantity = parseQuantity(stack, filename)

        return Result.success(MinecraftIdFactory.minecraftIdFromId(id) to quantity)
    }

    private fun parseQuantity(stack: JsonObject, filename: String): ResourceQuantity {
        val countElement = stack["count"] ?: return ResourceQuantity.ItemQuantity(1)
        val primitive = when (val p = countElement.primitiveResult(filename)) {
            is Result.Success -> p.value
            is Result.Failure -> return ResourceQuantity.ItemQuantity(1)
        }
        // counts are stored as floats in the JSON (e.g. "count": 20.0); accept either form.
        val intValue = primitive.content.toDoubleOrNull()?.toInt()
            ?: primitive.content.toIntOrNull()
            ?: return ResourceQuantity.ItemQuantity(1)

        return when {
            intValue > 0 -> ResourceQuantity.ItemQuantity(intValue)
            intValue == 0 -> ResourceQuantity.RuntimeCalculation
            else -> ResourceQuantity.ItemQuantity(1)
        }
    }

    private suspend fun ResourceSource.withNames(namesInput: Pair<MinecraftVersion.Release, Path>): ResourceSource {
        return copy(
            requiredItems = requiredItems.map { it.first.withName(namesInput) to it.second },
            producedItems = producedItems.map { it.first.withName(namesInput) to it.second }
        )
    }

    private suspend fun MinecraftId.withName(namesInput: Pair<MinecraftVersion.Release, Path>): MinecraftId {
        return when (this) {
            is Item -> copy(name = ExtractNamesStep.getName(namesInput, this.id))
            is MinecraftTag -> this  // trades only contain plain item IDs, never tag refs
        }
    }
}
