package app.mcorg.domain.idea

import app.mcorg.domain.model.idea.IdeaVisibility
import app.mcorg.test.fixtures.TestDataFactory
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdeaAccessTest {

    @Test
    fun `a stored visibility reads as its enum value`() {
        assertEquals(IdeaVisibility.PUBLIC, ideaVisibilityOf("PUBLIC"))
        assertEquals(IdeaVisibility.PRIVATE, ideaVisibilityOf("PRIVATE"))
    }

    /** IdeaVisibilityPlugin used to compare strings, so an unknown value counted as not public. */
    @Test
    fun `an unknown stored visibility fails closed, visible only to its creator and superadmins`() {
        val visibility = ideaVisibilityOf("UNLISTED")
        val creator = TestDataFactory.createTestTokenProfile(id = 1)
        val stranger = TestDataFactory.createTestTokenProfile(id = 2)
        val superAdmin = TestDataFactory.createTestTokenProfile(id = 3, roles = listOf("superadmin"))

        assertTrue(visibility.isVisibleTo(creator, createdBy = 1))
        assertTrue(visibility.isVisibleTo(superAdmin, createdBy = 1))
        assertFalse(visibility.isVisibleTo(stranger, createdBy = 1))
    }
}
