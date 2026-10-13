package app.mcorg.presentation.templated.dsl

import app.mcorg.pipeline.resources.CarriedBoxes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The line that says who is carrying a project's tagged shulker boxes (MCO-548). It is read by
 * someone who went to the storage and found boxes gone, so it has to name the player and say the
 * counts are not wrong for it.
 */
class CarriedTextTest {

    @Test
    fun `one player with one box`() {
        assertEquals(
            "Even is carrying 1 tagged shulker box. What is in it still counts.",
            carriedText(listOf(CarriedBoxes("Even", 1))),
        )
    }

    @Test
    fun `several players are all named, most boxes first as the query orders them`() {
        assertEquals(
            "Even is carrying 3 tagged shulker boxes; Alex 1. What is in them still counts.",
            carriedText(listOf(CarriedBoxes("Even", 3), CarriedBoxes("Alex", 1))),
        )
    }
}
