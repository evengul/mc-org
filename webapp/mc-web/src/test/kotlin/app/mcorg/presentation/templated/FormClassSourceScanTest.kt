package app.mcorg.presentation.templated

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * No template calls kotlinx.html's `form` with a positional string: the first parameter of `form`
 * is `action`, not `classes` as it is for `div`.
 *
 * `form("connect-discord__form")` compiles, renders `action="connect-discord__form"`, and leaves the
 * form without its class. HTMX intercepts the submit, so the form still works and nothing fails;
 * only the class's CSS silently never applies. Three settings forms shipped that way. The miss is
 * invisible to a status code or a click, so it is checked where it is made: in the source.
 */
class FormClassSourceScanTest {

    @Test
    fun `no template passes a positional string to form`() {
        val offenders = File(sourceRoot(), "app/mcorg/presentation").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    "${file.name}:${index + 1}".takeIf { POSITIONAL_FORM.containsMatchIn(line) }
                }
            }
            .toList()

        assertTrue(
            offenders.isEmpty(),
            "form's first parameter is `action`; pass the class as `form(classes = \"...\")`: $offenders",
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
        val POSITIONAL_FORM = Regex("""\bform\(\s*"""")
    }
}
