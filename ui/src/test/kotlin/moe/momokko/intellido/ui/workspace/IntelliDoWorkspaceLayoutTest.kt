package moe.momokko.intellido.ui.workspace

import moe.momokko.intellido.platform.catalog.DirectoryKind
import moe.momokko.intellido.ui.directory.DirectoryFileEditorProvider
import moe.momokko.intellido.ui.directory.DirectoryFileType
import moe.momokko.intellido.ui.home.HomeFileEditorProvider
import moe.momokko.intellido.ui.home.HomeFileType
import moe.momokko.intellido.ui.topic.TopicFileEditorProvider
import moe.momokko.intellido.ui.topic.TopicFileType
import moe.momokko.intellido.ui.welcome.WelcomeFileEditorProvider
import moe.momokko.intellido.platform.splash.SplashArtwork
import moe.momokko.intellido.ui.welcome.WelcomeFileType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.DataInputStream

class IntelliDoWorkspaceLayoutTest {
    @Test
    fun `workspace uses a hidden Home file instead of a custom main window`() {
        assertEquals("workspace", IntelliDoWorkspaceLayout.DIRECTORY_NAME)
        assertEquals("Home.intellido-home", IntelliDoWorkspaceLayout.HOME_FILE_NAME)
        assertEquals("intellido-home", HomeFileType.EXTENSION)
        assertEquals("intellido-home", HomeFileType.INSTANCE.defaultExtension)
        assertEquals("intellido-home", HomeFileEditorProvider.EDITOR_TYPE_ID)
        assertTrue(HomeFileType.INSTANCE.isReadOnly)
    }

    @Test
    fun `welcome is a separate closeable file from the Home topic list`() {
        assertEquals("Welcome.intellido-welcome", IntelliDoWorkspaceLayout.WELCOME_FILE_NAME)
        assertEquals("intellido-welcome", WelcomeFileType.EXTENSION)
        assertEquals("intellido-welcome", WelcomeFileType.INSTANCE.defaultExtension)
        assertEquals("intellido-welcome", WelcomeFileEditorProvider.EDITOR_TYPE_ID)
        assertTrue(WelcomeFileType.INSTANCE.isReadOnly)
    }

    @Test
    fun `topic preview files are per-topic and distinct from Home`() {
        assertEquals("intellido-topic", IntelliDoWorkspaceLayout.TOPIC_EXTENSION)
        assertEquals("topic-101.intellido-topic", IntelliDoWorkspaceLayout.topicFileName(101))
        assertEquals("intellido-topic", TopicFileType.EXTENSION)
        assertEquals("intellido-topic", TopicFileEditorProvider.EDITOR_TYPE_ID)
        assertTrue(TopicFileType.INSTANCE.isReadOnly)
        assertTrue(IntelliDoWorkspaceLayout.topicFileName(101) != IntelliDoWorkspaceLayout.HOME_FILE_NAME)
        assertEquals("IntelliDoHome", HomeFileType.NAME)
        assertEquals("IntelliDoWelcome", WelcomeFileType.NAME)
        assertEquals("IntelliDoTopic", TopicFileType.NAME)
    }

    @Test
    fun `plugin descriptor fileType names match FileType getName`() {
        val xml = java.io.File("../src/main/resources/META-INF/plugin.xml").takeIf { it.isFile }
            ?: java.io.File("src/main/resources/META-INF/plugin.xml")
        val text = xml.readText()
        assertTrue(text.contains("name=\"IntelliDoHome\""))
        assertTrue(text.contains("name=\"IntelliDoWelcome\""))
        assertTrue(text.contains("name=\"IntelliDoTopic\""))
        assertTrue(text.contains("name=\"IntelliDoDirectory\""))
        assertTrue(text.contains("name=\"IntelliDoUser\""))
        assertTrue(text.contains("name=\"IntelliDoBrowse\""))
        assertTrue(text.contains("id=\"LINUX DO\""))
        assertTrue(text.contains("icon=\"/icons/linuxdo.svg\""))
        assertTrue(text.contains("searchEverywhereContributor"))
        assertTrue(text.contains("CommunityNavToolWindowFactory"))
        assertTrue(text.contains("IntelliDoActionConfigurationCustomizer"))
        assertTrue(text.contains("IntelliDoMenuSurfaceCustomizer"))
    }

