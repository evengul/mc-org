package app.mcorg.presentation.handler

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.invitation.handleAcceptInvitation
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.InviteParamPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.patch
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

/**
 * MCO-247: the world-membership gate (`WorldParticipantPlugin`) was hoisted to the whole
 * `/{worldId}` subtree in `WorldHandler`. Invite acceptance is intentionally routed
 * *outside* that subtree — at `/invites/{inviteId}/accept` in `InviteHandler` — precisely
 * so a non-member (the invitee) can still join. This test proves the hoist didn't
 * accidentally break that join flow.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class AcceptInvitationIT : WithUser() {

    @Test
    fun `an invitee who is not yet a world member can accept their invite`() = testApplication {
        val worldId = createWorld()
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

    private fun io.ktor.server.testing.ApplicationTestBuilder.installRoutes() {
        routing {
            install(AuthPlugin)
            route("/invites/{inviteId}") {
                install(InviteParamPlugin)
                patch("/accept") { call.handleAcceptInvitation() }
            }
        }
    }

    private fun createWorld(): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = "AcceptInvitation IT World",
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
