package app.mcorg.pipeline.auth

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.Role
import app.mcorg.domain.model.user.TokenProfile
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.delete
import io.ktor.server.routing.route
import io.ktor.server.testing.ApplicationTestBuilder
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
 * MCO-662 — what "Delete account" removes, which the privacy page states.
 *
 * `world` has no foreign key onto `users`, so the bare `DELETE FROM users` this replaced left a
 * world whose only member deleted their account in the database with nobody in it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
@Tag("database")
class DeleteAccountIT : WithUser() {

    @Test
    fun `A world the account was the only member of is deleted with it`() = testApplication {
        installDeleteAccount()
        val leaving = createExtraUser()
        val worldId = createWorld(leaving)

        val response = client.delete("/account") { addAuthCookie(this, leaving) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(userExists(leaving.id), "the account is deleted")
        assertFalse(worldExists(worldId), "a world with nobody left in it is deleted")
    }

    @Test
    fun `A world with other members is kept for them`() = testApplication {
        installDeleteAccount()
        val leaving = createExtraUser()
        val staying = createExtraUser()
        val worldId = createWorld(leaving)
        addMember(staying.id, worldId, Role.MEMBER)

        client.delete("/account") { addAuthCookie(this, leaving) }

        assertFalse(userExists(leaving.id))
        assertTrue(worldExists(worldId), "the other member keeps the world")
        assertEquals(listOf(staying.id), memberIds(worldId))
    }

    @Test
    fun `A banned member does not keep a world alive`() = testApplication {
        installDeleteAccount()
        val leaving = createExtraUser()
        val banned = createExtraUser()
        val worldId = createWorld(leaving)
        addMember(banned.id, worldId, Role.BANNED)

        client.delete("/account") { addAuthCookie(this, leaving) }

        assertFalse(worldExists(worldId), "nobody who can open the world is left in it")
        assertTrue(userExists(banned.id), "the banned member's own account is untouched")
    }

    @Test
    fun `Only the account's own worlds are touched`() = testApplication {
        installDeleteAccount()
        val leaving = createExtraUser()
        val bystander = createExtraUser()
        val theirs = createWorld(bystander)
        createWorld(leaving)

        client.delete("/account") { addAuthCookie(this, leaving) }

        assertTrue(worldExists(theirs))
        assertTrue(userExists(bystander.id))
    }

    @Test
    fun `Signed out, nothing is deleted`() = testApplication {
        installDeleteAccount()
        val owner = createExtraUser()
        val worldId = createWorld(owner)
        val client = createClient { followRedirects = false }

        val response = client.delete("/account")

        assertEquals(HttpStatusCode.Found, response.status)
        assertTrue(userExists(owner.id))
        assertTrue(worldExists(worldId))
    }

    private fun ApplicationTestBuilder.installDeleteAccount() {
        routing {
            install(AuthPlugin)
            route("/account") { delete { call.handleDeleteAccount() } }
        }
    }

    private fun createWorld(owner: TokenProfile): Int = runBlocking {
        val result = CreateWorldStep(owner).process(
            CreateWorldInput(
                name = "DeleteAccountIT ${System.nanoTime()}",
                description = "test",
                version = MinecraftVersion.fromString("1.21.4")
            )
        )
        (result as Result.Success).value
    }

    private fun addMember(userId: Int, worldId: Int, role: Role) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert("INSERT INTO world_members (user_id, world_id, display_name, world_role) VALUES (?, ?, ?, ?)"),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, userId)
                stmt.setInt(2, worldId)
                stmt.setString(3, "member-$userId")
                stmt.setInt(4, role.level)
            }
        ).process(Unit)
    }

    private fun exists(sql: String, id: Int): Boolean = runBlocking {
        val result = DatabaseSteps.query<Int, Boolean>(
            sql = SafeSQL.select(sql),
            parameterSetter = { stmt, value -> stmt.setInt(1, value) },
            resultMapper = { rs -> rs.next() && rs.getBoolean(1) }
        ).process(id)
        (result as Result.Success).value
    }

    private fun userExists(id: Int) = exists("SELECT EXISTS(SELECT 1 FROM users WHERE id = ?)", id)
    private fun worldExists(id: Int) = exists("SELECT EXISTS(SELECT 1 FROM world WHERE id = ?)", id)

    private fun memberIds(worldId: Int): List<Int> = runBlocking {
        val result = DatabaseSteps.query<Int, List<Int>>(
            sql = SafeSQL.select("SELECT user_id FROM world_members WHERE world_id = ? ORDER BY user_id"),
            parameterSetter = { stmt, value -> stmt.setInt(1, value) },
            resultMapper = { rs -> buildList { while (rs.next()) add(rs.getInt(1)) } }
        ).process(worldId)
        (result as Result.Success).value
    }
}
