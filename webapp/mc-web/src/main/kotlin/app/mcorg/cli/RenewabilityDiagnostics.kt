package app.mcorg.cli

import app.mcorg.config.Database
import app.mcorg.engine.renewability.Renewability
import app.mcorg.engine.renewability.RenewabilityFixture
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.minecraft.BuildRenewabilityForVersionStep
import app.mcorg.pipeline.minecraft.LoadRenewabilityInputsStep
import app.mcorg.pipeline.minecraft.LoadResourceSourcesForVersionStep
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.util.zip.GZIPOutputStream
import kotlin.system.exitProcess

/**
 * Read-only inspection of [Renewability] against the real ingested data (MCO-565). **Changes
 * nothing in the database.** Its job is reviewing a new Minecraft version, or a change to the
 * rules, against the implementation that ships rather than a second copy of it.
 *
 * ```
 * # what changed against the committed snapshot
 * mvn -q -pl mc-web exec:java@renewability-diagnostics -Dexec.args="version=26.3.0"
 *
 * # one item's verdict with every way to make it, and why each does or does not count
 * mvn -q -pl mc-web exec:java@renewability-diagnostics -Dexec.args="version=26.3.0 why tuff raw_iron"
 *
 * # the review page's data, for the versions named (see renewability-review/README.md)
 * mvn -q -pl mc-web exec:java@renewability-diagnostics -Dexec.args="review versions=26.3.0,1.21.11"
 *
 * # accept the current verdicts: write the snapshot and its fixture for the version
 * mvn -q -pl mc-web exec:java@renewability-diagnostics -Dexec.args="version=26.3.0 snapshot"
 * ```
 *
 * Snapshots live in `mc-engine/src/test/resources/renewability/<version>/` — `verdicts.tsv` and
 * the `sources.tsv.gz` fixture `RenewabilitySnapshotTest` rebuilds the graph from. Paths are
 * relative to `webapp/`, where `mvn` runs; `dir=` and `out=` override them.
 */
fun main(args: Array<String>) {
    val exitCode = runBlocking {
        try {
            run(args.toList())
        } catch (e: Throwable) {
            System.err.println("renewability-diagnostics failed: ${e.message}")
            e.printStackTrace()
            2
        } finally {
            Database.shutdown()
        }
    }
    exitProcess(exitCode)
}

private suspend fun run(args: List<String>): Int {
    var version: String? = null
    var versions: List<String> = emptyList()
    var dir = File("mc-engine/src/test/resources/renewability")
    var out = File("target/renewability-review")
    var why = false
    var snapshot = false
    var review = false
    val items = mutableListOf<String>()
    for (arg in args) {
        when {
            arg.startsWith("version=") -> version = arg.substringAfter('=')
            arg.startsWith("versions=") -> versions = arg.substringAfter('=').split(',').map { it.trim() }
            arg.startsWith("dir=") -> dir = File(arg.substringAfter('='))
            arg.startsWith("out=") -> out = File(arg.substringAfter('='))
            arg == "why" -> why = true
            arg == "snapshot" -> snapshot = true
            arg == "review" -> review = true
            else -> items += arg.removePrefix("minecraft:")
        }
    }

    if (review) return writeReview(versions.ifEmpty { listOfNotNull(version) }, dir, out)
    val v = version ?: run {
        System.err.println("version=<v> is required (or: review versions=<v1>,<v2>)")
        return 1
    }
    val renewability = load(v) ?: return 1
    return when {
        snapshot -> writeSnapshot(v, renewability, dir)
        why -> { printWhy(renewability, items); 0 }
        else -> { printSummary(v, renewability, readSnapshot(dir, v)); 0 }
    }
}

private suspend fun load(version: String): Renewability? =
    when (val r = BuildRenewabilityForVersionStep.process(version)) {
        is Result.Success -> r.value.renewability
        is Result.Failure -> {
            System.err.println("No renewability for version '$version' (${r.error}). Is it ingested?")
            null
        }
    }

private fun readSnapshot(dir: File, version: String): Map<String, Boolean>? =
    File(dir, "$version/verdicts.tsv").takeIf { it.exists() }?.readLines()
        ?.filter { it.isNotBlank() && !it.startsWith("#") }
        ?.associate { line -> line.split('\t').let { it[0] to (it[1] == "1") } }

private fun printSummary(version: String, renewability: Renewability, snapshot: Map<String, Boolean>?) {
    val verdicts = renewability.explain()
    println("Renewability · version $version")
    println("${verdicts.count { it.renewable }} of ${verdicts.size} items with a source are renewable")
    if (snapshot == null) {
        println("No snapshot for $version: every verdict is unreviewed. Review it, then run `snapshot`.")
        return
    }
    val now = verdicts.associate { it.itemId to it.renewable }
    val flipped = now.filter { (id, r) -> snapshot[id] != null && snapshot[id] != r }
    val added = now.keys - snapshot.keys
    val removed = snapshot.keys - now.keys
    if (flipped.isEmpty() && added.isEmpty() && removed.isEmpty()) {
        println("Matches the committed snapshot: nothing to review.")
        return
    }
    fun show(label: String, ids: Collection<String>) {
        if (ids.isNotEmpty()) println("$label (${ids.size}): ${ids.sorted().joinToString(" ")}")
    }
    show("Flipped to renewable", flipped.filterValues { it }.keys)
    show("Flipped to not renewable", flipped.filterValues { !it }.keys)
    show("New", added)
    show("Gone", removed)
}

