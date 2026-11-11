package app.mcorg.presentation.handler

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.Role
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.pipeline.world.handleDeleteWorld
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.WorldOwnerPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
@Tag("database")
class GetLandingIT : WithUser() {

    @Test
    fun `Redirect to sign-in when not authenticated`() = testApplication {
        val client = createClient { followRedirects = false }
        routing {
            install(AuthPlugin)
            get("/") { call.handleGetLanding() }
        }

        val response = client.get("/")
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/auth/sign-in?redirect_to=%2F", response.headers["Location"])
    }

    @Test
    fun `Redirect to worlds when activeWorldId is null`() = testApplication {
        val client = createClient { followRedirects = false }
        routing {
            install(AuthPlugin)
            get("/") { call.handleGetLanding() }
        }

        val response = client.get("/") { addAuthCookie(this) }
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/worlds", response.headers["Location"])
    }

    @Test
    fun `Redirect to the world's roadmap when the user belongs to the active world`() = testApplication {
        val client = createClient { followRedirects = false }
        installLanding()
        val worldId = createWorld()

        val response = client.get("/") { addAuthCookie(this, user.copy(activeWorldId = worldId)) }
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/worlds/$worldId/roadmap", response.headers["Location"])
    }

    @Test
    fun `Redirect to worlds when the active world does not exist`() = testApplication {
        val client = createClient { followRedirects = false }
        installLanding()

        val response = client.get("/") { addAuthCookie(this, user.copy(activeWorldId = 999_999)) }
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/worlds", response.headers["Location"])
    }

    @Test
    fun `Deleting the active world lands on worlds, and the landing stops pointing at it`() = testApplication {
        val client = createClient { followRedirects = false }
        installLanding()
        routing {
            route("/worlds/{worldId}/settings") {
                install(WorldParamPlugin)
                install(WorldOwnerPlugin)
                delete { call.handleDeleteWorld() }
            }
        }
        val worldId = createWorld()
        val staleToken = user.copy(activeWorldId = worldId)

        // Prime the membership cache with "yes": the delete has to clear it.
        assertEquals("/worlds/$worldId/roadmap", client.get("/") { addAuthCookie(this, staleToken) }.headers["Location"])

        val deleted = client.delete("/worlds/$worldId/settings") { addAuthCookie(this, staleToken) }
        assertEquals(HttpStatusCode.OK, deleted.status)
        assertEquals("/worlds", deleted.headers["HX-Redirect"])

        val landing = client.get("/") { addAuthCookie(this, staleToken) }
        assertEquals(HttpStatusCode.Found, landing.status)
        assertEquals("/worlds", landing.headers["Location"])
    }

    @Test
    fun `Redirect to worlds after being removed from the active world`() = testApplication {
        val client = createClient { followRedirects = false }
        installLanding()
        val worldId = createWorld()
        val member = createExtraUser()
        runBlocking {
            DatabaseSteps.update<Unit>(
                SafeSQL.insert("INSERT INTO world_members (user_id, world_id, display_name, world_role) VALUES (?, ?, ?, ?)"),
                parameterSetter = { stmt, _ ->
                    stmt.setInt(1, member.id)
                    stmt.setInt(2, worldId)
                    stmt.setString(3, "member-${member.id}")
                    stmt.setInt(4, Role.MEMBER.level)
                }
            ).process(Unit)
        }
        val staleToken = member.copy(activeWorldId = worldId)
        assertEquals("/worlds/$worldId/roadmap", client.get("/") { addAuthCookie(this, staleToken) }.headers["Location"])

        runBlocking {
            DatabaseSteps.update<Unit>(
                SafeSQL.delete("DELETE FROM world_members WHERE user_id = ? AND world_id = ?"),
                parameterSetter = { stmt, _ -> stmt.setInt(1, member.id); stmt.setInt(2, worldId) }
            ).process(Unit)
            CacheManager.onMemberRemoved(member.id, worldId)
        }

        val landing = client.get("/") { addAuthCookie(this, staleToken) }
        assertEquals("/worlds", landing.headers["Location"])
    }

    private fun ApplicationTestBuilder.installLanding() {
        routing {
            install(AuthPlugin)
            get("/") { call.handleGetLanding() }
        }
    }

    private fun createWorld(): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = "GetLandingIT ${System.nanoTime()}",
                description = "test",
                version = MinecraftVersion.fromString("1.21.4")
            )
        )
        (result as Result.Success).value
    }
}
