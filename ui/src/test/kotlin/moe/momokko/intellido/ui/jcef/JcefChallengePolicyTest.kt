package moe.momokko.intellido.ui.jcef

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JcefChallengePolicyTest {
    @Test
    fun `sign-in or a recent success suppresses another challenge dialog`() {
        assertFalse(JcefChallengePolicy.shouldOpenDialog(signInOpen = true, lastSuccessAtMs = 0, nowMs = 10_000))
        assertFalse(
            JcefChallengePolicy.shouldOpenDialog(
                signInOpen = false,
                lastSuccessAtMs = 1_000,
                nowMs = 1_000 + JcefChallengePolicy.COOLDOWN_MS - 1,
            ),
        )
        assertTrue(
            JcefChallengePolicy.shouldOpenDialog(
                signInOpen = false,
                lastSuccessAtMs = 1_000,
                nowMs = 1_000 + JcefChallengePolicy.COOLDOWN_MS + 1,
            ),
        )
        assertTrue(JcefChallengePolicy.shouldOpenDialog(signInOpen = false, lastSuccessAtMs = 0, nowMs = 10_000))
    }

    @Test
    fun `challenge probe never treats the interstitial logo as a pass`() {
        val js = JcefChallengeDialog.PROBE_JS
        assertFalse(js.contains("site-text-logo"))
        assertFalse(js.contains("site-logo"))
        assertFalse(js.contains("main-outlet"))
        assertTrue(js.contains("/session/csrf"))
        assertTrue(js.contains("cf-mitigated"))
        assertTrue(js.contains("\"csrf\""))
        assertTrue(js.contains("hasTurnstile"))
    }
}
