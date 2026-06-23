# mc-data

Minecraft server data extraction — parses recipes, loot tables, items, and tags from Minecraft's generated JSON data files.

## Purpose

Takes extracted Minecraft server JAR data (JSON files) and produces structured `ServerData` containing all items, recipes, and resource sources. This feeds into `mc-engine`'s graph builder.

## Tech

- Depends on: `mc-domain`, `mc-pipeline` (uses Step/Result pattern)
- Uses SLF4J for logging
- Maven build, JVM 21 target
- Package: `app.mcorg.data.minecraft.*`

## Structure

```
ExtractServerDataSteps.kt   — Top-level orchestration: ExtractMinecraftDataStep, DeleteFileStep
PathResolvers.kt             — Resolves file paths within extracted server data (pre/post-1.21 naming)
extract/
  ExtractRelevantMinecraftFilesStep.kt — Extracts JSON files from server JAR (nested zip)
  ExtractionContext.kt        — Immutable per-version context (names, tags, item registry)
                                + ExtractionContextFactory + ResourceSource.withNames
  ExtractItemsStep.kt         — Maps the context's lang-derived item registry to Items
  ExtractResourceSources.kt   — Combines recipes + loot + trades into ResourceSources
  ExtractVillagerTradesStep.kt — Parses villager trade JSON (26.1+)
  JsonResultUtils.kt          — JSON parsing helpers
  ParseFilesRecursively.kt    — Stateless concurrent recursive JSON-directory walker
  loot/
    ExtractLootTables.kt       — Loot table orchestration (type whitelist, hardcoded loot)
    LootTableParser.kt         — Parses tables, pools, and entries (mutually recursive, one class)
    LootYield.kt               — LootEntry/LootDrop models + number-provider averaging
  recipe/
    ExtractRecipesStep.kt      — Recipe extraction orchestration (type dispatch)
    ShapedRecipeParser.kt      — 3x3 crafting grid recipes
    ShapelessRecipeParser.kt   — Orderless crafting recipes
    SimpleRecipeParser.kt      — Smelting, smoking, blasting, etc.
    SmithingTransformParser.kt — Smithing table recipes
    TransmuteRecipeParser.kt   — Transmutation recipes
    CraftingImbueParser.kt     — crafting_imbue recipes (26.1+)
    CraftingValuesParser.kt    — Shared crafting value extraction
    ItemRefParser.kt           — parseItemRef: one helper for all ingredient/result id spellings
    RecipeItemIdParser.kt      — Result-item id resolution across version schemas
    RecipeQuantityParser.kt    — Quantity extraction
    MinecraftIdFactory.kt      — Creates MinecraftId from raw strings
failure/
  ExtractionFailure.kt        — Error types for extraction failures
```

## Key Concepts

- `ExtractMinecraftDataStep` builds an **`ExtractionContext`** once per version (display names
  from `lang/en_us.json`, item/block tags, and the item registry derived from lang keys), then
  passes it to the item/recipe/loot/trade steps as their Step input. There is no global or
  mutable state — parsers are pure functions of (context, json).
- Tag names collide between `tags/item/` and `tags/block/` (e.g. `banners`); the context loads
  item tags with precedence and block tags only fill unused names, deterministically.
- Names are resolved at the end of each step via the shared `ResourceSource.withNames(context)`.
- All extraction is **fail-fast by design**: any single bad file fails the whole version with
  `ExtractionFailure.Multiple` so new/changed formats are noticed, not silently dropped.
  Unknown recipe types that contain `_special_` (and a few cosmetic types) are deliberately
  IGNORED instead.
- Output is `ServerData` containing items + resource sources.
- Temporary files are cleaned up via `DeleteFileStep` in a `finally` block.
- **Changing extraction output for the same server jar** (new/changed synthetic sources, recipe/loot
  parsers, item/tag mapping) must bump `ExtractionVersion.CURRENT` and add a matching `History:` line in
  `ExtractionVersion.kt`. Ingestion re-ingests any version whose stored `extraction_version` is older than
  `CURRENT`, so the bump triggers exactly one automatic re-ingest per version — without it, unchanged jars
  keep their stale data.

## Build

```bash
cd webapp && mvn compile -pl mc-data
mvn test -pl mc-data
```

## Tests

Two categories of tests:

**Unit tests** (always run, no external data needed):
- Parser tests use inline JSON strings via `Json.parseToJsonElement(...)`
- Cover all recipe parsers, loot table/pool/entry parsing, the extraction context,
  JSON utils, path resolvers, and MinecraftIdFactory
- `TestUtils.kt` provides `executeAndAssertSuccess`, `executeAndAssertFailure`, `assertResultSuccess`, `assertResultFailure`

**E2E tests** (require extracted Minecraft server data):
- `ExtractItemsStepTest`, `ExtractRecipesStepTest`, `ExtractLootTablesTest`, `ExtractVillagerTradesStepTest`
- Run against real server data across 30 Minecraft versions; steps take a context built via
  `ServerFileTest.contextFor(version)`
- `ServerFileTest` base class with `@BeforeAll` auto-downloads and extracts server JARs if data is missing
- `ServerFileDownloader` test utility fetches versions from Fabric MC API and server JARs from a GitHub Gist
- Downloaded data is cached in CI via GitHub Actions cache
- Data stored at `src/test/resources/servers/extracted/` (gitignored)
