package app.mcorg.presentation.handler.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.domain.model.project.ProjectState
import app.mcorg.domain.model.user.Role
import app.mcorg.config.CacheManager
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.ReviewedMaterial
import app.mcorg.pipeline.project.ReviewedMaterialsCodec
import app.mcorg.pipeline.project.handleImportIdea
import app.mcorg.pipeline.project.handleReviewIdeaImport
import app.mcorg.pipeline.resources.GetWorldFarmSuppliesStep
import app.mcorg.pipeline.resources.WorldFarmSuppliesInput
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.presentation.plugins.AuthPlugin
import app.mcorg.presentation.plugins.IdeaParamPlugin
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Idea import through the review screen (MCO-306): the second import door gets the same
 * exclude-before-you-commit treatment as the schematic upload.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ImportIdeaReviewIT : WithUser() {

    private val version = MinecraftVersion.fromString("1.21.4")

    private var worldId: Int = 0
    private var ideaId: Int = 0
    private var airyIdeaId: Int = 0
    private var expensiveIdeaId: Int = 0
    private var wideIdeaId: Int = 0
    private var multiModeIdeaId: Int = 0
    private var unknownProductionIdeaId: Int = 0
    private var placedIdeaId: Int = 0
    private var cobbleIdeaId: Int = 0

    @BeforeAll
    fun setup() {
        worldId = createWorld("Idea Import IT World")
        seedItem("minecraft:iron_ingot", "Iron Ingot")
        seedItem("minecraft:oak_planks", "Oak Planks")
        seedItem("minecraft:redstone", "Redstone Dust")
        seedItem("minecraft:air", "Air (Block)")
        seedItem("minecraft:water", "Water")
        seedItem("minecraft:water_bucket", "Water Bucket")
        ideaId = createIdea("Iron Farm Idea")
        addRequirement(ideaId, "minecraft:iron_ingot", 64)
        addRequirement(ideaId, "minecraft:oak_planks", 32)
        addRequirement(ideaId, "minecraft:redstone", 8)

        // The MCO-305 case, from a real idea: a schematic-derived idea whose largest row was
        // nine million air blocks — 90% of the build's material total.
        airyIdeaId = createIdea("Ghast Farm Roof")
        addRequirement(airyIdeaId, "minecraft:air", 9_389_854)
        addRequirement(airyIdeaId, "minecraft:water", 12)
        addRequirement(airyIdeaId, "minecraft:oak_planks", 64)

        // MCO-308, from the report: an idea captured from a schematic records *placed* ids.
        // Both are real catalog entries, which is why they used to survive validation and
        // land on the gathering list as rows no source produces.
        seedItem("minecraft:redstone_wire", "Redstone Wire (Block)")
        seedItem("minecraft:birch_sign", "Birch Sign")
        seedItem("minecraft:birch_wall_sign", "Birch Wall Sign (Block)")
        placedIdeaId = createIdea("Ghast Farm Wiring")
        addRequirement(placedIdeaId, "minecraft:redstone_wire", 592)
        addRequirement(placedIdeaId, "minecraft:birch_wall_sign", 1)
        addRequirement(placedIdeaId, "minecraft:redstone", 8)
        addRequirement(placedIdeaId, "minecraft:oak_planks", 64)

        // MCO-397: a curated "slow to gather" row, which must stay off the strip.
        seedItem("minecraft:elytra", "Elytra")
        expensiveIdeaId = createIdea("Elytra Wall")
        addRequirement(expensiveIdeaId, "minecraft:elytra", 3)
        addRequirement(expensiveIdeaId, "minecraft:oak_planks", 20)

        // MCO-315: an idea whose list is far wider than the ~466 rows that used to fit in a
        // request body. The idea door shares the review screen, so it shared the data loss.
        wideIdeaId = createIdea("Very Wide Idea")
        seedItems((1..BULK_ROWS).map { "minecraft:bulk_item_$it" to "Bulk Item $it" })
        addRequirements(wideIdeaId, (1..BULK_ROWS).map { "minecraft:bulk_item_$it" to it })

        // MCO-411: what the idea produces has to survive the import, or the farm the user just
        // imported supplies nothing and the roadmap draws no edge from it (MCO-287's model).
        addProductionMode(ideaId, "Default", listOf("minecraft:iron_ingot" to 620))

        // Two modes, no choice recorded anywhere yet: the import takes the highest-yield one
        // (ratesForImport). An unmeasured rate arrives as project_productions' own 0.
        seedItem("minecraft:bone", "Bone")
        seedItem("minecraft:blaze_rod", "Blaze Rod")
        seedItem("minecraft:blaze_powder", "Blaze Powder")
        multiModeIdeaId = createIdea("Fortress Farm")
        addRequirement(multiModeIdeaId, "minecraft:oak_planks", 10)
        addProductionMode(multiModeIdeaId, "Skeletons only", listOf("minecraft:bone" to 700))
        addProductionMode(
            multiModeIdeaId,
            "Everything on",
            listOf("minecraft:bone" to 500, "minecraft:blaze_rod" to 400, "minecraft:blaze_powder" to null),
        )

        // MCO-456: an idea claiming to produce something this world's version has no item for.
        // Never seeded into minecraft_items by any IT class — the container is shared, so an id
        // used here has to stay unseeded suite-wide to keep meaning "this version has no such
        // item".
        unknownProductionIdeaId = createIdea("Shrieker Farm")
        addRequirement(unknownProductionIdeaId, "minecraft:oak_planks", 12)
        addProductionMode(
            unknownProductionIdeaId,
            "Default",
            listOf("minecraft:iron_ingot" to 90, "minecraft:sculk_shrieker" to 30),
        )

        // MCO-463, from MCO-439 finding 1: a cobblestone farm published as single/4 modules, both
        // chosen when you *build* it and costing roughly 4x apart. Before this the bank could hold
        // one material list for the pair, so one of the two was always wrong.
        //
        // It *produces* tuff rather than the cobblestone it is built from, which is a test-harness
        // constraint and not a fact about the farm. The Testcontainers database is shared across IT
        // classes, so an idea seeded here is in the idea bank every other suite queries — and
        // FarmSuggestionIT asserts that a cobblestone plan has *no* design answering it once its
        // own design is excluded. A second cobblestone producer here made that suite fail
        // depending on class order, which is exactly the suite-wide collision this file already
        // warns about for minecraft:sculk_shrieker. Requirements are unconstrained (nothing matches
        // demand against them), so the 4x cobblestone relationship that makes this a build-time
        // axis is kept where it matters.
        seedItem("minecraft:cobblestone", "Cobblestone")
        seedItem("minecraft:hopper", "Hopper")
        seedItem("minecraft:tuff", "Tuff")
        cobbleIdeaId = createIdea("Cobblestone Farm")
        addBuildTimeMode(
            cobbleIdeaId,
            "1 module",
            rates = listOf("minecraft:tuff" to 231_000),
            requirements = listOf("minecraft:cobblestone" to 400),
        )
        addBuildTimeMode(
            cobbleIdeaId,
            "4 modules",
            rates = listOf("minecraft:tuff" to 924_000),
            requirements = listOf("minecraft:cobblestone" to 1_600, "minecraft:hopper" to 256),
        )
    }

    // ---- review ------------------------------------------------------------------

    @Test
    fun `review lists the idea's requirements and creates nothing`() = testApplication {
        setupRoutes()
        val before = countProjects()

        val response = client.get("/ideas/$ideaId/import/review?worldId=$worldId") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "Review this import")
        assertContains(body, "Iron Farm Idea")
        // One field for the whole list since MCO-315, not one parameter per row.
        assertContains(body, """name="materials" value="v1;3;""")
        assertContains(body, "minecraft:iron_ingot=64")
        assertContains(body, "minecraft:oak_planks=32")
        assertFalse(body.contains("qty["), "the per-row parameters are gone")
        // The form has to carry the world through — the action URL has no room for it.
        assertContains(body, "name=\"worldId\"")
        assertEquals(before, countProjects(), "review must not create anything")
    }

    @Test
    fun `review without a world is refused`() = testApplication {
        setupRoutes()

        val response = client.get("/ideas/$ideaId/import/review") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `a non-member cannot review an import into someone else's world`() = testApplication {
        setupRoutes()
        val outsider = createExtraUser("idea-review-outsider")

        val response = client.get("/ideas/$ideaId/import/review?worldId=$worldId") {
            addAuthCookie(this, outsider)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // ---- warn early (MCO-305) ------------------------------------------------------

    @Test
    fun `air never reaches the review screen`() = testApplication {
        setupRoutes()

        val response = client.get("/ideas/$airyIdeaId/import/review?worldId=$worldId") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertFalse(body.contains("minecraft:air"), "air is filtered, not offered as a row")
        assertFalse(body.contains("9389854"), "and its quantity goes with it")
        assertContains(body, "minecraft:oak_planks=64", message = "the real materials still arrive")
    }

    @Test
    fun `a fluid arrives as the bucket you carry, with the cell count as context`() = testApplication {
        // MCO-396. This used to assert the opposite — water stayed as a row and was flagged
        // "Not really materials". That reading is what put `Blocked: Water — no feasible
        // source found` in the plan, because nothing produces water: it is not an item.
        setupRoutes()

        val response = client.get("/ideas/$airyIdeaId/import/review?worldId=$worldId") { addAuthCookie(this) }

        val body = response.bodyAsText()
        assertContains(body, "minecraft:water_bucket=1", message = "one bucket, however many cells")
        assertFalse(body.contains("minecraft:water="), "the fluid id itself must not be a row")
        assertContains(body, "placed 12×", message = "the schematic's own number stays visible")
        assertFalse(body.contains("Not really materials"), "the warning kind is gone entirely")
    }

    @Test
    fun `placed block ids reach the review screen as the item you gather`() = testApplication {
        // MCO-308. The idea door used to take recorded ids verbatim, so `Redstone Wire
        // (Block)` x592 and `Birch Wall Sign (Block)` x1 arrived as rows — and MCO-305's strip
        // correctly, uselessly, called them creative-only. Nothing produces redstone_wire; what
        // the user needs is 592 redstone, which is an ordinary ask.
        setupRoutes()

        val response = client.get("/ideas/$placedIdeaId/import/review?worldId=$worldId") {
            addAuthCookie(this)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        // 592 placed + 8 already in item form: resolution sums onto the item, never overwrites.
        assertContains(body, "minecraft:redstone=600", message = "placed dust and stored dust are one row")
        assertContains(body, "minecraft:birch_sign=1", message = "a wall sign is a sign")
        assertFalse(body.contains("minecraft:redstone_wire"), "the placed id must not survive")
        assertFalse(body.contains("minecraft:birch_wall_sign"), "nor the wall variant")
        assertFalse(
            body.contains("not obtainable in survival"),
            "and with them gone the strip has nothing left to warn about",
        )
    }

    @Test
    fun `a placed id resolved away is gathered under its real item on import`() = testApplication {
        // The review screen posts back what it rendered, so the project gets the resolved rows.
        setupRoutes()
        val client = createClient { followRedirects = false }

        val response = client.post("/ideas/$placedIdeaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "worldId=$worldId&name=Wiring&" + materials(
                    "minecraft:redstone" to 600,
                    "minecraft:birch_sign" to 1,
                )
            )
        }

        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())
        val projectId = response.headers["Location"]!!.substringAfterLast("/").toInt()

        assertEquals(
            listOf("minecraft:birch_sign" to 1, "minecraft:redstone" to 600),
            readRequirements(projectId).sortedBy { it.first },
        )
    }

    @Test
    fun `a slow-to-gather row keeps its chip but never reaches the strip`() = testApplication {
        // MCO-397. An elytra is a real expedition and the row says so, but the user picked the
        // build knowing that — it is not worth a "!" above the fold. The strip is reserved for
        // creative-only rows, the one kind that asks for a decision before the import lands.
        setupRoutes()

        val response = client.get("/ideas/$expensiveIdeaId/import/review?worldId=$worldId") {
            addAuthCookie(this)
        }

        val body = response.bodyAsText()
        assertContains(body, "Slow to gather", message = "the row chip stays")
        assertFalse(body.contains("Expensive to gather"), "but the strip heading is gone")
        assertFalse(body.contains("callout__icon"), "and with nothing left to say, so is the whole strip")
    }

    @Test
    fun `an import that includes air drops it instead of creating a resource row`() = testApplication {
        setupRoutes()
        val client = createClient { followRedirects = false }

        // The review screen never renders an air row, so this is a hand-rolled post — the
        // point being that "air is never a material" holds where the list becomes final too.
        val response = client.post("/ideas/$airyIdeaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "worldId=$worldId&name=Roof&" + materials(
                    "minecraft:air" to 9389854,
                    "minecraft:oak_planks" to 64,
                )
            )
        }

        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())
        val projectId = response.headers["Location"]!!.substringAfterLast("/").toInt()

        assertEquals(listOf("minecraft:oak_planks" to 64), readRequirements(projectId))
    }

    // ---- create ------------------------------------------------------------------

    @Test
    fun `import creates the project from the reviewed rows only`() = testApplication {
        setupRoutes()
        val client = createClient { followRedirects = false }

        val response = client.post("/ideas/$ideaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "worldId=$worldId&name=Reviewed Iron Farm&" + materials(
                    "minecraft:iron_ingot" to 64,
                    "!minecraft:oak_planks" to 32,
                    "minecraft:redstone" to 8,
                )
            )
        }

        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())
        val projectId = response.headers["Location"]!!.substringAfterLast("/").toInt()

        assertEquals("Reviewed Iron Farm", getProjectName(projectId))
        assertEquals(
            listOf("minecraft:iron_ingot" to 64, "minecraft:redstone" to 8),
            readRequirements(projectId),
            "oak planks were excluded on the review screen",
        )
        assertEquals(ideaId, getProjectIdeaId(projectId), "the idea link survives the review step")
    }

    @Test
    fun `import records what the idea produces`() = testApplication {
        setupRoutes()
        val client = createClient { followRedirects = false }

        val response = client.post("/ideas/$ideaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("worldId=$worldId&" + materials("minecraft:iron_ingot" to 64))
        }

        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())
        val projectId = response.headers["Location"]!!.substringAfterLast("/").toInt()

        // The whole of MCO-411: this list was empty for every import ever made, so no imported
        // farm has ever supplied anything.
        assertEquals(listOf("minecraft:iron_ingot" to 620), readProductions(projectId))
    }

    @Test
    fun `an idea with several modes imports the highest-yield one`() = testApplication {
        setupRoutes()
        val client = createClient { followRedirects = false }

        val response = client.post("/ideas/$multiModeIdeaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("worldId=$worldId&" + materials("minecraft:oak_planks" to 10))
        }

        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())
        val projectId = response.headers["Location"]!!.substringAfterLast("/").toInt()

        assertEquals(
            listOf(
                "minecraft:blaze_powder" to 0,
                "minecraft:blaze_rod" to 400,
                "minecraft:bone" to 500,
            ),
            readProductions(projectId),
            "900/h across three items beats the skeletons-only mode's 700",
        )
    }

    @Test
    fun `a production this version has no item for is reported, not fatal`() = testApplication {
        setupRoutes()
        val client = createClient { followRedirects = false }

        val review = client.get("/ideas/$unknownProductionIdeaId/import/review?worldId=$worldId") {
            addAuthCookie(this)
        }

        // The whole of MCO-456: this GET used to fail outright, so the material list — the part
        // the user was asked to review — was unreachable over a field this screen does not have.
        assertEquals(HttpStatusCode.OK, review.status)
        val body = review.bodyAsText()
        assertContains(body, "Not recorded as production")
        assertContains(body, "Sculk Shrieker")
        // Ids, not display names: the container is shared across IT classes, so whichever class
        // seeds an id first owns its name (MCO-361).
        assertContains(body, "minecraft:oak_planks=12")

        val response = client.post("/ideas/$unknownProductionIdeaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("worldId=$worldId&" + materials("minecraft:oak_planks" to 12))
        }

        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())
        val projectId = response.headers["Location"]!!.substringAfterLast("/").toInt()

        assertEquals(
            listOf("minecraft:iron_ingot" to 90),
            readProductions(projectId),
            "the production this version does know about still supplies the world",
        )
    }

    // ---- already built (MCO-457) ---------------------------------------------------

    @Test
    fun `the review screen offers to record the idea as already built`() = testApplication {
        setupRoutes()

        val response = client.get("/ideas/$ideaId/import/review?worldId=$worldId") { addAuthCookie(this) }

        val body = response.bodyAsText()
        assertContains(body, "This is already built in my world")
        assertContains(body, "name=\"alreadyBuilt\"")
        // Both submit labels ship; CSS picks one, so the screen still says the right thing
        // with scripting off.
        assertContains(body, "Record as built")
        assertContains(body, "Create project")
    }

    @Test
    fun `an already-built import lands operational, producing, with nothing to gather`() = testApplication {
        setupRoutes()
        val client = createClient { followRedirects = false }

        val response = client.post("/ideas/$ideaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "worldId=$worldId&name=Standing Iron Farm&alreadyBuilt=on&" + materials(
                    "minecraft:iron_ingot" to 64,
                    "minecraft:oak_planks" to 32,
                )
            )
        }

        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())
        val projectId = response.headers["Location"]!!.substringAfterLast("/").toInt()

        // MCO-298's lifecycle, reached through the idea door. DONE is the producing condition
        // (MCO-287), so this is what makes it supply rather than promise.
        assertEquals(
            ProjectStage.COMPLETED.name to ProjectState.DONE.name,
            readLifecycle(projectId),
        )
        assertEquals(
            listOf("minecraft:iron_ingot" to 620),
            readProductions(projectId),
            "the idea's rates are the reason to come through this door rather than the MCO-298 form",
        )
        assertEquals(
            emptyList(),
            readRequirements(projectId),
            "no invented gathering work for a build that already exists",
        )
        assertEquals(ideaId, getProjectIdeaId(projectId), "and the link back to the idea survives")
    }

    @Test
    fun `an already-built import supplies the world immediately`() = testApplication {
        setupRoutes()
        val client = createClient { followRedirects = false }

        val response = client.post("/ideas/$multiModeIdeaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "worldId=$worldId&name=Standing Fortress Farm&alreadyBuilt=on&" +
                    materials("minecraft:oak_planks" to 10)
            )
        }

        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())

        // The whole of MCO-457: before it, this import arrived ACTIVE and showed up in
        // GetWorldPlannedFarmsStep as supply that "will come" — for a farm already standing.
        val supplies = runBlocking {
            GetWorldFarmSuppliesStep.process(WorldFarmSuppliesInput(worldId, excludeProjectId = -1))
        }
        assertIs<Result.Success<*>>(supplies)
        val supplied = (supplies as Result.Success).value.map { it.itemId to it.projectName }
        assertTrue(
            supplied.contains("minecraft:blaze_rod" to "Standing Fortress Farm"),
            "got $supplied",
        )
    }

    @Test
    fun `an already-built import ignores the material list instead of refusing an empty one`() = testApplication {
        setupRoutes()
        val client = createClient { followRedirects = false }

        // Striking every row is refused for an ordinary import — it would import everything at
        // the moment the user asked for nothing. Here the list is moot either way, so the
        // guard must not fire: the same post with the box ticked has to succeed.
        val response = client.post("/ideas/$ideaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "worldId=$worldId&name=Struck But Built&alreadyBuilt=on&" +
                    materials("!minecraft:iron_ingot" to 64)
            )
        }

        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())
        val projectId = response.headers["Location"]!!.substringAfterLast("/").toInt()
        assertEquals(emptyList(), readRequirements(projectId))
    }

    @Test
    fun `an ordinary import still lands as work to do`() = testApplication {
        setupRoutes()
        val client = createClient { followRedirects = false }

        val response = client.post("/ideas/$ideaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("worldId=$worldId&name=Planned Iron Farm&" + materials("minecraft:iron_ingot" to 64))
        }

        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())
        val projectId = response.headers["Location"]!!.substringAfterLast("/").toInt()

        assertEquals(
            ProjectStage.RESOURCE_GATHERING.name to ProjectState.ACTIVE.name,
            readLifecycle(projectId),
            "an unticked box must leave the common case exactly as it was",
        )
        assertEquals(listOf("minecraft:iron_ingot" to 64), readRequirements(projectId))
    }

    @Test
    fun `a submission with no rows is refused rather than importing the whole idea`() = testApplication {
        setupRoutes()
        val before = countProjects()

        // Unchecking every row sends a list where every row is struck. Importing the idea's
        // full list here would do the opposite of what the user just asked for.
        val response = client.post("/ideas/$ideaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("worldId=$worldId&" + materials("!minecraft:iron_ingot" to 64))
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(before, countProjects())
    }

    @Test
    fun `a submission with no material list at all is refused`() = testApplication {
        setupRoutes()
        val before = countProjects()

        // Also the shape a review page rendered before MCO-315 would send. There is no
        // "import the whole idea instead" fallback, by design.
        val response = client.post("/ideas/$ideaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("worldId=$worldId&qty[minecraft:iron_ingot]=64")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(before, countProjects())
    }

    @Test
    fun `an item outside the world's catalog is refused`() = testApplication {
        setupRoutes()
        val before = countProjects()

        val response = client.post("/ideas/$ideaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("worldId=$worldId&" + materials("minecraft:not_a_real_item" to 1))
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(before, countProjects())
    }

    @Test
    fun `a non-member cannot import into someone else's world`() = testApplication {
        setupRoutes()
        val outsider = createExtraUser("idea-import-outsider")
        val before = countProjects()

        val response = client.post("/ideas/$ideaId/import") {
            addAuthCookie(this, outsider)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("worldId=$worldId&" + materials("minecraft:iron_ingot" to 1))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(before, countProjects())
    }

    // ---- MCO-315 — a list wider than the old request-parameter cap -------------------

    @Test
    fun `an idea list far past the old 466-row cutoff imports every included row`() = testApplication {
        setupRoutes()
        val client = createClient { followRedirects = false }

        val struck = (1..BULK_ROWS).filter { it % 7 == 0 }.map { "minecraft:bulk_item_$it" }.toSet()
        val rows = (1..BULK_ROWS).map { "minecraft:bulk_item_$it" to it }

        val response = client.post("/ideas/$wideIdeaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "worldId=$worldId&name=Wide Import&" + materials(
                    *rows.map { (id, amount) -> (if (id in struck) "!$id" else id) to amount }.toTypedArray()
                )
            )
        }

        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())
        val projectId = response.headers["Location"]!!.substringAfterLast("/").toInt()

        val persisted = readRequirements(projectId).toMap()
        assertEquals(BULK_ROWS - struck.size, persisted.size, "every included row is persisted")
        rows.forEach { (id, amount) ->
            if (id in struck) {
                assertFalse(id in persisted, "$id was struck and must not be persisted")
            } else {
                assertEquals(amount, persisted[id], "$id must survive the import")
            }
        }
    }

    @Test
    fun `an idea list that arrives short is refused rather than partly imported`() = testApplication {
        setupRoutes()
        val before = countProjects()

        val full = ReviewedMaterialsCodec.encode(
            (1..BULK_ROWS).map { ReviewedMaterial("minecraft:bulk_item_$it", it, included = true) }
        )
        val truncated = full.split(";").take(2 + 466).joinToString(";")

        val response = client.post("/ideas/$wideIdeaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("worldId=$worldId&name=Cut Short&${ReviewedMaterialsCodec.FIELD}=$truncated")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertContains(response.bodyAsText(), "466 of $BULK_ROWS")
        assertEquals(before, countProjects())
    }

    @Test
    fun `unauthenticated review is redirected`() = testApplication {
        setupRoutes()
        val unauth = createClient { followRedirects = false }

        val response = unauth.get("/ideas/$ideaId/import/review?worldId=$worldId")

        assertEquals(HttpStatusCode.Found, response.status)
    }

    // ---- routing — mirrors IdeaHandler --------------------------------------------

    // ---- build-time variants (MCO-463) -------------------------------------------

    @Test
    fun `a design with two ways of building it offers the choice on the review`() = testApplication {
        setupRoutes()

        val response = client.get("/ideas/$cobbleIdeaId/import/review?worldId=$worldId") { addAuthCookie(this) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "This design can be built more than one way")
        assertContains(body, "1 module")
        assertContains(body, "4 modules")
    }

    @Test
    fun `the default is the author's first variant, not the largest build`() = testApplication {
        setupRoutes()

        val body = client.get("/ideas/$cobbleIdeaId/import/review?worldId=$worldId") { addAuthCookie(this) }
            .bodyAsText()

        // `position` is the author's ordering and explicitly not a ranking; defaulting to the
        // biggest producer would quietly commit someone to the 4-module build's 4x bill.
        assertContains(body, "minecraft:cobblestone=400")
        assertFalse(body.contains("minecraft:cobblestone=1600"), "the 4-module list is not the default")
        assertFalse(body.contains("minecraft:hopper=256"), "nor are its hoppers")
    }

    @Test
    fun `choosing the other variant changes the material list`() = testApplication {
        setupRoutes()

        val body = client.get("/ideas/$cobbleIdeaId/import/review?worldId=$worldId&buildTimeMode=4+modules") {
            addAuthCookie(this)
        }.bodyAsText()

        assertContains(body, "minecraft:cobblestone=1600")
        assertContains(body, "minecraft:hopper=256")
        assertFalse(body.contains("minecraft:cobblestone=400"), "the single-module list is gone")
    }

    @Test
    fun `the chosen variant rides the form so the POST imports what was on screen`() = testApplication {
        setupRoutes()

        val body = client.get("/ideas/$cobbleIdeaId/import/review?worldId=$worldId&buildTimeMode=4+modules") {
            addAuthCookie(this)
        }.bodyAsText()

        assertContains(body, "name=\"buildTimeMode\"")
        assertContains(body, "value=\"4 modules\"")
    }

    @Test
    fun `importing a variant records its materials and its rate, not a sibling's`() = testApplication {
        setupRoutes()

        val response = client.post("/ideas/$cobbleIdeaId/import") {
            addAuthCookie(this)
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "worldId=$worldId&name=Cobble&buildTimeMode=4+modules&" + materials(
                    "minecraft:cobblestone" to 1600,
                    "minecraft:hopper" to 256,
                )
            )
        }

        assertEquals(HttpStatusCode.SeeOther, response.status, response.bodyAsText())
        val projectId = response.headers["Location"]!!.substringAfterLast("/").toInt()

        assertEquals(
            listOf("minecraft:cobblestone" to 1600, "minecraft:hopper" to 256),
            readRequirements(projectId).sortedBy { it.first },
        )
        // The 4-module rate, because that is the build being made. Importing the single-module
        // list with the 4-module throughput would be the exact drift MCO-463 exists to stop.
        assertEquals(listOf("minecraft:tuff" to 924_000), readProductions(projectId))
    }

    @Test
    fun `a design with one material list offers no choice at all`() = testApplication {
        setupRoutes()

        val body = client.get("/ideas/$ideaId/import/review?worldId=$worldId") { addAuthCookie(this) }
            .bodyAsText()

        // Every idea in the bank before MCO-463, and most after it. The screen must look exactly
        // as it did.
        assertFalse(body.contains("This design can be built more than one way"))
        assertFalse(body.contains("name=\"buildTimeMode\""))
    }

    @Test
    fun `a variant name that no longer exists falls back rather than importing nothing`() = testApplication {
        setupRoutes()

        val body = client.get("/ideas/$cobbleIdeaId/import/review?worldId=$worldId&buildTimeMode=8+modules") {
            addAuthCookie(this)
        }.bodyAsText()

        // What a stale review URL looks like after the author renamed a variant.
        assertEquals(true, body.contains("minecraft:cobblestone=400"), "falls back to the author's first")
    }

    private fun ApplicationTestBuilder.setupRoutes() {
        routing {
            install(AuthPlugin)
            route("/ideas/{ideaId}") {
                install(IdeaParamPlugin)
                route("/import") {
                    get("/review") { call.handleReviewIdeaImport() }
                    post { call.handleImportIdea() }
                }
            }
        }
    }

    // ---- fixtures ------------------------------------------------------------------

    private fun createWorld(name: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(name = name, description = "test", version = version)
        )
        (result as Result.Success).value
    }

    private fun seedItem(itemId: String, itemName: String) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert("INSERT INTO minecraft_version (version) VALUES ('1.21.4') ON CONFLICT DO NOTHING"),
            parameterSetter = { _, _ -> }
        ).process(Unit)
        DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO minecraft_items (version, item_id, item_name) VALUES ('1.21.4', ?, ?) ON CONFLICT DO NOTHING"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, itemId)
                stmt.setString(2, itemName)
            }
        ).process(Unit)
    }

    private fun createIdea(name: String): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                """
                INSERT INTO ideas (name, description, category, author, difficulty, minecraft_version_range, category_data, created_by)
                VALUES (?, 'test idea', 'FARM', '{"type":"single","name":"tester"}'::jsonb, 'EASY',
                        '{"type":"app.mcorg.domain.model.minecraft.MinecraftVersionRange.Unbounded"}'::jsonb, '{}'::jsonb, ?)
                RETURNING id
                """.trimIndent()
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, user.id)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun seedItems(items: List<Pair<String, String>>) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert("INSERT INTO minecraft_version (version) VALUES ('1.21.4') ON CONFLICT DO NOTHING"),
            parameterSetter = { _, _ -> }
        ).process(Unit)
        DatabaseSteps.batchUpdate<Pair<String, String>>(
            SafeSQL.insert(
                "INSERT INTO minecraft_items (version, item_id, item_name) VALUES ('1.21.4', ?, ?) ON CONFLICT DO NOTHING"
            ),
            parameterSetter = { stmt, (itemId, itemName) ->
                stmt.setString(1, itemId)
                stmt.setString(2, itemName)
            }
        ).process(items)
    }

    private fun addRequirements(ideaId: Int, requirements: List<Pair<String, Int>>) = runBlocking {
        DatabaseSteps.batchUpdate<Pair<String, Int>>(
            SafeSQL.insert("INSERT INTO idea_item_requirements (idea_id, item_id, quantity) VALUES (?, ?, ?)"),
            parameterSetter = { stmt, (itemId, quantity) ->
                stmt.setInt(1, ideaId)
                stmt.setString(2, itemId)
                stmt.setInt(3, quantity)
            }
        ).process(requirements)
    }

    private fun addRequirement(ideaId: Int, itemId: String, quantity: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO idea_item_requirements (idea_id, item_id, quantity) VALUES (?, ?, ?)"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, ideaId)
                stmt.setString(2, itemId)
                stmt.setInt(3, quantity)
            }
        ).process(Unit)
    }

    private fun countProjects(): Int = runBlocking {
        val result = DatabaseSteps.query<Int, Int>(
            sql = SafeSQL.select("SELECT COUNT(*) FROM projects WHERE world_id = ?"),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) },
            resultMapper = { rs -> if (rs.next()) rs.getInt(1) else 0 }
        ).process(worldId)
        (result as Result.Success).value
    }

    private fun getProjectName(projectId: Int): String = runBlocking {
        val result = DatabaseSteps.query<Int, String>(
            sql = SafeSQL.select("SELECT name FROM projects WHERE id = ?"),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) },
            resultMapper = { rs -> rs.next(); rs.getString("name") }
        ).process(projectId)
        (result as Result.Success).value
    }

    private fun readLifecycle(projectId: Int): Pair<String, String> = runBlocking {
        val result = DatabaseSteps.query<Int, Pair<String, String>>(
            sql = SafeSQL.select("SELECT stage, state FROM projects WHERE id = ?"),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) },
            resultMapper = { rs -> rs.next(); rs.getString("stage") to rs.getString("state") }
        ).process(projectId)
        (result as Result.Success).value
    }

    private fun getProjectIdeaId(projectId: Int): Int = runBlocking {
        val result = DatabaseSteps.query<Int, Int>(
            sql = SafeSQL.select("SELECT project_idea_id FROM projects WHERE id = ?"),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) },
            resultMapper = { rs -> rs.next(); rs.getInt("project_idea_id") }
        ).process(projectId)
        (result as Result.Success).value
    }

    private fun readRequirements(projectId: Int): List<Pair<String, Int>> = runBlocking {
        val result = DatabaseSteps.query<Int, List<Pair<String, Int>>>(
            sql = SafeSQL.select(
                "SELECT item_id, required FROM resource_gathering WHERE project_id = ? ORDER BY item_id"
            ),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) },
            resultMapper = { rs ->
                val rows = mutableListOf<Pair<String, Int>>()
                while (rs.next()) rows.add(rs.getString("item_id") to rs.getInt("required"))
                rows
            }
        ).process(projectId)
        (result as Result.Success).value
    }

    private fun addProductionMode(ideaId: Int, name: String, rates: List<Pair<String, Int?>>) = runBlocking {
        val modeId = DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO idea_production_modes (idea_id, name, position) " +
                    "VALUES (?, ?, (SELECT COALESCE(MAX(position) + 1, 0) FROM idea_production_modes WHERE idea_id = ?)) " +
                    "RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, ideaId)
                stmt.setString(2, name)
                stmt.setInt(3, ideaId)
            }
        ).process(Unit).let { (it as Result.Success).value }

        DatabaseSteps.batchUpdate<Pair<String, Int?>>(
            SafeSQL.insert("INSERT INTO idea_production_rates (mode_id, item_id, rate_per_hour) VALUES (?, ?, ?)"),
            parameterSetter = { stmt, (itemId, rate) ->
                stmt.setInt(1, modeId)
                stmt.setString(2, itemId)
                if (rate == null) stmt.setNull(3, java.sql.Types.INTEGER) else stmt.setInt(3, rate)
            }
        ).process(rates)
    }

    /**
     * A build-time variant: its own rates *and* its own material list (MCO-463, V2_61_0).
     *
     * The requirement rows carry `mode_id`, which is what makes them this variant's list rather
     * than the idea's base one. An idea with variants has no base rows at all.
     */
    private fun addBuildTimeMode(
        ideaId: Int,
        name: String,
        rates: List<Pair<String, Int?>>,
        requirements: List<Pair<String, Int>>,
    ) = runBlocking {
        val modeId = DatabaseSteps.update<Unit>(
            SafeSQL.insert(
                "INSERT INTO idea_production_modes (idea_id, name, position, kind) " +
                    "VALUES (?, ?, (SELECT COALESCE(MAX(position) + 1, 0) FROM idea_production_modes WHERE idea_id = ?), 'BUILD_TIME') " +
                    "RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, ideaId)
                stmt.setString(2, name)
                stmt.setInt(3, ideaId)
            }
        ).process(Unit).let { (it as Result.Success).value }

        DatabaseSteps.batchUpdate<Pair<String, Int?>>(
            SafeSQL.insert("INSERT INTO idea_production_rates (mode_id, item_id, rate_per_hour) VALUES (?, ?, ?)"),
            parameterSetter = { stmt, (itemId, rate) ->
                stmt.setInt(1, modeId)
                stmt.setString(2, itemId)
                if (rate == null) stmt.setNull(3, java.sql.Types.INTEGER) else stmt.setInt(3, rate)
            }
        ).process(rates)

        DatabaseSteps.batchUpdate<Pair<String, Int>>(
            SafeSQL.insert(
                "INSERT INTO idea_item_requirements (idea_id, item_id, quantity, mode_id) VALUES (?, ?, ?, ?)"
            ),
            parameterSetter = { stmt, (itemId, quantity) ->
                stmt.setInt(1, ideaId)
                stmt.setString(2, itemId)
                stmt.setInt(3, quantity)
                stmt.setInt(4, modeId)
            }
        ).process(requirements)
    }

    private fun readProductions(projectId: Int): List<Pair<String, Int>> = runBlocking {
        val result = DatabaseSteps.query<Int, List<Pair<String, Int>>>(
            sql = SafeSQL.select(
                "SELECT item_id, rate_per_hour FROM project_productions WHERE project_id = ? ORDER BY item_id"
            ),
            parameterSetter = { stmt, id -> stmt.setInt(1, id) },
            resultMapper = { rs ->
                val rows = mutableListOf<Pair<String, Int>>()
                while (rs.next()) rows.add(rs.getString("item_id") to rs.getInt("rate_per_hour"))
                rows
            }
        ).process(projectId)
        (result as Result.Success).value
    }

    /**
     * A request body carrying the whole list in one field, as the review page now sends it.
     * Prefix an id with `!` to mark the row struck.
     */
    private fun materials(vararg rows: Pair<String, Int>): String {
        val encoded = ReviewedMaterialsCodec.encode(
            rows.map { (id, amount) ->
                ReviewedMaterial(id.removePrefix("!"), amount, included = !id.startsWith("!"))
            }
        )
        return "${ReviewedMaterialsCodec.FIELD}=$encoded"
    }

    private companion object {
        /** Comfortably past the ~466 rows that used to fit in a request body. */
        const val BULK_ROWS = 600
    }
}
