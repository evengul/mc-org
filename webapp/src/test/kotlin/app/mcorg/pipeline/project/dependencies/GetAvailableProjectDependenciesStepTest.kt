package app.mcorg.pipeline.project.dependencies

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.project.ProjectType
import app.mcorg.domain.pipeline.Result
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.failure.DatabaseFailure
import app.mcorg.pipeline.project.CreateProjectInput
import app.mcorg.pipeline.project.CreateProjectStep
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class GetAvailableProjectDependenciesStepTest : WithUser() {

    private var worldId: Int = -1
    private var projectA: Int = -1
    private var projectB: Int = -1
    private var projectC: Int = -1
    private var projectD: Int = -1

    @BeforeEach
    fun setUp() {
        // Clean the database before each test
        DatabaseTestExtension.cleanDatabase()

        // Create a test world
        worldId = createTestWorld("First world", "The first test world")

        // Create test projects
        projectA = createTestProject("Project A", "Building A")
        projectB = createTestProject("Project B", "Building B")
        projectC = createTestProject("Project C", "Building C")
        projectD = createTestProject("Project D", "Building D")
    }

    @Test
    fun `Should return all other projects when no dependencies exist`() = runBlocking {
        val result = GetAvailableProjectDependenciesStep(worldId).process(projectA)

        assertTrue(result is Result.Success)
        val availableProjects = result.value

        assertEquals(3, availableProjects.size)
        assertTrue(availableProjects.any { it.id == projectB && it.name == "Project B" })
        assertTrue(availableProjects.any { it.id == projectC && it.name == "Project C" })
        assertTrue(availableProjects.any { it.id == projectD && it.name == "Project D" })
    }

    @Test
    fun `Should exclude direct dependencies from available projects`() = runBlocking {
        // Create dependency: A depends on B
        createProjectDependency(projectA, projectB)

        val result = GetAvailableProjectDependenciesStep(worldId).process(projectA)

        assertTrue(result is Result.Success)
        val availableProjects = result.value

        assertEquals(2, availableProjects.size)
        assertFalse(availableProjects.any { it.id == projectB }) // B is excluded (direct dependency)
        assertTrue(availableProjects.any { it.id == projectC })
        assertTrue(availableProjects.any { it.id == projectD })
    }

    @Test
    fun `Should prevent direct cycle - if A depends on B, then B cannot depend on A`() = runBlocking {
        // Create dependency: A depends on B
        createProjectDependency(projectA, projectB)

        // Check available dependencies for B
        val result = GetAvailableProjectDependenciesStep(worldId).process(projectB)

        assertTrue(result is Result.Success)
        val availableProjects = result.value

        assertEquals(2, availableProjects.size)
        assertFalse(availableProjects.any { it.id == projectA }) // A is excluded (would create cycle)
        assertTrue(availableProjects.any { it.id == projectC })
        assertTrue(availableProjects.any { it.id == projectD })
    }

    @Test
    fun `Should prevent transitive cycle - if A depends on B and B depends on C, then C cannot depend on A`() = runBlocking {
        // Create dependency chain: A -> B -> C
        createProjectDependency(projectA, projectB)
        createProjectDependency(projectB, projectC)

        // Check available dependencies for C
        val result = GetAvailableProjectDependenciesStep(worldId).process(projectC)

        assertTrue(result is Result.Success)
        val availableProjects = result.value

        assertEquals(1, availableProjects.size)
        assertFalse(availableProjects.any { it.id == projectA }) // A is excluded (would create cycle A->B->C->A)
        assertFalse(availableProjects.any { it.id == projectB }) // B is excluded (would create cycle B->C->B)
        assertTrue(availableProjects.any { it.id == projectD })  // D is still available
    }

    @Test
    fun `Should handle complex dependency graph without cycles`() = runBlocking {
        // Create complex dependency structure:
        // A -> B, A -> C, B -> D
        createProjectDependency(projectA, projectB)
        createProjectDependency(projectA, projectC)
        createProjectDependency(projectB, projectD)

        // Check available dependencies for D
        val result = GetAvailableProjectDependenciesStep(worldId).process(projectD)

        assertTrue(result is Result.Success)
        val availableProjects = result.value

        assertEquals(1, availableProjects.size) // Only C is available for D
        // A is excluded (depends transitively on D: A->B->D, so D->A would create cycle)
        // B is excluded (depends directly on D: B->D, so D->B would create cycle)
        // C is available (no dependency relationship with D, so D->C is safe)
        assertTrue(availableProjects.any { it.id == projectC && it.name == "Project C" })
    }

    @Test
    fun `Should return no available dependencies in fully connected dependency graph`() = runBlocking {
        // A -> B, A -> C, A -> D  (A depends on everyone)
        // B -> C, B -> D          (B depends on C and D)
        // C -> D                  (C depends on D)
        createProjectDependency(projectA, projectB)
        createProjectDependency(projectA, projectC)
        createProjectDependency(projectA, projectD)
        createProjectDependency(projectB, projectC)
        createProjectDependency(projectB, projectD)
        createProjectDependency(projectC, projectD)

        // Check available dependencies for A (the top-level project)
        val result = GetAvailableProjectDependenciesStep(worldId).process(projectA)

        assertTrue(result is Result.Success)
        val availableProjects = result.value

        assertEquals(0, availableProjects.size) // No projects available for A
        // B is excluded (A already depends on B directly)
        // C is excluded (A already depends on C directly)
        // D is excluded (A already depends on D directly)
        // All other projects are already dependencies, so no additional dependencies possible
    }

    @Test
    fun `Should return no available dependencies when all paths would create cycles`() = runBlocking {
        // Create a circular dependency scenario where every possible addition would create a cycle:
        // A -> B -> C -> A (if we allowed cycles)
        // But we'll create: A -> B, B -> C, and test what C can depend on
        createProjectDependency(projectA, projectB)
        createProjectDependency(projectB, projectC)

        // Now test what A can depend on (should exclude B, C, and itself)
        val resultA = GetAvailableProjectDependenciesStep(worldId).process(projectA)
        assertTrue(resultA is Result.Success)
        assertEquals(1, resultA.value.size) // Only D available for A
        assertTrue(resultA.value.any { it.id == projectD })

        // Create A -> D to make the graph more complex
        createProjectDependency(projectA, projectD)

        // Now create D -> A to establish that D depends on A
        createProjectDependency(projectD, projectA)

        // Check what B can depend on - should be nothing since:
        // - A is excluded (A -> B, so B -> A would create direct cycle)
        // - C is excluded (B already depends on C)
        // - D is excluded (D -> A -> B, so B -> D would create cycle)
        val resultB = GetAvailableProjectDependenciesStep(worldId).process(projectB)

        assertTrue(resultB is Result.Success)
        val availableProjectsB = resultB.value

        assertEquals(0, availableProjectsB.size) // No projects available for B
        // A is excluded (would create direct cycle: A->B->A)
        // C is excluded (B already depends on C directly)
        // D is excluded (would create cycle: D->A->B->D)
    }

    @Test
    fun `Should exclude self-reference`() = runBlocking {
        val result = GetAvailableProjectDependenciesStep(worldId).process(projectA)

        assertTrue(result is Result.Success)
        val availableProjects = result.value

        assertFalse(availableProjects.any { it.id == projectA }) // Project cannot depend on itself
    }

    @Test
    fun `Should handle empty project set gracefully`() = runBlocking {
        // Delete all other projects, leaving only projectA
        deleteProject(projectB)
        deleteProject(projectC)
        deleteProject(projectD)

        val result = GetAvailableProjectDependenciesStep(worldId).process(projectA)

        assertTrue(result is Result.Success)
        val availableProjects = result.value

        assertEquals(0, availableProjects.size)
    }

    @Test
    fun `Should return projects in alphabetical order`() = runBlocking {
        // Create projects with names that will test alphabetical ordering
        createTestProject("Zebra Project", "Last alphabetically")
        createTestProject("Middle Project", "Middle alphabetically")
        createTestProject("First Project", "First alphabetically")

        val result = GetAvailableProjectDependenciesStep(worldId).process(projectA)

        assertTrue(result is Result.Success)
        val availableProjects = result.value

        val projectNames = availableProjects.map { it.name }
        assertEquals(projectNames.sorted(), projectNames) // Verify alphabetical order
    }

    @Test
    fun `Should only return projects from the same world`() = runBlocking {
        // Create a second world with its own projects
        val secondWorldId = createTestWorld("Second World", "Another test world")
        val projectE = createTestProject("Project E", "Building E", secondWorldId)
        val projectF = createTestProject("Project F", "Building F", secondWorldId)

        // Test that projects from the first world only see projects from their world
        val result = GetAvailableProjectDependenciesStep(worldId).process(projectA)

        assertTrue(result is Result.Success)
        val availableProjects = result.value

        // Should only see projects B, C, D from the same world, not E, F from the other world
        assertEquals(3, availableProjects.size)
        assertTrue(availableProjects.any { it.id == projectB && it.name == "Project B" })
        assertTrue(availableProjects.any { it.id == projectC && it.name == "Project C" })
        assertTrue(availableProjects.any { it.id == projectD && it.name == "Project D" })
        assertFalse(availableProjects.any { it.id == projectE }) // E is in different world
        assertFalse(availableProjects.any { it.id == projectF }) // F is in different world

        // Test the reverse - projects from second world should only see their own world
        val resultSecondWorld = GetAvailableProjectDependenciesStep(secondWorldId).process(projectE)

        assertTrue(resultSecondWorld is Result.Success)
        val availableProjectsSecondWorld = resultSecondWorld.value

        // Should only see project F from the same world, not A, B, C, D from the other world
        assertEquals(1, availableProjectsSecondWorld.size)
        assertTrue(availableProjectsSecondWorld.any { it.id == projectF && it.name == "Project F" })
        assertFalse(availableProjectsSecondWorld.any { it.id == projectA })
        assertFalse(availableProjectsSecondWorld.any { it.id == projectB })
        assertFalse(availableProjectsSecondWorld.any { it.id == projectC })
        assertFalse(availableProjectsSecondWorld.any { it.id == projectD })
    }


    private fun createTestWorld(name: String, description: String): Int = runBlocking {
        val result = CreateWorldStep(user).process(
            CreateWorldInput(
                name = name,
                description = description,
                version = MinecraftVersion.fromString("1.20.1")
            )
        )
        when (result) {
            is Result.Success -> result.value
            is Result.Failure -> throw IllegalStateException("Failed to create test world '$name': $result")
        }
    }

    private fun createTestProject(name: String, description: String, worldId: Int = this.worldId): Int = runBlocking {
        val result = CreateProjectStep(worldId).process(
            CreateProjectInput(
                name = name,
                description = description,
                type = ProjectType.BUILDING,
                version = MinecraftVersion.fromString("1.20.1")
            )
        )
        when (result) {
            is Result.Success -> result.value
            is Result.Failure -> throw IllegalStateException("Failed to create test project '$name': $result")
        }
    }

    private fun createProjectDependency(projectId: Int, dependsOnProjectId: Int) = runBlocking {
        val result = DatabaseSteps.update<Unit, DatabaseFailure>(
            SafeSQL.insert(
                """
                INSERT INTO project_dependencies (project_id, depends_on_project_id)
                VALUES (?, ?)
                """.trimIndent()
            ),
            parameterSetter = { statement, _ ->
                statement.setInt(1, projectId)
                statement.setInt(2, dependsOnProjectId)
            },
            errorMapper = { it }
        ).process(Unit)

        when (result) {
            is Result.Failure -> throw IllegalStateException("Failed to create dependency: $result")
            is Result.Success -> Unit
        }
    }

    private fun deleteProject(projectId: Int) = runBlocking {
        DatabaseSteps.update<Unit, DatabaseFailure>(
            SafeSQL.delete("DELETE FROM projects WHERE id = ?"),
            parameterSetter = { statement, _ ->
                statement.setInt(1, projectId)
            },
            errorMapper = { it }
        ).process(Unit)
    }
}
