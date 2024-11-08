package app.mcorg.infrastructure.reader

import junit.framework.TestCase

class ItemReaderTest : TestCase() {

    fun testGetRecipes() {
        val reader = ItemReader()
        val recipes = reader.getRecipes()
        val errors = recipes.filter { it.output.first.name.contains("ERROR") }

        if (errors.isNotEmpty()) errors.forEach { println(it) }

        assertEquals(errors.size, 0)
    }
}