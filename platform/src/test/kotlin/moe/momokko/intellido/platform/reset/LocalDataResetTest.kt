package moe.momokko.intellido.platform.reset

import moe.momokko.intellido.platform.i18n.FileLocalPreferenceStore
import moe.momokko.intellido.platform.i18n.InMemoryLocalPreferenceStore
import moe.momokko.intellido.platform.welcome.WelcomeVisibility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

class LocalDataResetTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `confirmation phrase is typed exactly and never touches downloads`() {
        assertEquals("清除所有本地数据", LocalDataReset.confirmPhrase(Locale.SIMPLIFIED_CHINESE))
        assertEquals("delete all local data", LocalDataReset.confirmPhrase(Locale.ENGLISH))
        assertTrue(LocalDataReset.matches("清除所有本地数据", Locale.SIMPLIFIED_CHINESE))
        assertFalse(LocalDataReset.matches("清除", Locale.SIMPLIFIED_CHINESE))
        assertFalse(LocalDataReset.matches("delete all local data", Locale.SIMPLIFIED_CHINESE))
    }

    @Test
    fun `preview lists managed categories and apply wipes them`() {
        val profile = root.resolve("jcef")
        val prefs = root.resolve("application.properties")
        val workspace = root.resolve("workspace")
        val cache = root.resolve("intellido-media")
        val logs = root.resolve("log")
        val downloads = root.resolve("Downloads").resolve("notes.pdf")
        Files.createDirectories(profile)
        Files.writeString(profile.resolve("Cookies"), "cookie")
        Files.createDirectories(workspace)
        Files.writeString(workspace.resolve("Home.intellido-home"), "home")
        Files.createDirectories(cache)
        Files.writeString(cache.resolve("a.bin"), "img")
        Files.createDirectories(logs)
        Files.writeString(logs.resolve("idea.log"), "log")
        Files.createDirectories(downloads.parent)
        Files.writeString(downloads, "keep")
        val store = FileLocalPreferenceStore(prefs)
        store.set(WelcomeVisibility.PREFERENCE_KEY, WelcomeVisibility.DISMISSED_VALUE)

        val preview = LocalDataReset.preview(profile, prefs, workspace, logs, listOf(cache))
        assertEquals(listOf("browser", "preferences", "workspace", "logs", "cache"), preview.map { it.id })
        assertTrue(preview.none { it.path.contains("Downloads") })

        LocalDataReset.apply(profile, prefs, workspace, logs, listOf(cache))
        assertTrue(Files.notExists(profile.resolve("Cookies")) || !Files.exists(profile) || Files.list(profile).use { it.count() } == 0L)
        assertTrue(Files.notExists(prefs))
        assertTrue(Files.notExists(workspace.resolve("Home.intellido-home")))
        assertTrue(Files.notExists(cache.resolve("a.bin")))
        assertTrue(Files.exists(downloads))
        val welcome = WelcomeVisibility(InMemoryLocalPreferenceStore())
        assertTrue(welcome.shouldShow())
    }
}
