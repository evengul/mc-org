package app.mcorg.pipeline.world

import app.mcorg.domain.model.minecraft.MinecraftVersion
import app.mcorg.domain.pipeline.Result
import app.mcorg.test.WithUser
import app.mcorg.test.postgres.DatabaseTestExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DatabaseTestExtension::class)
class CreateWorldStepTest : WithUser() {

    @Test
    fun `Should create world successfully`() {
        val now = System.nanoTime().toString()
        val result = createWorld(description = now)

        assertTrue(result is Result.Success)
        assertTrue {
            getWorlds().any { it.description == now }
        }
    }

    @Test
    fun `Should fail when user does not exist`() {
        val result = runBlocking {
            CreateWorldStep(user.copy(id = -1)).process(
                CreateWorldInput(
                    name = "My World",
                    description = "A cool world",
                    version = MinecraftVersion.fromString("1.16.5")
                )
            )
        }

        assertTrue(result is Result.Failure)
    }

    private fun createWorld(
        name: String = "My World",
        description: String = "A cool world",
        version: String = "1.16.5"
    ) = runBlocking {
        CreateWorldStep(user).process(
            CreateWorldInput(
                name = name,
                description = description,
                version = MinecraftVersion.fromString(version)
            )
        )
    }

    private fun getWorlds() = runBlocking {
        GetPermittedWorldsStep.process(GetPermittedWorldsInput(user.id, query = "")).getOrNull() ?: emptyList()
    }

}