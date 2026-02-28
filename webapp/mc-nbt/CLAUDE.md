# mc-nbt

NBT (Named Binary Tag) binary format parser for Minecraft schematics and Litematica files.

## Purpose

Parses Minecraft's NBT binary format — the serialization format used by Minecraft for world data, schematics, and Litematica `.litematic` files. Extracts structured data (block lists, dimensions, metadata) from uploaded files.

## Tech

- Depends on: `mc-domain`, `mc-pipeline` (uses Step/Result)
- Consumes pipeline test JAR for test utilities
- Pure binary I/O — no external parsing libraries
- Maven build, JVM 21 target
- Package: `app.mcorg.nbt.*`

## Structure

```
failure/
  NBTFailure.kt            — Error types for parse failures
io/
  BigEndianNbtInputStream.kt — Big-endian byte stream reader
  BinaryNbtDeserializer.kt   — Core NBT binary -> Tag tree deserializer
  BinaryParseFailure.kt      — Low-level parse error types
  CompressionType.kt          — GZip/Uncompressed detection
  Deserializer.kt             — Deserializer interface
  MaxDepthIO.kt               — Depth-limited reading to prevent stack overflow
  NbtInput.kt                 — Input abstraction
tag/
  Tag.kt                      — Sealed interface hierarchy: ByteTag, IntTag, StringTag, CompoundTag, ListTag, etc.
  NamedTag.kt                 — Named wrapper around Tags
util/
  LitematicaReader.kt         — High-level reader: .litematic file -> Litematica domain model
```

## Key Concepts

- **Tag hierarchy**: `Tag<T>` sealed interface with `EndTag`, `ByteTag`, `ShortTag`, `IntTag`, `LongTag`, `FloatTag`, `DoubleTag`, `StringTag`, `ListTag`, `CompoundTag`, `ByteListTag`, `IntListTag`, `LongListTag`
- **CompoundTag** is the main container (Map<String, Tag<*>>)
- **LitematicaReader** is the entry point for parsing `.litematic` files into `Litematica` domain objects
- Max depth of 512 to prevent malicious inputs

## Build

```bash
cd webapp && mvn compile -pl mc-nbt
mvn test -pl mc-nbt
```

## Tests

Located in `src/test/kotlin/app/mcorg/nbt/`. `LitematicaReaderTest` tests end-to-end parsing with real `.litematic` test fixtures.
