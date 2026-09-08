package app.mcorg.pipeline

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The boundary `SafeSQL` documents, enforced at the source (MCO-551): every SQL text handed to
 * `SafeSQL.select/with/insert/update/delete` under `src/main` is a single string literal with no
 * interpolation, unless the site is listed in [allowed] with the reason it is safe.
 *
 * Why a source scan and not a check inside `SafeSQL`: by the time a `String` reaches the factory
 * the interpolation has already happened, so runtime code cannot tell a constant from a request
 * value. The source can. Reading it once per test run costs nothing and turns "never interpolate"
 * from a rule nobody could check (MCO-435 found three silent exceptions) into one that fails the
 * build with the offending line named.
 *
 * Adding an entry to [allowed] is a review decision. Name the identifier and say why it cannot
 * carry request input — a `const val`, a `when` over fixed clauses, a run of `?` placeholders. An
 * entry that no longer matches anything fails the test too, so the list cannot rot.
 */
class SafeSqlSourceScanTest {

    /** Path relative to `src/main/kotlin` → identifier spliced into SQL → why that is safe. */
    private val allowed: Map<String, Map<String, String>> = mapOf(
        "app/mcorg/webhook/WebhookStore.kt" to mapOf(
            "CLAIM_LEASE_MINUTES" to "private const val inside an INTERVAL literal; MCO-435 tracks binding it through make_interval instead",
        ),
        "app/mcorg/api/ApiStore.kt" to mapOf(
            "CONTAINER_TAG_COLUMNS" to "private const val column list shared by several selects",
        ),
        "app/mcorg/api/ReporterTokenStore.kt" to mapOf(
            "REPORTER_TOKEN_COLUMNS" to "private const val column list shared by several selects",
        ),
        "app/mcorg/api/ReporterStore.kt" to mapOf(
            "placeholders" to "a run of literal '?' sized to the id list; every id is bound with setLong",
        ),
        "app/mcorg/pipeline/task/SearchTasksStep.kt" to mapOf(
            "sortBy" to "chosen by a `when` over fixed ORDER BY clauses; the request value itself never reaches the SQL",
        ),
        "app/mcorg/pipeline/project/commonsteps/SearchProjectsStep.kt" to mapOf(
            "sortBy" to "chosen by a `when` over fixed ORDER BY clauses; the request value itself never reaches the SQL",
        ),
        "app/mcorg/pipeline/world/commonsteps/GetWorldStep.kt" to mapOf(
            "projectTallyColumns" to "expands a fixed table alias into a fixed column list",
        ),
        "app/mcorg/pipeline/world/commonsteps/GetPermittedWorldsStep.kt" to mapOf(
            "projectTallyColumns" to "expands a fixed table alias into a fixed column list",
        ),
        "app/mcorg/pipeline/idea/SearchIdeasPipeline.kt" to mapOf(
            "baseSql" to "splices IdeaSqlBuilder's WHERE clause: fixed fragments over an allowlisted key set, every value bound",
        ),
        "app/mcorg/pipeline/idea/commonsteps/GetItemsInVersionRangeStep.kt" to mapOf(
            "getQuery" to "selects between two literal statements",
        ),
    )

    private val call = Regex("""SafeSQL\.(select|with|insert|update|delete)\s*\(""")
    private val interpolation = Regex("""\$\{?([A-Za-z_][A-Za-z_0-9]*)""")
    private val leadingIdentifier = Regex("""^([A-Za-z_][A-Za-z_0-9]*)""")

    @Test
    fun `every SafeSQL text is a literal, or its splices are allowlisted with a reason`() {
        val root = sourceRoot()
        val offences = mutableListOf<String>()
        val seen = mutableMapOf<String, MutableSet<String>>()
        var calls = 0

        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val relative = file.relativeTo(root).path.replace(File.separatorChar, '/')
            val text = file.readText()
            for (match in call.findAll(text)) {
                calls++
                val argument = argumentAfter(text, match.range.last + 1)
                val line = text.substring(0, match.range.first).count { it == '\n' } + 1
                val spliced = splicedIdentifiers(argument)
                seen.getOrPut(relative) { mutableSetOf() } += spliced
                val permitted = allowed[relative].orEmpty().keys
                val bad = spliced - permitted
                if (bad.isNotEmpty()) offences += "$relative:$line splices ${bad.joinToString()}"
            }
        }

        assertTrue(calls > 200, "found only $calls SafeSQL call sites under $root — is that the right source root?")

        val stale = allowed.flatMap { (file, ids) ->
            ids.keys.filterNot { it in seen[file].orEmpty() }.map { "$file: $it" }
        }

        if (offences.isEmpty() && stale.isEmpty()) return
        fail(
            buildString {
                if (offences.isNotEmpty()) {
                    appendLine("SQL text that is not a single literal, at sites not on the allowlist:")
                    offences.forEach { appendLine("  - $it") }
                    appendLine(
                        "Bind the value with '?' instead. If it genuinely cannot be bound (a const column list, a " +
                            "`when` over fixed clauses), add the identifier to `allowed` in this test with the reason."
                    )
                }
                if (stale.isNotEmpty()) {
                    appendLine("Allowlist entries that no longer match anything — remove them:")
                    stale.forEach { appendLine("  - $it") }
                }
            }.trimEnd()
        )
    }

    /** Identifiers the argument splices: `$name` / `${name...}` inside a literal, or the leading identifier of a non-literal. */
    private fun splicedIdentifiers(argument: String): Set<String> {
        val trimmed = argument.trim()
        return if (trimmed.startsWith('"')) {
            interpolation.findAll(trimmed).map { it.groupValues[1] }.toSet()
        } else {
            setOfNotNull(leadingIdentifier.find(trimmed)?.groupValues?.get(1) ?: trimmed)
        }
    }

    /**
     * The text between the factory's `(` and its matching `)`. Parentheses are counted naively,
     * which is right for SQL — its own parentheses balance — and would only mislead on an
     * unbalanced `)` inside a string literal, of which there are none.
     */
    private fun argumentAfter(text: String, start: Int): String {
        var depth = 1
        var i = start
        while (depth > 0 && i < text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> depth--
            }
            i++
        }
        return text.substring(start, i - 1)
    }

    private fun sourceRoot(): File {
        var dir: File? = File(javaClass.protectionDomain.codeSource.location.toURI())
        while (dir != null) {
            val candidate = File(dir, "src/main/kotlin")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        fail("could not find src/main/kotlin above the test classes directory")
    }
}
