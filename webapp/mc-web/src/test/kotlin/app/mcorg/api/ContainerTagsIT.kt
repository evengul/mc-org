package app.mcorg.api

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
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Container tags over the mod-facing API (MCO-530) — Shared Storage phase A.
 *
 * The cascade to `container_contents` and the measurement re-roll are asserted in MCO-532's tests;
 * neither table exists yet. What is provable here is the tag itself: its identity is the block
 * position, re-tagging moves it, and a world member is exactly who may address it.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ContainerTagsIT : WithUser() {

    private fun issueToken(user: TokenProfile = this.user): String = runBlocking {
        val token = ApiCrypto.newToken()
        CreateApiTokenStep.process(CreateApiTokenInput(user.id, ApiCrypto.sha256Hex(token), "test", null))
        token
    }

    private fun createWorld(name: String, owner: TokenProfile = user): Int = runBlocking {
        (CreateWorldStep(owner).process(
            CreateWorldInput(name, "test", MinecraftVersion.fromString("1.21.4"))
        ) as Result.Success).value
    }

    private fun createProject(worldId: Int, name: String): Int = runBlocking {
        (DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, state, location_x, location_y, location_z, location_dimension) " +
                    "VALUES (?, ?, '', 'BUILDING', 'RESOURCE_GATHERING', 'ACTIVE', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { st, _ -> st.setString(1, name); st.setInt(2, worldId) }
        ).process(Unit) as Result.Success).value
    }

    private fun addWorldMember(worldId: Int, member: TokenProfile, role: Role = Role.MEMBER) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO world_members (user_id, world_id, display_name, world_role) VALUES (?, ?, ?, ?)"
            ),
            parameterSetter = { st, _ ->
                st.setInt(1, member.id); st.setInt(2, worldId)
                st.setString(3, member.minecraftUsername); st.setInt(4, role.level)
            }
        ).process(Unit)
    }

    private suspend fun ApplicationTestBuilder.tag(
        worldId: Int,
        token: String?,
        body: String,
    ): HttpResponse = client.post("/api/v1/worlds/$worldId/containers") {
        if (token != null) header("Authorization", "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun ApplicationTestBuilder.listTags(worldId: Int, token: String?): List<ContainerTagDto> {
        val response = client.get("/api/v1/worlds/$worldId/containers") {
            if (token != null) header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return apiJson.decodeFromString(ContainerTagsResponse.serializer(), response.bodyAsText()).containers
    }

    private fun tagBody(projectId: Int, x: Int, y: Int, z: Int, kind: String = "chest", groupKey: String? = null) =
        buildString {
            append("""{"dimension":"minecraft:overworld","x":$x,"y":$y,"z":$z,""")
            append(""""project_id":$projectId,"kind":"$kind"""")
            if (groupKey != null) append(""","group_key":"$groupKey"""")
            append("}")
        }

    // ── POST + GET ─────────────────────────────────────────────────────────────

    @Test
    fun `tagging a container stores it and it reads back on the world`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val token = issueToken()
        val worldId = createWorld("Tags IT Create")
        val projectId = createProject(worldId, "Iron Farm")

        val response = tag(worldId, token, tagBody(projectId, 10, 64, -20, kind = "barrel"))
        assertEquals(HttpStatusCode.OK, response.status)
        val created = apiJson.decodeFromString(ContainerTagDto.serializer(), response.bodyAsText())
        assertEquals(projectId, created.projectId)
        assertEquals("barrel", created.kind)
        assertEquals(user.minecraftUsername, created.taggedBy)

        val listed = listTags(worldId, token).single()
        assertEquals(created.id, listed.id)
        assertEquals(10, listed.x)
        assertEquals(64, listed.y)
        assertEquals(-20, listed.z)
    }

    @Test
    fun `a freshly tagged container is unreadable and unseen until a sweep reads it`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val token = issueToken()
        val worldId = createWorld("Tags IT State")
        val projectId = createProject(worldId, "State Project")

        val response = tag(worldId, token, tagBody(projectId, 1, 2, 3))
        val created = apiJson.decodeFromString(ContainerTagDto.serializer(), response.bodyAsText())
        // The reporter owns both fields. Nothing has read this position yet, and the API says so
        // rather than optimistically claiming 'ok'.
        assertEquals("unreadable", created.state)
        assertNull(created.lastSeenAt)
        assertNotNull(created.taggedAt)
    }

    @Test
    fun `group_key defaults to the position when the client does not send one`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val token = issueToken()
        val worldId = createWorld("Tags IT Group Default")
        val projectId = createProject(worldId, "Group Default Project")

        val response = tag(worldId, token, tagBody(projectId, 5, 70, -8))
        val created = apiJson.decodeFromString(ContainerTagDto.serializer(), response.bodyAsText())
        assertEquals("5,70,-8", created.groupKey)
    }

    // ── Re-tagging is an upsert, not a second row ──────────────────────────────

    @Test
    fun `re-tagging a position moves it to the new project instead of adding a row`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val token = issueToken()
        val worldId = createWorld("Tags IT Retag")
        val first = createProject(worldId, "First Project")
        val second = createProject(worldId, "Second Project")

        val created = apiJson.decodeFromString(
            ContainerTagDto.serializer(),
            tag(worldId, token, tagBody(first, 0, 64, 0)).bodyAsText(),
        )
        val moved = apiJson.decodeFromString(
            ContainerTagDto.serializer(),
            tag(worldId, token, tagBody(second, 0, 64, 0, kind = "hopper")).bodyAsText(),
        )

        // Same row — the position is the identity, so the unique constraint turned this into an
        // UPDATE. A second row here would double-count the chest for the rest of the feature.
        assertEquals(created.id, moved.id)
        assertEquals(second, moved.projectId)
        assertEquals("hopper", moved.kind)
        assertEquals(1, listTags(worldId, token).size)
    }

    // ── Double chests ──────────────────────────────────────────────────────────

    @Test
    fun `tagging both halves of a double chest yields two rows sharing one group key`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val token = issueToken()
        val worldId = createWorld("Tags IT Double")
        val projectId = createProject(worldId, "Double Chest Project")

        // The client posts both halves with the lower half's position as the shared key.
        tag(worldId, token, tagBody(projectId, 4, 64, 4, groupKey = "4,64,4"))
        tag(worldId, token, tagBody(projectId, 5, 64, 4, groupKey = "4,64,4"))

        val tags = listTags(worldId, token)
        assertEquals(2, tags.size)
        assertEquals(setOf("4,64,4"), tags.map { it.groupKey }.toSet())
        assertEquals(setOf(4, 5), tags.map { it.x }.toSet())
    }

    // ── Validation ─────────────────────────────────────────────────────────────

    @Test
    fun `a furnace is not a taggable kind`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val token = issueToken()
        val worldId = createWorld("Tags IT Kind")
        val projectId = createProject(worldId, "Kind Project")

        // Tagging a furnace would count its fuel and its input, which is not what anyone means.
        val response = tag(worldId, token, tagBody(projectId, 1, 1, 1, kind = "furnace"))
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(listTags(worldId, token).isEmpty())
    }

    @Test
    fun `a project from another world cannot be tagged into this one`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val token = issueToken()
        val worldId = createWorld("Tags IT Own World")
        val otherWorldId = createWorld("Tags IT Other World")
        val foreignProject = createProject(otherWorldId, "Foreign Project")

        // Membership of both worlds is not the point — a tag names a position in ONE world, and its
        // project must live there too, or the measurement would roll up across worlds.
        val response = tag(worldId, token, tagBody(foreignProject, 2, 2, 2))
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(listTags(worldId, token).isEmpty())
    }

    @Test
    fun `an unknown project is not found`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val token = issueToken()
        val worldId = createWorld("Tags IT Unknown Project")

        val response = tag(worldId, token, tagBody(999_999, 3, 3, 3))
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // ── Membership ─────────────────────────────────────────────────────────────

    @Test
    fun `a non-member gets 403 on both reading and tagging`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("Tags IT Stranger")
        val projectId = createProject(worldId, "Stranger Project")
        val strangerToken = issueToken(createExtraUser())

        val read = client.get("/api/v1/worlds/$worldId/containers") {
            header("Authorization", "Bearer $strangerToken")
        }
        assertEquals(HttpStatusCode.Forbidden, read.status)
        assertEquals(HttpStatusCode.Forbidden, tag(worldId, strangerToken, tagBody(projectId, 1, 1, 1)).status)
    }

    @Test
    fun `any world member may tag, not just the owner`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("Tags IT Member")
        val projectId = createProject(worldId, "Member Project")
        val member = createExtraUser()
        addWorldMember(worldId, member, Role.MEMBER)

        val response = tag(worldId, issueToken(member), tagBody(projectId, 7, 7, 7))
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `container routes reject a missing token`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val worldId = createWorld("Tags IT NoAuth")

        val read = client.get("/api/v1/worlds/$worldId/containers")
        assertEquals(HttpStatusCode.Unauthorized, read.status)
        assertEquals(HttpStatusCode.Unauthorized, tag(worldId, null, tagBody(1, 1, 1, 1)).status)
    }

    // ── DELETE ─────────────────────────────────────────────────────────────────

    @Test
    fun `untagging removes the tag, and untagging again is not found`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val token = issueToken()
        val worldId = createWorld("Tags IT Delete")
        val projectId = createProject(worldId, "Delete Project")
        val created = apiJson.decodeFromString(
            ContainerTagDto.serializer(),
            tag(worldId, token, tagBody(projectId, 9, 9, 9)).bodyAsText(),
        )

        val first = client.delete("/api/v1/worlds/$worldId/containers/${created.id}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, first.status)
        assertTrue(listTags(worldId, token).isEmpty())

        val second = client.delete("/api/v1/worlds/$worldId/containers/${created.id}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, second.status)
    }

    @Test
    fun `a tag cannot be deleted through a world it does not belong to`() = testApplication {
        routing { install(AuthPlugin); apiV1Routes() }
        val token = issueToken()
        val worldId = createWorld("Tags IT Delete Own")
        val otherWorldId = createWorld("Tags IT Delete Other")
        val projectId = createProject(worldId, "Delete Scope Project")
        val created = apiJson.decodeFromString(
            ContainerTagDto.serializer(),
            tag(worldId, token, tagBody(projectId, 8, 8, 8)).bodyAsText(),
        )

        // Membership of the other world is real; the tag still is not addressable through it.
        val response = client.delete("/api/v1/worlds/$otherWorldId/containers/${created.id}") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(1, listTags(worldId, token).size)
    }
}
