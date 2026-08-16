package app.mcorg.presentation.handler.world

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.world.World
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.pipeline.world.settings.general.handleUpdateFarmScaleThreshold
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.WorldAdminPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.plugins.WorldParticipantPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.patch
import io.ktor.server.routing.route
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

/**
 * `PATCH /worlds/{worldId}/settings/farm-scale-threshold` (MCO-401) — the world-level line
 * above which raw demand is marked as worth a farm.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class UpdateFarmScaleThresholdIT : WithUser() {

    @Test
    fun `a new world starts at one shulker box`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Threshold Default World")

        assertEquals(World.DEFAULT_FARM_SCALE_THRESHOLD, storedThreshold(worldId))
        assertEquals(1728, World.DEFAULT_FARM_SCALE_THRESHOLD)
    }

    @Test
    fun `an admin can raise the threshold`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Threshold Update World")

        val response = client.patch("/worlds/$worldId/settings/farm-scale-threshold") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("farmScaleThreshold=50000")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(50_000, storedThreshold(worldId))
    }

    @Test
    fun `zero is rejected rather than silently disabling every marker`() = testApplication {
        // A threshold of 0 marks every raw material, which conveys exactly as much as marking
        // none. Rejecting it says why, instead of leaving a plan where the badge is meaningless.
        setupRoutes()
        val worldId = createWorld("Threshold Zero World")

        val response = client.patch("/worlds/$worldId/settings/farm-scale-threshold") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("farmScaleThreshold=0")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(World.DEFAULT_FARM_SCALE_THRESHOLD, storedThreshold(worldId))
    }

    @Test
    fun `a non-numeric threshold is rejected`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Threshold Text World")

        val response = client.patch("/worlds/$worldId/settings/farm-scale-threshold") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("farmScaleThreshold=a+shulker+box")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(World.DEFAULT_FARM_SCALE_THRESHOLD, storedThreshold(worldId))
    }

    @Test
    fun `a missing threshold is rejected`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Threshold Missing World")

        val response = client.patch("/worlds/$worldId/settings/farm-scale-threshold") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(World.DEFAULT_FARM_SCALE_THRESHOLD, storedThreshold(worldId))
    }

    @Test
    fun `an unauthenticated request is sent to sign-in rather than changing anything`() = testApplication {
        setupRoutes()
        val worldId = createWorld("Threshold Auth World")

        val response = client.patch("/worlds/$worldId/settings/farm-scale-threshold") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("farmScaleThreshold=50000")
        }

        // AuthPlugin redirects rather than 401s — this is a browser surface, not an API.
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals(World.DEFAULT_FARM_SCALE_THRESHOLD, storedThreshold(worldId))
    }

    // ---- fixtures ------------------------------------------------------------------

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                route("/settings") {
                    install(WorldAdminPlugin)
                    patch("/farm-scale-threshold") { call.handleUpdateFarmScaleThreshold() }
                }
            }
        }
    }

    private fun createWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(name = name, description = "test", version = MinecraftVersion.fromString("1.20.1"))
        )
        (result as Result.Success).value
    }

    private fun storedThreshold(worldId: Int): Int = runBlocking {
        val result = DatabaseSteps.query<Int, Int?>(
            SafeSQL.select("SELECT farm_scale_threshold FROM world WHERE id = ?"),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) },
            resultMapper = { rs -> if (rs.next()) rs.getInt("farm_scale_threshold") else null }
        ).process(worldId)
        (result as Result.Success).value!!
    }
}
