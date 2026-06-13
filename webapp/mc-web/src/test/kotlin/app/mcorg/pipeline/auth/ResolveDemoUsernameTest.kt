package app.mcorg.pipeline.auth

import app.mcorg.domain.Local
import app.mcorg.domain.Production
import app.mcorg.domain.Test as TestEnv
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResolveDemoUsernameTest {

    private val default = "evegul"

    @Test
    fun `allowlisted persona is honoured in non-production`() {
        assertEquals("alex", resolveDemoUsername(TestEnv, "alex", default))
        assertEquals("steve", resolveDemoUsername(Local, "steve", default))
        assertEquals("lilpebblez", resolveDemoUsername(TestEnv, "lilpebblez", default))
    }

    @Test
    fun `default demo user is always allowed`() {
        assertEquals(default, resolveDemoUsername(TestEnv, default, default))
    }

    @Test
    fun `unknown username falls back to the default user`() {
        assertEquals(default, resolveDemoUsername(TestEnv, "attacker", default))
        assertEquals(default, resolveDemoUsername(TestEnv, "superadmin", default))
    }

    @Test
    fun `null or blank falls back to the default user`() {
        assertEquals(default, resolveDemoUsername(TestEnv, null, default))
        assertEquals(default, resolveDemoUsername(TestEnv, "   ", default))
    }

    @Test
    fun `random yields a generated demo user`() {
        assertTrue(resolveDemoUsername(TestEnv, "random", default).startsWith("DemoUser_"))
    }

    @Test
    fun `production ignores the requested username`() {
        assertEquals(default, resolveDemoUsername(Production, "alex", default))
        assertEquals(default, resolveDemoUsername(Production, "random", default))
        assertEquals(default, resolveDemoUsername(Production, "attacker", default))
    }
}
