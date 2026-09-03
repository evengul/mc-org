package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.resources.handleBulkAnswerFoldedQuestions
import app.mcorg.pipeline.resources.handleGetBulkAnswerControl
import app.mcorg.pipeline.resources.handleGetNodePicker
import app.mcorg.pipeline.resources.handleResolveTagMember
import app.mcorg.pipeline.resources.handleUndoBulkAnswer
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.ProjectParamPlugin
import app.mcorg.presentation.plugins.UpdateActiveWorldPlugin
import app.mcorg.presentation.plugins.WorldParamPlugin
import app.mcorg.presentation.plugins.WorldParticipantPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.delete
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MCO-507 — "answer the remaining N with the recommended pick", end to end.
 *
 * The Testcontainers DB carries no ingested Minecraft data (see [PlanChainIT]'s note), and this
 * feature is meaningless without a graph — there is nothing to rank. So this class seeds a small
 * fake version into the ingestion tables, the same way [SwapResourceGatheringVariantIT] does, and
 * shapes it to produce exactly the situation the feature exists for: one big question that leads,
 * and a tail of three small ones that get folded away.
 *
 * The four choice sets are the real ones MCO-409 deliberately left as questions — different
 * stones, coal vs charcoal, sand vs red sand, soul sand vs soul soil — so the planner leaves all
 * four open rather than answering them under the canonical-form rule.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class PlanAttentionBulkIT : WithUser() {

    /** Unique per test class so the in-memory graph cache never collides with another class. */
    private val fakeVersion = "507.0.0"

    private var worldId: Int = 0

    /** The three questions the fold hides, and therefore the exact set the action may answer. */
    private val foldedTags = listOf("#test:coal_choice", "#test:sand_choice", "#test:soul_choice")

    /** The question that leads, and therefore the one the action must never touch. */
    private val leadTag = "#test:stone_choice"

    @BeforeAll
    fun setup() {
        runBlocking { seedFakeGraph(fakeVersion) }
        worldId = createWorld(fakeVersion)
    }

    // -------------------------------------------------------------------------
    // Auth — all three verbs sit behind the world-participant gate
    // -------------------------------------------------------------------------

    @Test
    fun `GET control without auth returns redirect`() = testApplication {
        val pid = questionProject()
        setupRoutes()

        val unauth = createClient { followRedirects = false }
        val response = unauth.get("/worlds/$worldId/projects/$pid/plan/attention/bulk")

        assertEquals(HttpStatusCode.Found, response.status)
    }

    @Test
    fun `POST without auth returns redirect and writes nothing`() = testApplication {
        val pid = questionProject()
        setupRoutes()

        val unauth = createClient { followRedirects = false }
        val response = unauth.post("/worlds/$worldId/projects/$pid/plan/attention/bulk")

        assertEquals(HttpStatusCode.Found, response.status)
        assertTrue(liveOverrides(pid).isEmpty(), "an unauthenticated POST must not answer anything")
    }

    @Test
    fun `DELETE without auth returns redirect`() = testApplication {
        val pid = questionProject()
        setupRoutes()

        val unauth = createClient { followRedirects = false }
        val response = unauth.delete("/worlds/$worldId/projects/$pid/plan/attention/bulk?ids=1")

        assertEquals(HttpStatusCode.Found, response.status)
    }

    // -------------------------------------------------------------------------
    // GET — the offer, and what it says it will do
    // -------------------------------------------------------------------------

    @Test
    fun `the control names every folded question and the pick it would make`() = testApplication {
        val pid = questionProject()
        setupRoutes()

        val body = client.get("/worlds/$worldId/projects/$pid/plan/attention/bulk") {
            addAuthCookie(this)
        }.bodyAsText()

        assertContains(body, "Answer the remaining 3 with the recommended pick")
        assertContains(body, "Answer these 3")
        // One line per folded question, each naming the answer it will get.
        assertEquals(3, Regex("plan-attention__pick-answer").findAll(body).count())
        listOf("Charcoal or Coal", "Red Sand or Sand", "Soul Sand or Soul Soil").forEach {
            assertContains(body, it)
        }
    }

    /**
     * The line the whole design rests on: the lead question is the one worth reading, and an
     * offer to answer it for you is exactly the auto-resolve this feature was chosen over.
     */
    @Test
    fun `the control never offers to answer the leading question`() = testApplication {
        val pid = questionProject()
        setupRoutes()

        val body = client.get("/worlds/$worldId/projects/$pid/plan/attention/bulk") {
            addAuthCookie(this)
        }.bodyAsText()

        assertFalse(
            body.contains("Blackstone, Cobbled Deepslate or Cobblestone"),
            "the leading question must not appear among the picks",
        )
    }

    @Test
    fun `there is no control when nothing is folded`() = testApplication {
        val pid = createProject()  // no resources at all, so no plan and no questions
        setupRoutes()

        val response = client.get("/worlds/$worldId/projects/$pid/plan/attention/bulk") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("", response.bodyAsText())
    }

    /**
     * The recommendation must be the *same* notion of best the picker shows, or the button and
     * the picker disagree in front of the user. Asserted against the picker's own output rather
     * than against a hardcoded member, so it stays true if the scorer changes.
     */
    @Test
    fun `each pick is the option the picker marks best score`() = testApplication {
        val pid = questionProject()
        setupRoutes()

        val control = client.get("/worlds/$worldId/projects/$pid/plan/attention/bulk") {
            addAuthCookie(this)
        }.bodyAsText()

        foldedTags.forEach { tag ->
            val encoded = tag.replace("#", "%23")
            val picker = client.get(
                "/worlds/$worldId/projects/$pid/plan/chain/$encoded/sources?node=$encoded&origin=list"
            ) { addAuthCookie(this) }.bodyAsText()

            val best = Regex("""picker-opt__name">([^<]+)</span><span class="picker-opt__hint">[^<]*best score""")
                .find(picker)
                ?.groupValues
                ?.get(1)
            assertTrue(best != null, "picker for $tag should mark a best option")
            assertContains(control, """plan-attention__pick-answer">$best<""")
        }
    }

    // -------------------------------------------------------------------------
    // POST — the answers themselves
    // -------------------------------------------------------------------------

    @Test
    fun `answering writes an ordinary override per folded question, carrying the planner pick`() = testApplication {
        val pid = questionProject()
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/$pid/plan/attention/bulk") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val rows = allRows(pid)
        assertEquals(3, rows.size)
        assertEquals(foldedTags.toSet(), rows.map { it.itemId }.toSet())
        rows.forEach {
            assertTrue(it.tagMember != null, "${it.itemId} must be an ordinary tag-member answer")
            assertEquals(it.tagMember, it.plannerPick, "the action agrees with the recommendation by construction")
            assertFalse(it.superseded, "nothing was superseded — these questions were open")
        }
        // The lead question is untouched.
        assertFalse(rows.any { it.itemId == leadTag })
    }

    @Test
    fun `answering re-renders the plan and offers the undo`() = testApplication {
        val pid = questionProject()
        setupRoutes()

        val body = client.post("/worlds/$worldId/projects/$pid/plan/attention/bulk") {
            addAuthCookie(this)
        }.bodyAsText()

        assertContains(body, "id=\"project-content\"")
        assertContains(body, "Revert those 3")
        assertContains(body, "hx-swap-oob=\"beforeend:#alert-container\"")
        // The answered questions have left "Needs attention".
        assertFalse(body.contains("Charcoal or Coal"), "an answered question is no longer a question")
    }

    @Test
    fun `answering when there is nothing folded is a validation failure`() = testApplication {
        val pid = createProject()
        setupRoutes()

        val response = client.post("/worlds/$worldId/projects/$pid/plan/attention/bulk") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertContains(response.bodyAsText(), "no folded questions left to answer")
        assertTrue(liveOverrides(pid).isEmpty())
    }

    // -------------------------------------------------------------------------
    // DELETE — undo, and only of what the action created
    // -------------------------------------------------------------------------

    @Test
    fun `undo removes exactly the rows the action created and leaves a hand-made answer alone`() = testApplication {
        val pid = questionProject()
        setupRoutes()

        val posted = client.post("/worlds/$worldId/projects/$pid/plan/attention/bulk") {
            addAuthCookie(this)
        }.bodyAsText()

        val ids = Regex("""plan/attention/bulk\?ids=([0-9,]+)""").find(posted)!!.groupValues[1]
        assertEquals(3, ids.split(",").size)

        // A question the user answers for themselves. Answering the leading one is only possible
        // after the tail is gone — the fold needs at least three to hide — which is also the order
        // a real session takes: clear the trivia, then make the decision that matters.
        client.submitForm(
            url = "/worlds/$worldId/projects/$pid/plan/chain/%23test:stone_choice/tag",
            formParameters = Parameters.build {
                append("node", leadTag)
                append("memberItemId", "minecraft:blackstone")
                append("origin", "list")
            }
        ) { addAuthCookie(this) }

        val response = client.delete("/worlds/$worldId/projects/$pid/plan/attention/bulk?ids=$ids") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "id=\"project-content\"")
        assertContains(response.bodyAsText(), "hx-swap-oob=\"delete:li#plan-attention-bulk-undo\"")

        val live = liveOverrides(pid)
        assertEquals(
            mapOf(leadTag to "minecraft:blackstone"),
            live,
            "only the user's own answer survives the undo",
        )
    }

    @Test
    fun `undo without ids is a validation failure`() = testApplication {
        val pid = questionProject()
        setupRoutes()

        val response = client.delete("/worlds/$worldId/projects/$pid/plan/attention/bulk") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `undo with unreadable ids is a validation failure`() = testApplication {
        val pid = questionProject()
        setupRoutes()

        val response = client.delete("/worlds/$worldId/projects/$pid/plan/attention/bulk?ids=abc,") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // -------------------------------------------------------------------------
    // Routes
    // -------------------------------------------------------------------------

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/worlds/{worldId}") {
                install(WorldParamPlugin)
                install(WorldParticipantPlugin)
                install(UpdateActiveWorldPlugin)
                route("/projects/{projectId}") {
                    install(ProjectParamPlugin)
                    route("/plan") {
                        route("/chain/{itemId}") {
                            get("/sources") { call.handleGetNodePicker() }
                            post("/tag") { call.handleResolveTagMember() }
                        }
                        route("/attention/bulk") {
                            get { call.handleGetBulkAnswerControl() }
                            post { call.handleBulkAnswerFoldedQuestions() }
                            delete { call.handleUndoBulkAnswer() }
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /**
     * A project whose plan has one dominant question and a tail of three small ones — the shape
     * "Needs attention" folds, and therefore the shape this feature answers.
     */
    private fun questionProject(): Int {
        val pid = createProject()
        createResourceGathering(pid, "minecraft:stone_bricks", "Stone Bricks", 100)
        createResourceGathering(pid, "minecraft:torch", "Torch", 1)
        createResourceGathering(pid, "minecraft:glass", "Glass", 1)
        createResourceGathering(pid, "minecraft:soul_torch", "Soul Torch", 1)
        return pid
    }

    private data class Row(
        val itemId: String,
        val tagMember: String?,
        val plannerPick: String?,
        val superseded: Boolean,
    )

    private fun allRows(projectId: Int): List<Row> = runBlocking {
        val result = DatabaseSteps.query<Int, List<Row>>(
            sql = SafeSQL.select(
                "SELECT item_id, tag_member, planner_pick, superseded_at " +
                    "FROM resource_gathering_plan_override WHERE project_id = ? ORDER BY id"
            ),
            parameterSetter = { stmt, pid -> stmt.setInt(1, pid) },
            resultMapper = { rs ->
                val rows = mutableListOf<Row>()
                while (rs.next()) {
                    rows.add(
                        Row(
                            itemId = rs.getString("item_id"),
                            tagMember = rs.getString("tag_member"),
                            plannerPick = rs.getString("planner_pick"),
                            superseded = rs.getTimestamp("superseded_at") != null,
                        )
                    )
                }
                rows
            }
        ).process(projectId)
        (result as Result.Success).value
    }

    private fun liveOverrides(projectId: Int): Map<String, String?> =
        allRows(projectId).filterNot { it.superseded }.associate { it.itemId to it.tagMember }

    private fun createWorld(version: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = "AttentionBulk IT World",
                description = "test",
                version = MinecraftVersion.fromString(version)
            )
        )
        (result as Result.Success).value
    }

    private fun createProject(): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, location_x, location_y, location_z, location_dimension) " +
                    "VALUES ('AttentionBulk IT Project', ?, '', 'BUILDING', 'PLANNING', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ -> stmt.setInt(1, worldId) }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun createResourceGathering(projectId: Int, itemId: String, name: String, required: Int): Int =
        runBlocking {
            val result = DatabaseSteps.update<Unit>(
                sql = SafeSQL.insert(
                    "INSERT INTO resource_gathering (project_id, item_id, name, required) VALUES (?, ?, ?, ?) RETURNING id"
                ),
                parameterSetter = { stmt, _ ->
                    stmt.setInt(1, projectId)
                    stmt.setString(2, itemId)
                    stmt.setString(3, name)
                    stmt.setInt(4, required)
                }
            ).process(Unit)
            (result as Result.Success).value
        }

    // ---- fake ingested graph -------------------------------------------------------------

    private suspend fun item(version: String, itemId: String, name: String) {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO minecraft_items (version, item_id, item_name) VALUES (?, ?, ?) " +
                    "ON CONFLICT (version, item_id) DO NOTHING"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, version)
                stmt.setString(2, itemId)
                stmt.setString(3, name)
            }
        ).process(Unit)
    }

    private suspend fun tag(version: String, tag: String, name: String) {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO minecraft_tag (version, tag, name) VALUES (?, ?, ?) ON CONFLICT (version, tag) DO NOTHING"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, version)
                stmt.setString(2, tag)
                stmt.setString(3, name)
            }
        ).process(Unit)
    }

    private suspend fun tagItem(version: String, tag: String, itemId: String) {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO minecraft_tag_item (version, tag, item) VALUES (?, ?, ?) " +
                    "ON CONFLICT (version, tag, item) DO NOTHING"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, version)
                stmt.setString(2, tag)
                stmt.setString(3, itemId)
            }
        ).process(Unit)
    }

    private suspend fun source(version: String, sourceType: String, filename: String): Int {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO resource_source (version, source_type, created_from_filename) VALUES (?, ?, ?) RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, version)
                stmt.setString(2, sourceType)
                stmt.setString(3, filename)
            }
        ).process(Unit)
        return (result as Result.Success).value
    }

    private suspend fun consumedTag(version: String, sourceId: Int, tag: String, count: Int) {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO resource_source_consumed_tag (version, resource_source_id, tag, count) VALUES (?, ?, ?, ?)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, version)
                stmt.setInt(2, sourceId)
                stmt.setString(3, tag)
                stmt.setInt(4, count)
            }
        ).process(Unit)
    }

    private suspend fun producedItem(version: String, sourceId: Int, itemId: String, count: Int) {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO resource_source_produced_item (version, resource_source_id, item, count) VALUES (?, ?, ?, ?)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, version)
                stmt.setInt(2, sourceId)
                stmt.setString(3, itemId)
                stmt.setInt(4, count)
            }
        ).process(Unit)
    }

    /** A raw-gather terminal: a block source producing one of [itemId] and consuming nothing. */
    private suspend fun rawBlock(version: String, itemId: String, name: String) {
        item(version, itemId, name)
        val src = source(version, "minecraft:block", "blocks/${itemId.substringAfter(':')}.json")
        producedItem(version, src, itemId, 1)
    }

    /** A recipe that turns [count] of [tag] into one [outputId] — which is what opens the tag. */
    private suspend fun craftFromTag(version: String, outputId: String, outputName: String, tag: String, count: Int) {
        item(version, outputId, outputName)
        val src = source(version, "minecraft:crafting_shapeless", "${outputId.substringAfter(':')}.json")
        consumedTag(version, src, tag, count)
        producedItem(version, src, outputId, 1)
    }

    private suspend fun choice(version: String, tagId: String, name: String, members: List<Pair<String, String>>) {
        tag(version, tagId, name)
        members.forEach { (id, itemName) ->
            rawBlock(version, id, itemName)
            tagItem(version, tagId, id)
        }
    }

    private suspend fun seedFakeGraph(version: String) {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert("INSERT INTO minecraft_version (version) VALUES (?) ON CONFLICT (version) DO NOTHING"),
            parameterSetter = { stmt, _ -> stmt.setString(1, version) }
        ).process(Unit)

        // The lead: 100 stone bricks at 4 stone per brick is 400 items, ~99% of everything the
        // questions decide, so the fold shows this one and hides the rest.
        choice(
            version, "#test:stone_choice", "Blackstone, Cobbled Deepslate or Cobblestone",
            listOf(
                "minecraft:cobblestone" to "Cobblestone",
                "minecraft:cobbled_deepslate" to "Cobbled Deepslate",
                "minecraft:blackstone" to "Blackstone",
            ),
        )
        craftFromTag(version, "minecraft:stone_bricks", "Stone Bricks", "#test:stone_choice", 4)

        // The tail: one item each. Small enough to be nobody's decision, numerous enough to be
        // the last thing between an import and a finished plan.
        choice(
            version, "#test:coal_choice", "Charcoal or Coal",
            listOf("minecraft:coal" to "Coal", "minecraft:charcoal" to "Charcoal"),
        )
        craftFromTag(version, "minecraft:torch", "Torch", "#test:coal_choice", 1)

        choice(
            version, "#test:sand_choice", "Red Sand or Sand",
            listOf("minecraft:sand" to "Sand", "minecraft:red_sand" to "Red Sand"),
        )
        craftFromTag(version, "minecraft:glass", "Glass", "#test:sand_choice", 1)

        choice(
            version, "#test:soul_choice", "Soul Sand or Soul Soil",
            listOf("minecraft:soul_sand" to "Soul Sand", "minecraft:soul_soil" to "Soul Soil"),
        )
        craftFromTag(version, "minecraft:soul_torch", "Soul Torch", "#test:soul_choice", 1)
    }
}
