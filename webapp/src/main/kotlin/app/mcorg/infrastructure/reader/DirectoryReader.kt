package app.mcorg.infrastructure.reader

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path

abstract class DirectoryReader<T>(private val path: String) : Reader() {
    open fun getValues(): List<T> {
        return with(resourceToFile()) {
            walk().filter { it.isFile }.map { parseFile(Path(it.path)) }.toList()
        }
    }

    private fun parseFile(file: Path): T {
        val content = Files.readString(file)
        return parseContent(content)
    }

    abstract fun parseContent(content: String): T

    private fun resourceToFile(): File {
        val resourceUrl = contextClassLoader.getResource(path)
        return File(checkNotNull(resourceUrl) { "Path not found '$path'" }.file)
    }
}