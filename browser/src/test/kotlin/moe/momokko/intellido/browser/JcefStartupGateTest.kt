package moe.momokko.intellido.browser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JcefStartupGateTest {
    @Test
    fun `available JCEF continues into the native app`() {
        val decision = JcefStartupGate { JcefProbeResult.Available }.decide()
        assertEquals(JcefStartupDecision.ContinueWithJcef, decision)
    }

    @Test
    fun `JCEF failure opens recovery and does not enable an HTTP fallback`() {
        val diagnostics = JcefDiagnostics.capture(
            reason = "cookie=secret; token=abc failed to start",
            jcefReportedSupported = false,
        )
        val decision = JcefStartupGate { JcefProbeResult.Unavailable(diagnostics) }.decide()
        val recovery = assertInstanceOf(JcefStartupDecision.ShowRecovery::class.java, decision)
        assertFalse(recovery.diagnostics.copyableText().contains("secret"))
        assertFalse(recovery.diagnostics.copyableText().contains("abc"))
        assertTrue(recovery.diagnostics.copyableText().contains("<redacted>"))
        assertEquals(
            listOf(
                "intellido.jcef.retry",
                "intellido.jcef.openRepairGuide",
                "intellido.jcef.copyDiagnostics",
                "intellido.exit",
            ),
            JcefRecoveryActions.all,
        )
        assertFalse(decision is JcefStartupDecision.ContinueWithJcef)
    }

    @Test
    fun `available JCEF with isolation failure still opens recovery without an HTTP fallback`() {
        val diagnostics = JcefDiagnostics.capture("isolated profile directory is not writable", false)
        val decision = JcefStartupGate(
            probe = { JcefProbeResult.Available },
            isolation = { JcefIsolationResult.Failed(diagnostics) },
        ).decide()
        assertInstanceOf(JcefStartupDecision.ShowRecovery::class.java, decision)
        assertFalse(decision is JcefStartupDecision.ContinueWithJcef)
    }

    @Test
    fun `missing JBCefApp class fails closed without an HTTP fallback`() {
        val diagnostics = JcefDiagnostics.capture(
            reason = "java.lang.NoClassDefFoundError: com/intellij/ui/jcef/JBCefApp",
            jcefReportedSupported = false,
        )
        val decision = JcefStartupGate { JcefProbeResult.Unavailable(diagnostics) }.decide()
        assertInstanceOf(JcefStartupDecision.ShowRecovery::class.java, decision)
        assertTrue(diagnostics.copyableText().contains("JBCefApp"))
        assertFalse(decision is JcefStartupDecision.ContinueWithJcef)
    }
}
