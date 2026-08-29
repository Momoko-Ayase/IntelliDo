package moe.momokko.intellido.ui.jcef

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SignInProbeTest {
    @Test
    fun `auth pages do not finish sign-in even if a user blob is present`() {
        val payload =
            """{"href":"https://linux.do/login","user":{"username":"helper","trust_level":2,"id":2}}"""
        assertNull(SignInProbe.sessionFromPayload(payload))
    }

    @Test
    fun `home after login becomes a signed-in session`() {
        val payload =
            """{"href":"https://linux.do/","user":{"username":"helper","trust_level":2,"id":2,"name":"助手","avatar_template":"/user_avatar/linux.do/helper/{size}/2.png"}}"""
        val session = SignInProbe.sessionFromPayload(payload) as moe.momokko.intellido.domain.session.MemberSession.SignedIn
        assertEquals("helper", session.username)
        assertEquals(2, session.trustLevel)
        assertEquals("助手", session.name)
    }

    @Test
    fun `anonymous current user on home is not signed in`() {
        assertNull(SignInProbe.sessionFromPayload("""{"href":"https://linux.do/","user":null}"""))
    }
}
