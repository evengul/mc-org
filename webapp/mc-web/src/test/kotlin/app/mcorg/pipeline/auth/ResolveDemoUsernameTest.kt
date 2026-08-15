package app.mcorg.pipeline.auth

import app.mcorg.domain.Local
import app.mcorg.domain.Production
import app.mcorg.domain.Test as TestEnv
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
        assertTrue(resolveDemoUsername(TestEnv, "random", default)!!.startsWith("DemoUser_"))
    }

    @Test
    fun `production ignores the requested username`() {
        assertEquals(default, resolveDemoUsername(Production, "alex", default))
        assertEquals(default, resolveDemoUsername(Production, "random", default))
        assertEquals(default, resolveDemoUsername(Production, "attacker", default))
    }

    // MCO-333: DEMO_USER lost its hardcoded "evegul" default, so "unset" is now reachable.
    // Demo sign-in must fail closed rather than invent an identity.

    @Test
    fun `an unset DEMO_USER yields null rather than a fallback identity`() {
        assertNull(resolveDemoUsername(Local, null, null))
        assertNull(resolveDemoUsername(TestEnv, null, null))
        assertNull(resolveDemoUsername(Production, null, null))
    }

    @Test
    fun `an unset DEMO_USER cannot be bypassed by asking for a persona`() {
        // The allowlisted personas are only reachable *alongside* a configured demo user.
        assertNull(resolveDemoUsername(TestEnv, "alex", null))
        assertNull(resolveDemoUsername(TestEnv, "attacker", null))
        assertNull(resolveDemoUsername(Production, "alex", null))
    }

    @Test
    fun `an unset DEMO_USER disables the random persona too`() {
        // "random" mints its own throwaway identity, so it would otherwise stay reachable and
        // keep the sign-in bypass alive on a deployment that never configured demo sign-in.
        assertNull(resolveDemoUsername(TestEnv, "random", null))
        assertNull(resolveDemoUsername(Local, "random", null))
    }
}
