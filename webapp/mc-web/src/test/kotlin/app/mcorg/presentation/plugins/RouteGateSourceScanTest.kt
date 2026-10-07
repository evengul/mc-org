package app.mcorg.presentation.plugins

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every route plugin in `presentation/plugins` gates with [onUnansweredCall], never a bare
 * `onCall` (MCO-158).
 *
 * The rule used to be a hand-written `if (call.isHandled) return@onCall` in the plugins that
 * needed it, and five of them didn't have it. The miss is invisible to a status-code assertion,
 * so it is checked where it is made: in the source.
 */
class RouteGateSourceScanTest {

    @Test
    fun `no route plugin uses a bare onCall`() {
        val pluginsDir = File(sourceRoot(), "app/mcorg/presentation/plugins")
        val offenders = pluginsDir.listFiles { file -> file.extension == "kt" }!!
            .filter { it.name != "RouteGate.kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    "${file.name}:${index + 1}".takeIf { BARE_ON_CALL.containsMatchIn(line) }
                }
            }

        assertTrue(
            offenders.isEmpty(),
            "use onUnansweredCall so the plugin stands down after an earlier refusal: $offenders",
        )
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
        val BARE_ON_CALL = Regex("""\bonCall\s*\{""")
    }
}
