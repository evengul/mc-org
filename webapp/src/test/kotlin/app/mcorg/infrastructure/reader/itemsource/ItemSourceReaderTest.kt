package app.mcorg.infrastructure.reader.itemsource

import kotlin.test.expect

import kotlin.test.Test

class ItemSourceReaderTest {

    @Test
    fun testReadItemSources() {
        val data = ItemSourceReader.readItemSources()
        expect("1.21.4") { data.version }
    }

}