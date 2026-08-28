package moe.momokko.intellido.browser

import moe.momokko.intellido.platform.identity.ReleaseChannel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class IsolatedBrowserProfileTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `anonymous profile is process-only under IntelliDo and wipes leftover cookies`() {
        val leftover = root.resolve("jcef").resolve("stable").resolve("anonymous").resolve("Cookies")
        Files.createDirectories(leftover.parent)
        Files.writeString(leftover, "cookie=secret")

        val profile = IsolatedBrowserProfiles.prepareAnonymous(root, ReleaseChannel.STABLE)
        val settings = IsolatedBrowserProfiles.settingsFor(profile)

        assertEquals(BrowserPersistence.ProcessOnly, profile.persistence)
        assertFalse(settings.persistSessionCookies)
        assertTrue(settings.cachePath.startsWith(root))
        assertFalse(IsolatedBrowserProfiles.isForbiddenSystemBrowserPath(settings.cachePath))
        assertFalse(Files.exists(leftover))
        assertTrue(Files.isDirectory(settings.cachePath))
        assertFalse(settings.cachePath.toString().contains("Chrome", ignoreCase = true))
        assertFalse(settings.cachePath.toString().contains("Edge", ignoreCase = true))
        assertEquals("zh-CN", settings.locale)
        assertEquals("zh-CN,zh", settings.acceptLanguage)
        assertEquals("ide.browser.jcef.cache.path", IsolatedBrowserProfiles.CACHE_PATH_PROPERTY)
        assertEquals(
            settings.cachePath.toAbsolutePath().toString(),
            IsolatedBrowserProfiles.jvmOverrides(settings)[IsolatedBrowserProfiles.CACHE_PATH_PROPERTY],
        )
    }

    @Test
    fun `system Chrome user data directories are rejected`() {
        val chrome = root.resolve("Google").resolve("Chrome").resolve("User Data")
        Files.createDirectories(chrome)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            IsolatedBrowserProfiles.prepareAnonymous(chrome, ReleaseChannel.NIGHTLY)
        }
        assertTrue(IsolatedBrowserProfiles.isForbiddenSystemBrowserPath(chrome))
    }
}
