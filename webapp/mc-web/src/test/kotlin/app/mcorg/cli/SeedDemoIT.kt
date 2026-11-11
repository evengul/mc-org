package app.mcorg.cli

import app.mcorg.test.postgres.DatabaseTestExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.sql.DriverManager
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MCO-657 — the cloud demo seed. Each test seeds for its own username, so they share the
 * container without seeing each other's world (the world key is name + member).
 */
@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class SeedDemoIT {

    private val fullVersion = "26.3.0"
    private val partialVersion = "26.2.0"

    @BeforeAll
    fun catalog() {
        insertVersion(fullVersion, SEED_ITEM_IDS)
        // Everything but stone bricks, so a seed against it has to refuse rather than half-write.
        insertVersion(partialVersion, SEED_ITEM_IDS - "minecraft:stone_bricks")
    }

    @Test
    fun `seeds a world the demo sign-in user owns, with projects, an ordering and public ideas`() = runBlocking {
        val outcome = seedDemo("seed-alice", fullVersion)

        assertTrue(outcome.worldCreated)
        assertEquals(SEED_IDEAS.size, outcome.ideasCreated)
        // The uuid DemoSignInPipeline derives, so the first sign-in finds this user.
        val userId = int("SELECT user_id FROM minecraft_profiles WHERE uuid = 'seed-alice-uuid'")
        assertEquals(0, int("SELECT world_role FROM world_members WHERE world_id = ${outcome.worldId} AND user_id = $userId"))
        assertEquals(fullVersion, string("SELECT version FROM world WHERE id = ${outcome.worldId}"))

        assertEquals(SEED_PROJECTS.size, int("SELECT count(*) FROM projects WHERE world_id = ${outcome.worldId}"))
        assertEquals(
            SEED_PROJECTS.sumOf { it.resources.size },
            int("SELECT count(*) FROM resource_gathering r JOIN projects p ON p.id = r.project_id WHERE p.world_id = ${outcome.worldId}"),
        )
        assertEquals("DONE", string("SELECT state FROM projects WHERE world_id = ${outcome.worldId} AND name = 'Iron Farm'"))
        assertEquals(
            "minecraft:iron_ingot",
            string("SELECT pp.item_id FROM project_productions pp JOIN projects p ON p.id = pp.project_id WHERE p.world_id = ${outcome.worldId}"),
        )
        assertEquals(
            "Main Base",
            string("""
                SELECT p.name FROM project_dependencies d JOIN projects p ON p.id = d.project_id
                WHERE p.world_id = ${outcome.worldId}
            """),
        )
        assertEquals(SEED_IDEAS.size, int("SELECT count(*) FROM ideas WHERE created_by = $userId AND visibility = 'PUBLIC'"))
        assertEquals(
            SEED_IDEAS.size,
            int("SELECT count(DISTINCT m.idea_id) FROM idea_production_modes m JOIN idea_production_rates r ON r.mode_id = m.id JOIN ideas i ON i.id = m.idea_id WHERE i.created_by = $userId"),
        )
    }

    @Test
    fun `a second run changes nothing`() = runBlocking {
        val first = seedDemo("seed-bob", fullVersion)
        val before = rowCounts()

        val second = seedDemo("seed-bob", fullVersion)

        assertFalse(second.worldCreated)
        assertEquals(first.worldId, second.worldId)
        assertEquals(0, second.ideasCreated)
        assertEquals(before, rowCounts())
    }

    @Test
    fun `a version missing a seeded item is refused before anything is written`() = runBlocking {
        val before = rowCounts()

        val failure = assertThrows<SeedFailure> { runBlocking { seedDemo("seed-carol", partialVersion) } }

        assertContains(failure.message!!, "minecraft:stone_bricks")
        // The user row is the one write that precedes the check: it is what sign-in creates anyway.
        assertEquals(before - "users" - "minecraft_profiles", rowCounts() - "users" - "minecraft_profiles")
    }

    @Test
    fun `a version that is not ingested is refused`() {
        val failure = assertThrows<SeedFailure> { runBlocking { seedDemo("seed-dave", "1.0.0") } }
        assertContains(failure.message!!, "not ingested")
    }

    private fun rowCounts(): Map<String, Int> =
        listOf(
            "users", "minecraft_profiles", "world", "world_members", "projects", "resource_gathering",
            "project_productions", "project_dependencies", "ideas", "idea_production_modes",
            "idea_production_rates", "idea_item_requirements",
        ).associateWith { int("SELECT count(*) FROM $it") }

    private fun insertVersion(version: String, itemIds: Set<String>) {
        DatabaseTestExtension.executeSQL("INSERT INTO minecraft_version (version) VALUES ('$version') ON CONFLICT DO NOTHING")
        itemIds.forEach { id ->
            DatabaseTestExtension.executeSQL(
                "INSERT INTO minecraft_items (version, item_id, item_name) VALUES ('$version', '$id', '$id') ON CONFLICT DO NOTHING"
            )
        }
    }

    private fun <T> single(sql: String, read: (java.sql.ResultSet) -> T): T =
        DriverManager.getConnection(
            DatabaseTestExtension.getJdbcUrl(), DatabaseTestExtension.getUsername(), DatabaseTestExtension.getPassword()
        ).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rs ->
                    check(rs.next()) { "no row for: $sql" }
                    read(rs)
                }
            }
        }

    private fun int(sql: String): Int = single(sql) { it.getInt(1) }
    private fun string(sql: String): String = single(sql) { it.getString(1) }
}