private fun printWhy(renewability: Renewability, items: List<String>) {
    val wanted = items.toSet()
    for (v in renewability.explain().filter { wanted.isEmpty() || it.itemId in wanted }) {
        println("${v.itemId}: ${if (v.renewable) "renewable" else "NOT renewable"}")
        for (e in v.proofs) println("  + ${e.kind}: ${e.name}${needs(e)}")
        for (e in v.blocked) println("  - ${e.kind}: ${e.name} — ${e.why}")
    }
    val missing = wanted - renewability.explain().mapTo(hashSetOf()) { it.itemId }
    if (missing.isNotEmpty()) println("No source at all: ${missing.sorted().joinToString(" ")}")
}

private fun needs(e: Renewability.Evidence): String {
    val parts = e.all + e.tags + e.anyOf.map { "any of " + it.joinToString("/") }
    return if (parts.isEmpty()) "" else "  ← " + parts.joinToString(", ")
}

private suspend fun writeSnapshot(version: String, renewability: Renewability, dir: File): Int {
    val sources = when (val r = LoadResourceSourcesForVersionStep.process(version)) {
        is Result.Success -> r.value
        is Result.Failure -> { System.err.println("Could not load sources: ${r.error}"); return 1 }
    }
    val inputs = when (val r = LoadRenewabilityInputsStep.process(version)) {
        is Result.Success -> r.value
        is Result.Failure -> { System.err.println("Could not load registry and tags: ${r.error}"); return 1 }
    }
    val target = File(dir, version).apply { mkdirs() }
    GZIPOutputStream(File(target, "sources.tsv.gz").outputStream()).bufferedWriter().use {
        it.write(RenewabilityFixture(sources, inputs.registry, inputs.tags).write())
    }
    val verdicts = renewability.explain()
    File(target, "verdicts.tsv").writeText(
        "# Reviewed renewability verdicts for $version (MCO-565). Written by renewability-diagnostics\n" +
            "# `snapshot`; RenewabilitySnapshotTest fails when the engine disagrees. 1 = renewable.\n" +
            // The test borrows from this exact version, so a donor that is not snapshotted fails
            // loudly instead of reproducing different verdicts.
            (renewability.tradeDonorVersion?.let { "# trades borrowed from $it\n" } ?: "") +
            verdicts.joinToString("") { "${it.itemId}\t${if (it.renewable) 1 else 0}\n" }
    )
    println("Wrote ${verdicts.size} verdicts (${verdicts.count { it.renewable }} renewable) and the fixture to $target")
    return 0
}

/** Kinds in a fixed order, so a kind's index — what the page stores — never moves. */
private val KINDS = listOf(
    "chest", "recipe", "block loot", Renewability.MECHANIC_KIND, "wandering trader", "archaeology",
    "villager trade", "mob interaction", "gameplay drop", "mob drop", "synthetic source", "fishing",
    "shearing", "bartering", "mob equipment", "block interaction",
)

/**
 * `renewability-data.js` for the review page, next to a copy of the page itself. Each version
 * carries its committed snapshot, and the page settles every item whose verdict matches it — so
 * a version that matches opens with nothing to review, and a new one shows only what is new or
 * has flipped.
 */
private suspend fun writeReview(versions: List<String>, dir: File, out: File): Int {
    if (versions.isEmpty()) {
        System.err.println("review needs versions=<v1>,<v2>")
        return 1
    }
    val kinds = KINDS.toMutableList()
    val data = buildJsonObject {
        putJsonArray("versions") { versions.forEach { add(JsonPrimitive(it)) } }
        for (version in versions) {
            val renewability = load(version) ?: return 1
            val verdicts = renewability.explain()
            putJsonObject(version) {
                put("rows", JsonArray(verdicts.map { v ->
                    buildJsonArray {
                        add(JsonPrimitive(v.itemId))
                        add(JsonPrimitive(if (v.renewable) 1 else 0))
                        add(JsonArray(v.proofs.map { evidenceJson(it, kinds) }))
                        add(JsonArray(v.blocked.map { evidenceJson(it, kinds) }))
                    }
                }))
                putJsonObject("tags") {
                    for ((tag, members) in renewability.tagMembers()) put(tag, JsonArray(members.map(::JsonPrimitive)))
                }
                putJsonObject("snapshot") {
                    readSnapshot(dir, version)?.forEach { (id, r) -> put(id, if (r) 1 else 0) }
                }
            }
        }
        put("kinds", JsonArray(kinds.map(::JsonPrimitive)))
    }
    out.mkdirs()
    File(out, "renewability-data.js").writeText("window.RENEW_DATA=$data;\n")
    val page = File("mc-web/src/main/kotlin/app/mcorg/cli/renewability-review/index.html")
    if (page.exists()) page.copyTo(File(out, "index.html"), overwrite = true)
    println("Wrote the review page and its data for ${versions.joinToString()} to $out")
    return 0
}

private fun evidenceJson(e: Renewability.Evidence, kinds: MutableList<String>): JsonObject = buildJsonObject {
    put("k", kinds.indexOf(e.kind).takeIf { it >= 0 } ?: kinds.size.also { kinds += e.kind })
    put("n", e.name)
    if (e.all.isNotEmpty()) put("a", JsonArray(e.all.map(::JsonPrimitive)))
    if (e.tags.isNotEmpty()) put("t", JsonArray(e.tags.map(::JsonPrimitive)))
    if (e.anyOf.isNotEmpty()) put("o", JsonArray(e.anyOf.map { g -> JsonArray(g.map(::JsonPrimitive)) }))
    if (e.never) put("x", 1)
    e.why?.let { put("w", it) }
}
