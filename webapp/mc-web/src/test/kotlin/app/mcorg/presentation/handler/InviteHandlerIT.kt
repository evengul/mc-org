package app.mcorg.presentation.handler

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.invitation.handleAcceptInvitation
import app.mcorg.pipeline.invitation.handleDeclineInvitation
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.InviteParamPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.patch
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.patch
import io.ktor.server.routing.route
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The invitee's side of an invitation, answered from the Worlds page.
 *
 * MCO-247: the world-membership gate (`WorldParticipantPlugin`) covers the whole `/{worldId}`
 * subtree in `WorldHandler`. Answering an invite is routed *outside* it — at
 * `/invites/{inviteId}/…` in `InviteHandler` — precisely so a non-member (the invitee) can join.
 *
 * MCO-623: both answers re-render `#worlds-content`, which is what the row's buttons target, so a
 * joined world shows in the list at once and the answered invitation leaves it. They used to
 * swap the row out and announce `worldListChanged`, which nothing listened for: the world only
 * appeared on reload.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class InviteHandlerIT : WithUser() {

    @Test
    fun `an invitee who is not yet a world member can accept their invite`() = testApplication {
        val worldId = createWorld("Accepted IT World")
        val invitee = createExtraUser()
        val inviteId = createPendingInvite(worldId, fromUserId = user.id, toUserId = invitee.id)

        installRoutes()

        val response = client.patch("/invites/$inviteId/accept") {
            addAuthCookie(this, invitee)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, memberCount(worldId, invitee.id), "invitee must now be a world member")
        assertEquals("ACCEPTED", inviteStatus(inviteId))
    }

    @Test
    fun `accepting re-renders the world list with the joined world in it`() = testApplication {
        val worldId = createWorld("Joined Through Invite")
        val invitee = createExtraUser()
        val inviteId = createPendingInvite(worldId, fromUserId = user.id, toUserId = invitee.id)

        installRoutes()

        val body = client.patch("/invites/$inviteId/accept") {
            addAuthCookie(this, invitee)
        }.bodyAsText()

        val worldsContent = worldsContentOf(body)
        assertTrue(worldsContent.contains("Joined Through Invite"), "the joined world must be in the re-rendered list")
        assertFalse(worldsContent.contains("pending-invitation-$inviteId"), "the answered invitation must be gone")
        assertFalse(worldsContent.contains("No worlds yet"), "the invitee now has a world, so no empty state")
        assertTrue(body.contains("alert-dialog--success"), "the notice must be a styled toast")
        assertTrue(body.contains("Joined Joined Through Invite"))
        assertFalse(body.contains("notice--success"), "the unstyled notice is gone")
    }

    @Test
    fun `declining re-renders the world list without the world or the invitation`() = testApplication {
        val keptWorldId = createWorld("Still Pending World")
        val declinedWorldId = createWorld("Declined Through Invite")
        val invitee = createExtraUser()
        val keptInviteId = createPendingInvite(keptWorldId, fromUserId = user.id, toUserId = invitee.id)
        val declinedInviteId = createPendingInvite(declinedWorldId, fromUserId = user.id, toUserId = invitee.id)

        installRoutes()

        val response = client.patch("/invites/$declinedInviteId/decline") {
            addAuthCookie(this, invitee)
        }
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("DECLINED", inviteStatus(declinedInviteId))
        assertEquals(0, memberCount(declinedWorldId, invitee.id))

        val worldsContent = worldsContentOf(body)
        assertFalse(worldsContent.contains("pending-invitation-$declinedInviteId"), "the declined invitation must be gone")
        assertTrue(worldsContent.contains("pending-invitation-$keptInviteId"), "the other invitation stays")
        assertTrue(body.contains("alert-dialog--success"), "the notice must be a styled toast")
        assertTrue(body.contains("Declined invitation to Declined Through Invite"))
    }

    /** The response's `#worlds-content` element onwards; fails if the response has none. */
    private fun worldsContentOf(body: String): String {
        val at = body.indexOf("id=\"worlds-content\"")
        assertTrue(at >= 0, "the response must re-render #worlds-content, which the invitation buttons target")
        return body.substring(at)
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.installRoutes() {
        routing {
            install(AuthPlugin)
            route("/invites/{inviteId}") {
                install(InviteParamPlugin)
                patch("/accept") { call.handleAcceptInvitation() }
                patch("/decline") { call.handleDeclineInvitation() }
            }
        }
    }

    private fun createWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = name,
                description = "test",
                version = MinecraftVersion.fromString("1.21.4")
            )
        )
        (result as Result.Success).value
    }

    private fun createPendingInvite(worldId: Int, fromUserId: Int, toUserId: Int): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                """
                INSERT INTO invites (world_id, from_user_id, to_user_id, role, created_at, status, status_reached_at)
                VALUES (?, ?, ?, 'MEMBER', CURRENT_TIMESTAMP, 'PENDING', CURRENT_TIMESTAMP)
                RETURNING id
                """.trimIndent()
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, worldId)
                stmt.setInt(2, fromUserId)
                stmt.setInt(3, toUserId)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun memberCount(worldId: Int, userId: Int): Int = runBlocking {
        val result = DatabaseSteps.query<Unit, Int>(
            sql = SafeSQL.select("SELECT COUNT(*) AS c FROM world_members WHERE world_id = ? AND user_id = ?"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, worldId)
                stmt.setInt(2, userId)
            },
            resultMapper = { rs -> rs.next(); rs.getInt("c") }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun inviteStatus(inviteId: Int): String = runBlocking {
        val result = DatabaseSteps.query<Unit, String>(
            sql = SafeSQL.select("SELECT status FROM invites WHERE id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, inviteId) },
            resultMapper = { rs -> rs.next(); rs.getString("status") }
        ).process(Unit)
        (result as Result.Success).value
    }
}
