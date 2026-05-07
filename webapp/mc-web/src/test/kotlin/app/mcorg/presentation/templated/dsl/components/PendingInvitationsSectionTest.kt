package app.mcorg.presentation.templated.dsl.components

import app.mcorg.domain.model.invite.Invite
import app.mcorg.domain.model.invite.InviteStatus
import app.mcorg.domain.model.user.Role
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import org.junit.jupiter.api.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PendingInvitationsSectionTest {

    private fun render(invitations: List<Invite>): String =
        createHTML().div { pendingInvitationsSection(invitations) }

    private fun invite(
        id: Int = 1,
        worldName: String = "Survival World",
        fromUsername: String = "alice",
        role: Role = Role.MEMBER,
        createdAt: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC),
    ): Invite = Invite(
        id = id,
        worldId = 10 + id,
        worldName = worldName,
        from = 100,
        fromUsername = fromUsername,
        to = 200,
        toUsername = "bob",
        role = role,
        createdAt = createdAt,
        expiresAt = createdAt.plusDays(7),
        status = InviteStatus.Pending(createdAt),
    )

    @Test
    fun `empty invitations list renders nothing`() {
        val html = render(emptyList())
        assertFalse(html.contains("pending-invitations-section"))
        assertFalse(html.contains("Pending invitations"))
    }

    @Test
    fun `single invitation renders section with row`() {
        val html = render(listOf(invite(id = 42, worldName = "Creative Base", fromUsername = "alice")))
        assertTrue(html.contains("id=\"pending-invitations-section\""))
        assertTrue(html.contains("Pending invitations"))
        assertTrue(html.contains("Creative Base"))
        assertTrue(html.contains("Invited by alice"))
        assertTrue(html.contains("Role: Member"))
        assertTrue(html.contains("id=\"pending-invitation-42\""))
    }

    @Test
    fun `multiple invitations render one row each`() {
        val html = render(listOf(
            invite(id = 1, worldName = "World A"),
            invite(id = 2, worldName = "World B"),
        ))
        assertTrue(html.contains("id=\"pending-invitation-1\""))
        assertTrue(html.contains("id=\"pending-invitation-2\""))
        assertTrue(html.contains("World A"))
        assertTrue(html.contains("World B"))
    }

    @Test
    fun `invitation row wires accept and decline HTMX attributes`() {
        val html = render(listOf(invite(id = 7, worldName = "Mine World")))
        assertTrue(html.contains("hx-patch=\"/invites/7/accept\""))
        assertTrue(html.contains("hx-patch=\"/invites/7/decline\""))
        assertTrue(html.contains("hx-target=\"#pending-invitation-7\""))
        assertTrue(html.contains("hx-swap=\"outerHTML\""))
        assertTrue(html.contains("Accept invitation to join Mine World?"))
        assertTrue(html.contains("Decline invitation to Mine World?"))
    }

    @Test
    fun `formatRelativeExpiry returns expected phrasing`() {
        val now = ZonedDateTime.of(2026, 4, 9, 12, 0, 0, 0, ZoneOffset.UTC)
        assertEquals("in 7 days", formatRelativeExpiry(now.plusDays(7), now))
        assertEquals("in 2 days", formatRelativeExpiry(now.plusDays(2), now))
        assertEquals("tomorrow", formatRelativeExpiry(now.plusDays(1).plusHours(1), now))
        assertEquals("in 2 hours", formatRelativeExpiry(now.plusHours(2), now))
        assertEquals("in 1 hour", formatRelativeExpiry(now.plusHours(1), now))
        assertEquals("in 30 minutes", formatRelativeExpiry(now.plusMinutes(30), now))
        assertEquals("in 1 minute", formatRelativeExpiry(now.plusMinutes(1), now))
        assertEquals("today", formatRelativeExpiry(now.plusSeconds(10), now))
        assertEquals("now", formatRelativeExpiry(now.minusSeconds(1), now))
    }
}
