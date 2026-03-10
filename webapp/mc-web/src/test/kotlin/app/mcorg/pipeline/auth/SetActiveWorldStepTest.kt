package app.mcorg.pipeline.auth

import app.mcorg.pipeline.auth.commonsteps.SetActiveWorldInput
import app.mcorg.pipeline.auth.commonsteps.SetActiveWorldStep
import app.mcorg.test.fixtures.TestDataFactory
import app.mcorg.pipeline.TestUtils
import com.auth0.jwt.JWT
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SetActiveWorldStepTest {

    @Test
    fun `should create token with activeWorldId set`() {
        val profile = TestDataFactory.createTestTokenProfile(id = 1, activeWorldId = null)
        val step = SetActiveWorldStep()

        val result = TestUtils.executeAndAssertSuccess(step, SetActiveWorldInput(profile, 42))

        assertNotNull(result)
        val decodedJWT = JWT.decode(result)
        assertEquals(42, decodedJWT.getClaim("active_world_id").asInt())
        assertEquals(1, decodedJWT.getClaim("sub").asInt())
    }

    @Test
    fun `should overwrite existing activeWorldId`() {
        val profile = TestDataFactory.createTestTokenProfile(id = 1, activeWorldId = 10)
        val step = SetActiveWorldStep()

        val result = TestUtils.executeAndAssertSuccess(step, SetActiveWorldInput(profile, 99))

        val decodedJWT = JWT.decode(result)
        assertEquals(99, decodedJWT.getClaim("active_world_id").asInt())
    }
}
