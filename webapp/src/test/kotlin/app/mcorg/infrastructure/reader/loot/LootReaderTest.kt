package app.mcorg.infrastructure.reader.loot

import app.mcorg.infrastructure.reader.LootReader
import junit.framework.TestCase
import kotlin.test.assertNotEquals

class LootReaderTest : TestCase() {

    fun testGetValues() {
        val loot = LootReader().getValues()
        assertNotEquals(loot.size, 0)
    }
}