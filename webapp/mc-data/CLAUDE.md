# mc-data

Minecraft server data extraction — parses recipes, loot tables, items, and tags from Minecraft's generated JSON data files.

## Purpose

Takes extracted Minecraft server JAR data (JSON files) and produces structured `ServerData` containing all items, recipes, and resource sources. This feeds into `mc-engine`'s graph builder.

## Tech

- Depends on: `mc-domain`, `mc-pipeline` (uses Step/Result pattern)
- Uses Caffeine for caching
- Uses SLF4J for logging
- Maven build, JVM 21 target
- Package: `app.mcorg.data.minecraft.*`

## Structure

```
ExtractServerDataSteps.kt   — Top-level orchestration: ExtractMinecraftDataStep, DeleteFileStep
PathResolvers.kt             — Resolves file paths within extracted server data
extract/
  ExtractRelevantMinecraftFilesStep.kt — Extracts JSON files from server JAR (nested zip)
  ExtractItemsStep.kt         — Parses items from registry data
  ExtractNamesStep.kt         — Extracts display names
  ExtractResourceSources.kt   — Combines recipes + loot into ResourceSources
  ExtractTagsStep.kt          — Parses Minecraft tags (item groups)
  JsonResultUtils.kt          — JSON parsing helpers
  ParseFilesRecursivelyStep.kt — Recursive directory traversal for JSON files
  loot/
    ExtractLootTables.kt       — Loot table orchestration
    LootTableParser.kt         — Parses loot_table JSON structure
    PoolParser.kt              — Parses loot pools
    EntryParser.kt             — Parses loot entries
  recipe/
    ExtractRecipesStep.kt      — Recipe extraction orchestration
    ShapedRecipeParser.kt      — 3x3 crafting grid recipes
    ShapelessRecipeParser.kt   — Orderless crafting recipes
    SimpleRecipeParser.kt      — Smelting, smoking, blasting, etc.
    SmithingTransformParser.kt — Smithing table recipes
    TransmuteRecipeParser.kt   — Transmutation recipes
    CraftingValuesParser.kt    — Shared crafting value extraction
    RecipeItemIdParser.kt      — Item ID resolution from recipe JSON
    RecipeQuantityParser.kt    — Quantity extraction
    MinecraftIdFactory.kt      — Creates MinecraftId from raw strings
failure/
  ExtractionFailure.kt        — Error types for extraction failures
```

## Key Concepts

- All extraction is done via **pipeline Steps** returning `Result<ExtractionFailure, T>`
- Input is `Pair<MinecraftVersion.Release, Path>` — version + path to extracted server data
- Output is `ServerData` containing items + resource sources
- Temporary files are cleaned up via `DeleteFileStep` in a `finally` block

## Build

```bash
cd webapp && mvn compile -pl mc-data
mvn test -pl mc-data
```

## Tests

Two categories of tests:

**Unit tests** (always run, no external data needed):
- Parser tests use inline JSON strings via `Json.parseToJsonElement(...)`
- Cover all recipe parsers, loot entry/pool/table parsers, JSON utils, path resolvers, and MinecraftIdFactory
- `TestUtils.kt` provides `executeAndAssertSuccess`, `executeAndAssertFailure`, `assertResultSuccess`, `assertResultFailure`

**E2E tests** (require extracted Minecraft server data):
- `ExtractItemsStepTest`, `ExtractRecipesStepTest`, `ExtractLootTablesTest`
- Run against real server data across 26+ Minecraft versions
- `ServerFileTest` base class with `@BeforeAll` auto-downloads and extracts server JARs if data is missing
- `ServerFileDownloader` test utility fetches versions from Fabric MC API and server JARs from a GitHub Gist
- Downloaded data is cached in CI via GitHub Actions cache
- Data stored at `src/test/resources/servers/extracted/` (gitignored)
