package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.model.project.ProjectStage
import app.mcorg.pipeline.DatabaseSteps
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.SafeSQL
import app.mcorg.pipeline.project.commonsteps.GetProjectListStep
import app.mcorg.pipeline.world.CreateWorldInput
import app.mcorg.pipeline.world.CreateWorldStep
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class GetProjectListStepTest : WithUser() {

    private var worldId: Int = 0

    @BeforeAll
    fun setup() {
        val result = runBlocking {
            CreateWorldStep(user).process(
                CreateWorldInput(
                    name = "ProjectList StepTest World",
                    description = "test",
                    version = MinecraftVersion.fromString("1.21.4")
                )
            )
        }
        worldId = (result as Result.Success).value
    }

    @Test
    fun `returns empty list when world has no projects`() {
        val result = runBlocking {
            GetProjectListStep(worldId).process(Unit)
        }
        assertIs<Result.Success<*>>(result)
        assertTrue((result as Result.Success).value.isEmpty())
    }

    @Test
    fun `returns project list items when projects exist`() {
        val projectId = createProject("List Item Test Project")

        val result = runBlocking {
            GetProjectListStep(worldId).process(Unit)
        }

        assertIs<Result.Success<*>>(result)
        val projects = (result as Result.Success).value
        val project = projects.find { it.id == projectId }
        assertTrue(project != null, "Created project should be in the list")
        assertEquals("List Item Test Project", project.name)
        assertEquals(ProjectStage.IDEA, project.stage)
        assertEquals(0, project.tasksTotal)
        assertEquals(0, project.tasksDone)
        assertEquals(0, project.resourcesRequired)
        assertEquals(0, project.resourcesGathered)
        assertEquals(null, project.nextTaskName)
    }

    @Test
    fun `populates next task name from action_task`() {
        val projectId = createProject("NextTask Test Project")
        createActionTask(projectId, "My First Task")

        val result = runBlocking {
            GetProjectListStep(worldId).process(Unit)
        }

        assertIs<Result.Success<*>>(result)
        val projects = (result as Result.Success).value
        val project = projects.find { it.id == projectId }
        assertTrue(project != null)
        assertEquals("My First Task", project.nextTaskName)
        assertEquals(1, project.tasksTotal)
        assertEquals(0, project.tasksDone)
    }

    @Test
    fun `completed projects are sorted to bottom`() {
        val idleProjectId = createProject("Idle Project (IDEA stage)")
        val completedProjectId = createProject("Completed Project")
        markProjectCompleted(completedProjectId)

        val result = runBlocking {
            GetProjectListStep(worldId).process(Unit)
        }

        assertIs<Result.Success<*>>(result)
        val projects = (result as Result.Success).value
        val idleIndex = projects.indexOfFirst { it.id == idleProjectId }
        val completedIndex = projects.indexOfFirst { it.id == completedProjectId }
        assertTrue(idleIndex >= 0 && completedIndex >= 0)
        assertTrue(idleIndex < completedIndex, "Idle project should appear before completed project")
    }

    private fun createProject(name: String): Int = runBlocking {
        val result = DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO projects (name, world_id, description, type, stage, location_x, location_y, location_z, location_dimension) " +
                        "VALUES (?, ?, '', 'BUILDING', 'IDEA', 0, 0, 0, 'OVERWORLD') RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setString(1, name)
                stmt.setInt(2, worldId)
            }
        ).process(Unit)
        (result as Result.Success).value
    }

    private fun createActionTask(projectId: Int, taskName: String): Unit = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.insert(
                "INSERT INTO action_task (project_id, name, completed) VALUES (?, ?, false) RETURNING id"
            ),
            parameterSetter = { stmt, _ ->
                stmt.setInt(1, projectId)
                stmt.setString(2, taskName)
            }
        ).process(Unit)
    }

    private fun markProjectCompleted(projectId: Int): Unit = runBlocking {
        DatabaseSteps.update<Unit>(
            sql = SafeSQL.update("UPDATE projects SET stage = 'COMPLETED' WHERE id = ?"),
            parameterSetter = { stmt, _ -> stmt.setInt(1, projectId) }
        ).process(Unit)
    }
}
