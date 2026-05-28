package app.mcorg.presentation.handler.world

import app.mcorg.config.CacheManager
import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.user.Role
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.WorldAdminPlugin
import app.mcorg.presentation.plugins.WorldOwnerPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.method
import io.ktor.server.routing.route
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class WorldOwnerPluginIT : WithUser() {

    private var worldId: Int = 0

    @BeforeAll
    fun setup() {
        val result = runBlocking {
            CreateWorldStep(user).process(
                CreateWorldInput(
                    name = "WorldOwnerPluginIT World",
                    description = "test",
                    version = MinecraftVersion.fromString("1.21.4")
                )
            )
        }
        worldId = (result as Result.Success).value
    }

    @Test
    fun `OWNER can DELETE settings - returns 200`() = testApplication {
        installSettingsRoutes()
        val client = createClient { followRedirects = false }

        val response = client.delete("/worlds/$worldId/settings") {
            addAuthCookie(this, user)
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `ADMIN cannot DELETE settings - returns 403 from WorldOwnerPlugin`() = testApplication {
        installSettingsRoutes()
        val client = createClient { followRedirects = false }

        val adminWorldId = createSeparateWorld()
        val adminUser = createExtraUser()
        addWorldMember(adminUser.id, adminWorldId, Role.ADMIN, "admin-${adminUser.id}")

        val response = client.delete("/worlds/$adminWorldId/settings") {
            addAuthCookie(this, adminUser)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `MEMBER cannot DELETE settings - returns 403 from WorldAdminPlugin`() = testApplication {
        installSettingsRoutes()
        val client = createClient { followRedirects = false }

        val memberWorldId = createSeparateWorld()
        val memberUser = createExtraUser()
        addWorldMember(memberUser.id, memberWorldId, Role.MEMBER, "member-${memberUser.id}")

        val response = client.delete("/worlds/$memberWorldId/settings") {
            addAuthCookie(this, memberUser)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `Non-member cannot DELETE settings - returns 403`() = testApplication {
        installSettingsRoutes()
        val client = createClient { followRedirects = false }

        val nonMember = createExtraUser()

        val response = client.delete("/worlds/$worldId/settings") {
            addAuthCookie(this, nonMember)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `ADMIN can still GET settings - WorldOwnerPlugin scoped to DELETE only`() = testApplication {
        installSettingsRoutes()
        val client = createClient { followRedirects = false }

        val adminWorldId = createSeparateWorld()
        val adminUser = createExtraUser()
        addWorldMember(adminUser.id, adminWorldId, Role.ADMIN, "admin-${adminUser.id}")

        val response = client.get("/worlds/$adminWorldId/settings") {
            addAuthCookie(this, adminUser)
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.installSettingsRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                route("/settings") {
                    install(WorldAdminPlugin)
                    get { call.respond(HttpStatusCode.OK, "settings") }
                    method(HttpMethod.Delete) {
                        install(WorldOwnerPlugin)
                        handle { call.respond(HttpStatusCode.OK, "deleted") }
                    }
                }
            }
        }
    }

    private fun createSeparateWorld(): Int {
        val result = runBlocking {
            CreateWorldStep(user).process(
                CreateWorldInput(
                    name = "WorldOwnerPluginIT Aux ${System.nanoTime()}",
                    description = "aux",
                    version = MinecraftVersion.fromString("1.21.4")
                )
            )
        }
        return (result as Result.Success).value
    }

    private fun addWorldMember(userId: Int, worldId: Int, role: Role, displayName: String) {
        runBlocking {
            DatabaseSteps.update<Unit>(
                SafeSQL.insert("INSERT INTO world_members (user_id, world_id, display_name, world_role) VALUES (?, ?, ?, ?)"),
                parameterSetter = { stmt, _ ->
                    stmt.setInt(1, userId)
                    stmt.setInt(2, worldId)
                    stmt.setString(3, displayName)
                    stmt.setInt(4, role.level)
                }
            ).process(Unit)
            CacheManager.onMemberAdded(userId, worldId)
            // Invalidate any pre-cached "false" role lookups so newly-added member reads fresh
            CacheManager.worldMemberRole.asMap().keys
                .filter { it.startsWith("$userId:$worldId:") }
                .forEach { CacheManager.worldMemberRole.invalidate(it) }
        }
    }
}
