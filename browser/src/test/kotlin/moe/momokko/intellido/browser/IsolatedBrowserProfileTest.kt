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
    fun `anonymous profile keeps disk cookies so Cloudflare clearance can survive restarts`() {
        val leftover = root.resolve("jcef").resolve("stable").resolve("profile").resolve("Cookies")
        Files.createDirectories(leftover.parent)
        Files.writeString(leftover, "cookie=secret")

        val profile = IsolatedBrowserProfiles.prepareAnonymous(root, ReleaseChannel.STABLE)
        val settings = IsolatedBrowserProfiles.settingsFor(profile)

        assertEquals(BrowserPersistence.ProcessOnly, profile.persistence)
        assertTrue(settings.persistSessionCookies)
        assertTrue(settings.cachePath.startsWith(root))
        assertFalse(IsolatedBrowserProfiles.isForbiddenSystemBrowserPath(settings.cachePath))
        assertTrue(Files.exists(leftover))
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

    @Test
    fun `anonymous prepare does not wipe jcef_cache so clearance can persist`() {
        val leftover = root.resolve(IsolatedBrowserProfiles.DEFAULT_CACHE_DIR).resolve("Cookies")
        Files.createDirectories(leftover.parent)
        Files.writeString(leftover, "cookie=secret")
        IsolatedBrowserProfiles.prepareAnonymous(root, ReleaseChannel.STABLE)
        assertTrue(Files.exists(leftover))
        assertTrue(IsolatedBrowserProfiles.CEF_SWITCHES.contains("--disable-extensions"))
    }

    @Test
    fun `remembered profile keeps cookies and persists them to disk`() {
        val leftover = root.resolve("jcef").resolve("stable").resolve("profile").resolve("Cookies")
        Files.createDirectories(leftover.parent)
        Files.writeString(leftover, "cookie=secret")

        val profile = IsolatedBrowserProfiles.prepareRemembered(root, ReleaseChannel.STABLE)
        val settings = IsolatedBrowserProfiles.settingsFor(profile)

        assertEquals(BrowserPersistence.OsProtected, profile.persistence)
        assertTrue(settings.persistSessionCookies)
        assertTrue(Files.exists(leftover))
        IsolatedBrowserProfiles.signOut(profile)
        assertTrue(Files.notExists(leftover))
        assertEquals(BrowserPersistence.ProcessOnly, IsolatedBrowserProfiles.signOut(profile).persistence)
    }
}
