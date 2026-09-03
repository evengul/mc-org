package app.mcorg.pipeline.world

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.world.World
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.world.commonsteps.GetPermittedWorldsInput
import app.mcorg.pipeline.world.commonsteps.GetPermittedWorldsStep
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals

/**
 * The worlds page puts `worlds.first()` in the hero slot, so whatever this query returns first is
 * the world the user lands on. That makes the ordering's *totality* a product property, not a
 * detail: every key ahead of the primary key can tie — `pinned` is a boolean, `last_opened_at` is
 * NULL until the user opens a world, `updated_at` defaults to the transaction timestamp, and
 * `world.name` carries an index but no unique constraint. Two never-opened worlds with the same
 * name tie on all four, and before MCO-500 the hero was then whichever row the plan happened to
 * emit first — stable enough to look fine, arbitrary enough to flip.
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class GetPermittedWorldsOrderingIT : WithUser() {

    // Every IT signs in as the same profile against a container that is never truncated between
    // classes, so without this the list under test is every world an earlier class left behind --
    // and per test, every world an earlier test in this class left behind.
    @BeforeEach
    fun clearWorlds() = DatabaseTestExtension.cleanDatabase()

    @Test
    fun `worlds tied on every sort key are ordered by id, newest first`() {
        val older = createWorld("Twin World")
        val newer = createWorld("Twin World")
        // What two worlds created in a single transaction get: CURRENT_TIMESTAMP is the
        // transaction timestamp, so their created_at/updated_at are equal to the microsecond.
        tieUpdatedAt(older, newer)

        assertEquals(
            listOf(newer, older),
            worlds().map { it.id },
            "a complete tie left the hero slot to the query plan"
        )
    }

    /** The tiebreaker must stay a tiebreaker: an older world that is pinned still leads. */
    @Test
    fun `a pinned world outranks a newer unpinned one`() {
        val pinnedWorld = createWorld("Pinned World")
        val newerWorld = createWorld("Newer World")
        pin(pinnedWorld)

        assertEquals(listOf(pinnedWorld, newerWorld), worlds().map { it.id })
    }

    /** Likewise for recency: a more-recently-opened older world still leads. */
    @Test
    fun `a more recently opened world outranks a newer never-opened one`() {
        val openedWorld = createWorld("Opened World")
        val newerWorld = createWorld("Newer World")
        open(openedWorld)

        assertEquals(listOf(openedWorld, newerWorld), worlds().map { it.id })
    }

    private fun worlds(): List<World> = runBlocking {
        val result = GetPermittedWorldsStep.process(GetPermittedWorldsInput(userId = user.id))
        (result as Result.Success).value
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

    private fun tieUpdatedAt(vararg worldIds: Int) = worldIds.forEach { worldId ->
        runBlocking {
            DatabaseSteps.update<Unit>(
                sql = SafeSQL.update("UPDATE world SET created_at = 'epoch', updated_at = 'epoch' WHERE id = ?"),
                parameterSetter = { stmt, _ -> stmt.setInt(1, worldId) }
            ).process(Unit)
        }
    }

    private fun pin(worldId: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.update("UPDATE world_members SET pinned = true WHERE world_id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, worldId) }
        ).process(Unit)
        Unit
    }

    private fun open(worldId: Int) = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.update("UPDATE world_members SET last_opened_at = now() WHERE world_id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, worldId) }
        ).process(Unit)
        Unit
    }
}
