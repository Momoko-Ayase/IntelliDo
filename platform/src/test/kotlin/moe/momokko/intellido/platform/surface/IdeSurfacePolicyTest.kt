package moe.momokko.intellido.platform.surface

import moe.momokko.intellido.domain.session.MemberSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IdeSurfacePolicyTest {
    @Test
    fun `programming VCS build and terminal plugins are not shipped`() {
        assertFalse(IdeSurfacePolicy.shouldKeepPlugin("com.intellij.java"))
        assertFalse(IdeSurfacePolicy.shouldKeepPlugin("com.intellij.java.ide"))
        assertFalse(IdeSurfacePolicy.shouldKeepPlugin("org.jetbrains.kotlin"))
        assertFalse(IdeSurfacePolicy.shouldKeepPlugin("Git4Idea"))
        assertFalse(IdeSurfacePolicy.shouldKeepPlugin("org.jetbrains.plugins.github"))
        assertFalse(IdeSurfacePolicy.shouldKeepPlugin("org.jetbrains.idea.maven"))
        assertFalse(IdeSurfacePolicy.shouldKeepPlugin("org.jetbrains.plugins.gradle"))
        assertFalse(IdeSurfacePolicy.shouldKeepPlugin("org.jetbrains.plugins.terminal"))
        assertFalse(IdeSurfacePolicy.shouldKeepPlugin("com.intellij.database"))
        assertFalse(IdeSurfacePolicy.shouldKeepPlugin("com.intellij.mcpServer"))
        assertFalse(IdeSurfacePolicy.shouldKeepPlugin("com.intellij.settingsSync"))
        assertFalse(IdeSurfacePolicy.shouldKeepPlugin("intellij.indexing.shared.core"))
        assertFalse(IdeSurfacePolicy.shouldKeepPlugin("com.intellij.modules.ultimate"))
        assertFalse(IdeSurfacePolicy.shouldKeepPlugin("JavaScript"))
        assertFalse(IdeSurfacePolicy.shouldKeepPlugin("com.intellij.spring"))
    }

    @Test
    fun `shell JCEF markdown localization editor and spellcheck plugins stay`() {
        IdeSurfacePolicy.requiredPluginIds.forEach { id ->
            assertTrue(IdeSurfacePolicy.shouldKeepPlugin(id), "$id must remain shipped")
        }
        assertTrue(IdeSurfacePolicy.shouldKeepPlugin("tanvd.grazi"))
        assertTrue(IdeSurfacePolicy.shouldKeepPlugin("com.intellij.modules.json"))
        assertTrue(IdeSurfacePolicy.shouldKeepPlugin("org.jetbrains.plugins.yaml"))
        assertTrue(IdeSurfacePolicy.shouldKeepPlugin("com.intellij.properties"))
        assertTrue(IdeSurfacePolicy.shouldKeepPlugin("intellij.webp"))
        assertTrue(IdeSurfacePolicy.shouldKeepPlugin("com.intellij.platform.daemon"))
        assertTrue(IdeSurfacePolicy.shouldKeepPlugin("intellij.structureView.plugin"))
        assertTrue(IdeSurfacePolicy.shouldKeepPlugin("intellij.structuralSearch.plugin"))
        assertTrue(IdeSurfacePolicy.keptPluginIds().containsAll(IdeSurfacePolicy.requiredPluginIds))
    }

    @Test
    fun `plugin directories to keep follow the allowlist not a disable list`() {
        val kept = IdeSurfacePolicy.pluginDirectoriesToKeep(
            mapOf(
                "java" to "com.intellij.java",
                "markdown" to "org.intellij.plugins.markdown",
                "Kotlin" to "org.jetbrains.kotlin",
                "grazie" to "tanvd.grazi",
                "vcs-git" to "Git4Idea",
                "jcef-plugin" to "com.intellij.modules.jcef",
                "terminal" to "org.jetbrains.plugins.terminal",
            ),
        )
        assertEquals(setOf("markdown", "grazie", "jcef-plugin"), kept)
        assertFalse(IdeSurfacePolicy.disabledPluginIds().contains("com.intellij.java"))
        assertFalse(IdeSurfacePolicy.disabledPluginIds().contains("org.intellij.plugins.markdown"))
    }

    @Test
    fun `only IntelliDo and LINUX DO tool windows stay visible`() {
        val signedIn = MemberSession.SignedIn("helper", 2)
        assertTrue(IdeSurfacePolicy.shouldKeepToolWindow("LINUX DO Connect", signedIn))
        assertTrue(IdeSurfacePolicy.shouldKeepToolWindow("IntelliDo Drafts", signedIn))
        assertTrue(IdeSurfacePolicy.shouldKeepToolWindow("LINUX DO Chat", signedIn))
        assertTrue(IdeSurfacePolicy.shouldKeepToolWindow("LINUX DO", MemberSession.Anonymous))
        assertFalse(IdeSurfacePolicy.shouldKeepToolWindow("LINUX DO Connect", MemberSession.Anonymous))
        assertFalse(IdeSurfacePolicy.shouldKeepToolWindow("IntelliDo Notifications", MemberSession.Anonymous))
        assertFalse(IdeSurfacePolicy.shouldKeepToolWindow("Project"))
        assertFalse(IdeSurfacePolicy.shouldKeepToolWindow("Run"))
        assertFalse(IdeSurfacePolicy.shouldKeepToolWindow("Terminal"))
        assertFalse(IdeSurfacePolicy.shouldKeepToolWindow("Git"))
        assertFalse(IdeSurfacePolicy.shouldKeepToolWindow("Database"))
        assertFalse(IdeSurfacePolicy.shouldKeepToolWindow("Commit"))
        assertFalse(IdeSurfacePolicy.shouldKeepToolWindow("Find"))
        assertFalse(IdeSurfacePolicy.shouldKeepToolWindow("Debug"))
        assertFalse(IdeSurfacePolicy.shouldKeepToolWindow("Hierarchy"))
        assertTrue(IdeSurfacePolicy.programmingToolWindowIds.containsAll(listOf("Commit", "Find", "Run", "Debug", "Hierarchy")))
    }

    @Test
    fun `top-bar VCS and run-widget actions are treated as programming surface`() {
        assertTrue(IdeSurfacePolicy.programmingMenuGroupIds.contains("VcsGroups"))
        assertTrue(IdeSurfacePolicy.programmingActionIds.contains("NewUiRunWidget"))
        assertTrue(IdeSurfacePolicy.programmingActionIds.contains("RedesignedRunConfigurationSelector"))
        assertTrue(IdeSurfacePolicy.programmingActionIds.contains("editRunConfigurations"))
        assertTrue(IdeSurfacePolicy.toolbarGroupIdsToStrip.contains("MainToolbarRight"))
        assertTrue(IdeSurfacePolicy.programmingActionIds.contains("ViewNavigationBar"))
        assertTrue(IdeSurfacePolicy.hideNavigationBar)
        assertTrue(IdeSurfacePolicy.actionsUnregisteredAtStartup().contains("TrialStateWidget"))
        assertTrue(IdeSurfacePolicy.actionsUnregisteredAtStartup().contains("UnlockSubscription"))
        assertTrue(IdeSurfacePolicy.actionsUnregisteredAtStartup().contains("main.toolbar.Project"))
        assertFalse(IdeSurfacePolicy.actionsUnregisteredAtStartup().contains("NewGroup"))
        assertFalse(IdeSurfacePolicy.actionsUnregisteredAtStartup().contains("RunMenu"))
        assertTrue(IdeSurfacePolicy.programmingMenuGroupIds.contains("NewGroup"))
    }
}