    @Test
    fun `windows installer is per-user and does not register file types`() {
        val iss = java.io.File("../packaging/windows/IntelliDo.iss").takeIf { it.isFile }
            ?: java.io.File("packaging/windows/IntelliDo.iss")
        val text = iss.readText()
        assertTrue(iss.isFile, iss.absolutePath)
        assertTrue(text.contains("PrivilegesRequired=lowest"))
        assertTrue(text.contains("DefaultDirName={localappdata}\\Programs\\IntelliDo"))
        assertTrue(text.contains("ChangesAssociations=no"))
        assertTrue(text.contains("intellido64.exe"))
        assertTrue(text.contains("Momokko\\IntelliDo\\splash"))
        val notice = java.io.File("../packaging/windows/INSTALL-NOTICE.zh.txt").takeIf { it.isFile }
            ?: java.io.File("packaging/windows/INSTALL-NOTICE.zh.txt")
        assertTrue(notice.readText().contains("非官方"))
        val appInfo = java.io.File("../src/main/resources/idea/IntelliDoApplicationInfo.xml").takeIf { it.isFile }
            ?: java.io.File("src/main/resources/idea/IntelliDoApplicationInfo.xml")
        val appInfoText = appInfo.readText()
        assertTrue(appInfoText.contains("<essential-plugin>moe.momokko.intellido</essential-plugin>"))
        assertTrue(appInfoText.contains("date=\"@build.date@\""))
        val registry = java.io.File("../packaging/windows/default-config/early-access-registry.txt").takeIf { it.isFile }
            ?: java.io.File("packaging/windows/default-config/early-access-registry.txt")
        val registryText = registry.readText()
        assertTrue(registryText.contains("i18n.locale"))
        assertTrue(registryText.contains("zh-CN"))
        val localeXml = java.io.File("../packaging/windows/default-config/options/ide.general.xml").takeIf { it.isFile }
            ?: java.io.File("packaging/windows/default-config/options/ide.general.xml")
        assertTrue(localeXml.readText().contains("selectedLocale\" value=\"zh-CN\""))
        assertTrue(text.contains("userappdata}\\Momokko\\IntelliDo"))
    }

    @Test
    fun `product splash window is 800 by 450 not the 2048 master`() {
        assertEquals(800, SplashArtwork.WINDOW_WIDTH)
        assertEquals(450, SplashArtwork.WINDOW_HEIGHT)
        val splash = java.io.File("../artwork/final/splash-window.png").takeIf { it.isFile }
            ?: java.io.File("artwork/final/splash-window.png")
        assertTrue(splash.isFile, splash.absolutePath)
        DataInputStream(splash.inputStream()).use { input ->
            input.skipBytes(16)
            assertEquals(800, input.readInt())
            assertEquals(450, input.readInt())
        }
        val startup = java.io.File("../ui/src/main/kotlin/moe/momokko/intellido/ui/startup/IntelliDoStartup.kt").takeIf { it.isFile }
            ?: java.io.File("src/main/kotlin/moe/momokko/intellido/ui/startup/IntelliDoStartup.kt")
        assertFalse(
            startup.readText().contains("IntelliDoSplashWindow"),
            "A second splash after the main window must not come back",
        )
    }

    @Test
    fun `directory files are distinct from Home and topics`() {
        assertEquals("intellido-directory", IntelliDoWorkspaceLayout.DIRECTORY_EXTENSION)
        assertEquals("categories.intellido-directory", DirectoryKind.CATEGORIES.fileName)
        assertEquals("intellido-directory", DirectoryFileType.EXTENSION)
        assertEquals("intellido-directory", DirectoryFileEditorProvider.EDITOR_TYPE_ID)
        assertTrue(DirectoryFileType.INSTANCE.isReadOnly)
    }

    @Test
    fun `user profile files are per-username`() {
        assertEquals("intellido-user", IntelliDoWorkspaceLayout.USER_EXTENSION)
        assertEquals("user-helper.intellido-user", IntelliDoWorkspaceLayout.userFileName("helper"))
        assertEquals("helper", IntelliDoWorkspaceLayout.usernameFrom("user-helper.intellido-user"))
        assertEquals("intellido-user", moe.momokko.intellido.ui.profile.UserFileType.EXTENSION)
        assertEquals("intellido-user", moe.momokko.intellido.ui.profile.UserFileEditorProvider.EDITOR_TYPE_ID)
    }

    @Test
    fun `crafted usernames cannot escape the workspace directory`() {
        val traversal = IntelliDoWorkspaceLayout.userFileName("../../evil")
        assertFalse(traversal.contains('/'), traversal)
        assertFalse(traversal.contains('\\'), traversal)
        assertFalse(traversal.contains(".."), traversal)
        assertEquals("../../evil", IntelliDoWorkspaceLayout.usernameFrom(traversal))

        val windowsReserved = IntelliDoWorkspaceLayout.userFileName("a:b*c?d\"e<f>g|h")
        listOf(':', '*', '?', '"', '<', '>', '|').forEach { bad ->
            assertFalse(windowsReserved.contains(bad), "$bad survived in $windowsReserved")
        }
        assertEquals("a:b*c?d\"e<f>g|h", IntelliDoWorkspaceLayout.usernameFrom(windowsReserved))
    }

    @Test
    fun `unicode usernames survive the filename round trip`() {
        val name = "码农_01"
        val file = IntelliDoWorkspaceLayout.userFileName(name)
        assertTrue(file.endsWith(".intellido-user"), file)
        assertEquals(name, IntelliDoWorkspaceLayout.usernameFrom(file))
    }

    @Test
    fun `browse files are per url and distinct from topics`() {
        assertEquals("intellido-browse", IntelliDoWorkspaceLayout.BROWSE_EXTENSION)
        assertTrue(IntelliDoWorkspaceLayout.browseFileName("https://connect.linux.do").endsWith(".intellido-browse"))
        assertEquals("intellido-browse", moe.momokko.intellido.ui.browse.BrowseFileType.EXTENSION)
        assertEquals("intellido-browse", moe.momokko.intellido.ui.browse.BrowseFileEditorProvider.EDITOR_TYPE_ID)
        assertTrue(moe.momokko.intellido.ui.browse.BrowseFileType.INSTANCE.isReadOnly)
    }
}
