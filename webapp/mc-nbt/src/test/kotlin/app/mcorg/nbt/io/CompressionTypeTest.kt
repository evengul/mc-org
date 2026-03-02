package app.mcorg.nbt.io

import app.mcorg.pipeline.TestUtils.assertResultSuccess
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CompressionTypeTest {
    @Test
    fun `detect GZIP from magic bytes`() {
        val result = assertResultSuccess(CompressionType.detect(byteArrayOf(0x1F, 0x8B.toByte(), 0x00)))
        assertEquals(CompressionType.GZIP, result)
    }

    @Test
    fun `detect ZLIB from magic bytes`() {
        val result = assertResultSuccess(CompressionType.detect(byteArrayOf(0x78, 0x9C.toByte(), 0x00)))
        assertEquals(CompressionType.ZLIB, result)
    }

    @Test
    fun `detect NONE for other bytes`() {
        val result = assertResultSuccess(CompressionType.detect(byteArrayOf(0x0A, 0x00)))
        assertEquals(CompressionType.NONE, result)
    }

    @Test
    fun `detect NONE for empty array`() {
        val result = assertResultSuccess(CompressionType.detect(byteArrayOf()))
        assertEquals(CompressionType.NONE, result)
    }

    @Test
    fun `detect NONE for single byte`() {
        val result = assertResultSuccess(CompressionType.detect(byteArrayOf(0x1F)))
        assertEquals(CompressionType.NONE, result)
    }
}
