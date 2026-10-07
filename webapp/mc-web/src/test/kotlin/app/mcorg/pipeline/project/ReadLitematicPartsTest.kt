package app.mcorg.pipeline.project

import app.mcorg.presentation.plugins.MAX_SCHEMATIC_UPLOAD_BYTES
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * MCO-421: the part loop shared by the project import and the idea upload. Its bounds are what
 * still hold on a route that lost its body limit, and it must stop reading once either is broken.
 */
class ReadLitematicPartsTest {

    @Test
    fun `reads every litematic and hands the other fields over`() = runBlocking {
        val fields = mutableListOf<String>()
        val upload = Upload(
            file("overworld.litematic", ByteArray(10) { 1 }),
            field("name", "Iron farm"),
            file("nether.litematic", ByteArray(20) { 2 }),
        )

        val read = upload.readLitematicParts { fields += "${it.name}=${it.value}" }

        assertIs<LitematicParts.Read>(read)
        assertEquals(listOf("overworld.litematic" to 10, "nether.litematic" to 20), read.files.map { it.fileName to it.content.size })
        assertEquals(listOf("name=Iron farm"), fields)
        assertEquals(3, upload.released)
    }

    @Test
    fun `a file that is not a litematic is released without being read`() = runBlocking {
        var opened = false
        val upload = Upload({ upload ->
            PartData.FileItem({ opened = true; ByteReadChannel(ByteArray(10)) }, {}, disposition("notes.txt")) { upload.released++ }
        })

        val read = upload.readLitematicParts()

        assertEquals(LitematicParts.Read(emptyList()), read)
        assertEquals(false, opened)
        assertEquals(1, upload.released)
    }

    @Test
    fun `a file that uses exactly the budget is accepted`() = runBlocking {
        val read = Upload(file("big.litematic", ByteArray(MAX_SCHEMATIC_UPLOAD_BYTES.toInt()))).readLitematicParts()

        assertIs<LitematicParts.Read>(read)
        assertEquals(MAX_SCHEMATIC_UPLOAD_BYTES.toInt(), read.files.single().content.size)
    }

    @Test
    fun `files that together pass the budget are too large, and nothing after them is read`() = runBlocking {
        val half = (MAX_SCHEMATIC_UPLOAD_BYTES / 2 + 1).toInt()
        val upload = Upload(
            file("a.litematic", ByteArray(half)),
            file("b.litematic", ByteArray(half)),
            *Array(100) { field("filler$it", "x") },
        )

        assertIs<LitematicParts.TooLarge>(upload.readLitematicParts())
        assertEquals(2, upload.partsRead)
        assertEquals(2, upload.released)
    }

    @Test
    fun `one file past the cap is too many, and nothing after it is read`() = runBlocking {
        val upload = Upload(
            *Array(ReceiveSchematicStep.MAX_FILES + 1) { file("part$it.litematic", ByteArray(4)) },
            *Array(100) { field("filler$it", "x") },
        )

        assertIs<LitematicParts.TooMany>(upload.readLitematicParts())
        assertEquals(ReceiveSchematicStep.MAX_FILES + 1, upload.partsRead)
        assertEquals(ReceiveSchematicStep.MAX_FILES + 1, upload.released)
    }

    private inner class Upload(vararg parts: (Upload) -> PartData) : MultiPartData {
        private val pending = ArrayDeque(parts.toList())
        var partsRead = 0
        var released = 0

        override suspend fun readPart(): PartData? = pending.removeFirstOrNull()?.let { partsRead++; it(this) }
    }

    private fun file(name: String, bytes: ByteArray): (Upload) -> PartData = { upload ->
        PartData.FileItem({ ByteReadChannel(bytes) }, {}, disposition(name)) { upload.released++ }
    }

    private fun field(name: String, value: String): (Upload) -> PartData = { upload ->
        PartData.FormItem(value, {}, Headers.build {
            append(HttpHeaders.ContentDisposition, "form-data; name=\"$name\"")
        }) { upload.released++ }
    }

    private fun disposition(fileName: String) = Headers.build {
        append(HttpHeaders.ContentDisposition, "form-data; name=\"schematicFile\"; filename=\"$fileName\"")
    }
}
