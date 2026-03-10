package app.mcorg.pipeline.project

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.pipeline.Result
import app.mcorg.pipeline.project.commonsteps.GetViewPreferenceInput
import app.mcorg.pipeline.project.commonsteps.GetViewPreferenceStep
import app.mcorg.pipeline.project.commonsteps.SetViewPreferenceInput
import app.mcorg.pipeline.project.commonsteps.SetViewPreferenceStep
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
import kotlin.test.assertTrue

@Tag("database")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class ViewPreferenceStepTest : WithUser() {

    private var projectId: Int = 0

    @BeforeAll
    fun setup() {
        val worldResult = runBlocking {
            CreateWorldStep(user).process(
                CreateWorldInput(
                    name = "ViewPref Test World",
                    description = "test",
                    version = MinecraftVersion.fromString("1.21.4")
                )
            )
        }
        assertTrue(worldResult is Result.Success)
        val worldId = (worldResult as Result.Success).value

        val projectResult = runBlocking {
            app.mcorg.pipeline.DatabaseSteps.update<Unit>(
                sql = app.mcorg.pipeline.SafeSQL.insert("INSERT INTO projects (name, world_id, type, stage) VALUES ('Test Project', ?, 'build', 'planning') RETURNING id"),
                parameterSetter = { stmt, _ -> stmt.setInt(1, worldId) }
            ).process(Unit)
        }
        assertTrue(projectResult is Result.Success)
        projectId = (projectResult as Result.Success).value
    }

    @Test
    fun `GetViewPreferenceStep should return execute when no row exists`() {
        val result = runBlocking {
            GetViewPreferenceStep.process(GetViewPreferenceInput(user.id, projectId))
        }
        assertTrue(result is Result.Success)
        assertEquals("execute", (result as Result.Success).value)
    }

    @Test
    fun `SetViewPreferenceStep should insert preference successfully`() {
        val setResult = runBlocking {
            SetViewPreferenceStep.process(SetViewPreferenceInput(user.id, projectId, "plan"))
        }
        assertTrue(setResult is Result.Success)

        val getResult = runBlocking {
            GetViewPreferenceStep.process(GetViewPreferenceInput(user.id, projectId))
        }
        assertTrue(getResult is Result.Success)
        assertEquals("plan", (getResult as Result.Success).value)
    }

    @Test
    fun `SetViewPreferenceStep should upsert without duplicate key error`() {
        val first = runBlocking {
            SetViewPreferenceStep.process(SetViewPreferenceInput(user.id, projectId, "plan"))
        }
        assertTrue(first is Result.Success)

        val second = runBlocking {
            SetViewPreferenceStep.process(SetViewPreferenceInput(user.id, projectId, "execute"))
        }
        assertTrue(second is Result.Success)

        val getResult = runBlocking {
            GetViewPreferenceStep.process(GetViewPreferenceInput(user.id, projectId))
        }
        assertTrue(getResult is Result.Success)
        assertEquals("execute", (getResult as Result.Success).value)
    }

    @Test
    fun `SetViewPreferenceStep should fail for invalid preference`() {
        val result = runBlocking {
            SetViewPreferenceStep.process(SetViewPreferenceInput(user.id, projectId, "invalid"))
        }
        assertTrue(result is Result.Failure)
    }
}
