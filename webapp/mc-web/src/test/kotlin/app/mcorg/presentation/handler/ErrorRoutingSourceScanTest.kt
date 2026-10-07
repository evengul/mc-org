package app.mcorg.presentation.handler

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * An error the browser can be shown says where it goes (MCO-581, MCO-583, MCO-591).
 *
 * htmx swaps no error response into its target (`noSwap`, `Layout.kt`), and that also silences
 * `HX-Retarget`. So a 4xx or 5xx reaches the screen only as an out-of-band alert
 * (`respondRefusal`, `respondBadRequest`), a re-render in place (`respondInPlace`), or field
 * messages (`defaultHandleError` with a validation failure). Anything answered directly with an
 * error status is dropped without a trace, which is how about twenty refusals and the idea
 * form's Save went silent. Each check here is a way that happened.
 */
class ErrorRoutingSourceScanTest {

    @Test
    fun `no error status is answered past the helpers that route it`() {
        val offenders = kotlinSources()
            .filterNot { (path, _) -> path.startsWith("app/mcorg/api/") || path in ALLOWED }
            .flatMap { (path, text) -> directErrorResponses(text).map { line -> "$path:$line" } }

        assertTrue(
            offenders.isEmpty(),
            "answer an error with respondRefusal / respondBadRequest / respondInPlace / defaultHandleError, " +
                "or add the file to ALLOWED with the reason no browser shows it: $offenders",
        )
    }

    @Test
    fun `no slot is addressed by a hand-written id or a page-wide selector`() {
        val offenders = kotlinSources().flatMap { (path, text) ->
            text.lines().mapIndexedNotNull { index, line ->
                "$path:${index + 1}".takeIf { STALE_ROUTING.any { it.containsMatchIn(line) } }
            }
        }

        assertTrue(
            offenders.isEmpty(),
            "a field's slot is fieldError(<parameter>), placed by the form that sent the request: $offenders",
        )
    }

    /** Line numbers of `respond…(` calls whose arguments carry a 4xx or 5xx status. */
    private fun directErrorResponses(text: String): List<Int> =
        RESPOND_CALL.findAll(text).mapNotNull { call ->
            val args = argumentsFrom(text, call.range.last + 1)
            val line = text.substring(0, call.range.first).count { it == '\n' } + 1
            line.takeIf { ERROR_STATUS.containsMatchIn(args) }
        }.toList()

    /** The text between an opening parenthesis at [start] - 1 and its match, skipping strings. */
    private fun argumentsFrom(text: String, start: Int): String {
        var depth = 1
        var i = start
        var inString = false
        while (i < text.length && depth > 0) {
            val c = text[i]
            when {
                inString && c == '\\' -> i++
                c == '"' -> inString = !inString
                !inString && c == '(' -> depth++
                !inString && c == ')' -> depth--
            }
            i++
        }
        return text.substring(start, (i - 1).coerceAtLeast(start))
    }

    private fun kotlinSources(): List<Pair<String, String>> {
        val root = sourceRoot()
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
            .toList()
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

    private companion object {
        /** `respond(`, `respondHtml(`, `respondText(`, but not the routing helpers themselves. */
        val RESPOND_CALL = Regex("""\brespond(Html|Text)?\(""")

        val ERROR_STATUS = Regex(
            """HttpStatusCode\.(BadRequest|Unauthorized|Forbidden|NotFound|MethodNotAllowed|Conflict|Gone|""" +
                """UnprocessableEntity|PayloadTooLarge|TooManyRequests|InternalServerError|BadGateway|ServiceUnavailable)\b"""
        )

        val STALE_ROUTING = listOf(
            Regex("""hx-target-error"""),
            Regex(""""validation-error-"""),
            Regex("""id = "error-"""),
        )

        /** Files that answer an error status directly, and why nothing is lost by it. */
        val ALLOWED = setOf(
            // The boundary itself: it builds the routed shapes.
            "app/mcorg/presentation/handler/ErrorHandler.kt",
            // StatusPages: whole pages for page loads, and a 404 under htmx left as it is.
            "app/mcorg/presentation/plugins/Routing.kt",
            // AdminPlugin hides admin routes behind a bare 404; the banned page is a page load.
            "app/mcorg/presentation/plugins/RolePlugins.kt",
            // Demo sign-in does not exist in production: a page-load 404, given its page by StatusPages.
            "app/mcorg/pipeline/auth/DemoSignInPipeline.kt",
            // The device-link page is a plain form post; its refusal is that page with the reason.
            "app/mcorg/presentation/handler/link/LinkHandler.kt",
            // A row that no longer exists is left as it is on purpose.
            "app/mcorg/pipeline/resources/PlanRowPipeline.kt",
            // Machine and edge endpoints: no browser, no htmx.
            "app/mcorg/presentation/handler/ReadinessHandler.kt",
            "app/mcorg/presentation/plugins/MachineEndpointAuthPlugin.kt",
            "app/mcorg/presentation/plugins/EdgeOriginGate.kt",
            "app/mcorg/presentation/plugins/PreviewGate.kt",
            "app/mcorg/webhook/WebhookAdminRoutes.kt",
        )
    }
}
