package moe.momokko.intellido.platform.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ProductIdentityTest {
    @Test
    fun `stable and nightly use distinct names directories and mutex selectors`() {
        val stable = ProductIdentity(ReleaseChannel.STABLE, "0.1.0")
        val nightly = ProductIdentity(ReleaseChannel.NIGHTLY, "0.1.0")

        assertEquals("IntelliDo", stable.visibleProductName)
        assertEquals("IntelliDo Nightly", nightly.visibleProductName)
        assertEquals("IntelliDo", stable.pathsSelector)
        assertEquals("IntelliDoNightly", nightly.pathsSelector)
        assertNotEquals(stable.pathsSelector, nightly.pathsSelector)
        assertEquals("Momokko", stable.vendorDirectoryName)
        assertEquals(stable.vendorDirectoryName, nightly.vendorDirectoryName)
        assertFalse(stable.vendorDirectoryName.any { it.isWhitespace() })
        assertEquals("branding/stable", stable.brandingDirectory)
        assertEquals("branding/nightly", nightly.brandingDirectory)
    }

    @Test
    fun `nightly build info uses the documented version date and commit shape`() {
        val nightly = ProductIdentity(ReleaseChannel.NIGHTLY, "0.1.0")
        assertEquals(
            "0.1.0-nightly.20260822+abc1234",
            nightly.buildInfo("20260822", "abc1234"),
        )
    }

    @Test
    fun `controlled request identity names the product without install or account ids`() {
        val userAgent = ProductIdentity(ReleaseChannel.STABLE, "0.1.0").requestIdentity().userAgent()
        assertEquals("IntelliDo/0.1.0 (+https://github.com/Momoko-Ayase/IntelliDo)", userAgent)
        assertFalse(userAgent.contains("install", ignoreCase = true))
        assertFalse(userAgent.contains("account", ignoreCase = true))
        assertFalse(userAgent.contains("telemetry", ignoreCase = true))
    }
}
