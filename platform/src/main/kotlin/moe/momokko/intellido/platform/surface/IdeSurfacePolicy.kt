package moe.momokko.intellido.platform.surface

import moe.momokko.intellido.domain.session.MemberSession
import moe.momokko.intellido.platform.nav.CommunityNavModel

/**
 * Which IntelliJ bundled capabilities IntelliDo ships. Programming, VCS, build,
 * run/debug, terminal, database, AI, and remote-dev plugins are omitted from
 * the product rather than left installed and disabled. IntelliDo ApplicationInfo
 * does not mark Java as essential, so those plugins are omitted with the rest.
 */
object IdeSurfacePolicy {
    const val KEPT_PLUGINS_RESOURCE: String = "/ide/kept-plugins.txt"
    const val DISABLED_PLUGINS_RESOURCE: String = "/ide/disabled-plugins.txt"

    val requiredPluginIds: Set<String> = setOf(
        "com.intellij",
        "com.intellij.modules.jcef",
        "org.intellij.plugins.markdown",
        "com.intellij.zh",
        "com.intellij.ja",
        "com.intellij.ko",
        "com.intellij.platform.images",
        "moe.momokko.intellido",
    )

    val programmingMenuGroupIds: List<String> = listOf(
        "CodeMenu",
        "AnalyzeMenu",
        "RefactoringMenu",
        "BuildMenu",
        "RunMenu",
        "GoToCodeGroup",
        "GenerateGroup",
        "NewGroup",
        "VcsGroup",
        "VcsGroups",
        "Vcs.MainMenu",
        "Git.Menu",
        "Git.MainMenu",
        "ToolsMenu",
        "NavBarVcsGroup",
        "SegmentedVcsActionsBarGroup",
        "VcsToolbarActions",
        "VcsNavBarToolbarActions",
        "ToolbarRunGroup",
        "RunToolbarMainActionGroup",
        "ExecutionTargetsToolbarGroup",
    )

    val toolbarGroupIdsToStrip: List<String> = listOf(
        "MainToolbarRight",
        "MainToolbarLeft",
        "MainToolbarCenter",
        "MainToolbarNewUI",
        "MainToolBar",
        "MainToolbarQuickActions.Run",
    )

    val programmingActionIds: List<String> = listOf(
        "NewProject",
        "NewModule",
        "NewDir",
        "NewFile",
        "NewClass",
        "NewScratchFile",
        "NewElement",
        "OpenFile",
        "OpenModuleSettings",
        "CloseProject",
        "RevealFile",
        "ShowProjectStructureSettings",
        "WelcomeScreen.CreateNewProject",
        "WelcomeScreen.OpenProject",
        "CompileDirty",
        "Compile",
        "CompileProject",
        "BuildProject",
        "Rebuild",
        "Run",
        "Debug",
        "ChooseRunConfiguration",
        "ChooseDebugConfiguration",
        "editRunConfigurations",
        "NewUiRunWidget",
        "RedesignedRunConfigurationSelector",
        "RunToolbarWidgetAction",
        "MoreRunToolbarActions",
        "AllRunConfigurationsToggle",
        "CreateRunConfiguration",
        "CreateNewRunConfiguration",
        "main.toolbar.Project",
        "SegmentedVcsControlAction",
        "VcsToolbarLabelAction",
        "Stop",
        "Pause",
        "Resume",
        "StepOver",
        "StepInto",
        "StepOut",
        "RunClass",
        "DebugClass",
        "Generate",
        "OverrideMethods",
        "ImplementMethods",
        "InspectCode",
        "ActivateProjectToolWindow",
        "ActivateTerminalToolWindow",
        "ActivateRunToolWindow",
        "ActivateDebugToolWindow",
        "ActivateBuildToolWindow",
        "ActivateTODOToolWindow",
        "ActivateStructureToolWindow",
        "ActivateVersionControlToolWindow",
        "Git.Clone",
        "Vcs.Import",
        "CheckinProject",
        "UpdateProject",
        "ViewNavigationBar",
        "ShowNavBar",
        "NavBar",
    )

    val trialAndLicenseActionIds: List<String> = listOf(
        "TrialStateWidget",
        "StartTrial",
        "UnlockSubscription",
        "CancelProTrial",
    )

    const val hideNavigationBar: Boolean = true

    /**
     * Toolbar/trial actions that can be unregistered as stubs. Menu groups such as
     * NewGroup stay registered because wrappers like WeighingNewActionGroup require
     * a non-null delegate; those groups are removed from MainMenu instead.
     */
    fun actionsUnregisteredAtStartup(): List<String> =
        (trialAndLicenseActionIds + listOf(
            "NewUiRunWidget",
            "RedesignedRunConfigurationSelector",
            "RunToolbarWidgetAction",
            "main.toolbar.Project",
            "SegmentedVcsControlAction",
            "VcsToolbarLabelAction",
        )).distinct()


    fun bundledKeepPluginIds(): Set<String> {
        val raw = IdeSurfacePolicy::class.java.getResourceAsStream(KEPT_PLUGINS_RESOURCE)
            ?.bufferedReader()
            ?.use { it.readLines() }
            ?: emptyList()
        return raw
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()
    }

    fun keptPluginIds(): Set<String> = requiredPluginIds + bundledKeepPluginIds()

    fun shouldKeepPlugin(id: String): Boolean = id in keptPluginIds()

    fun disabledPluginIds(): Set<String> {
        val raw = IdeSurfacePolicy::class.java.getResourceAsStream(DISABLED_PLUGINS_RESOURCE)
            ?.bufferedReader()
            ?.use { it.readLines() }
            ?: emptyList()
        return raw
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()
    }

    fun pluginDirectoriesToKeep(discoveredIdByDirectory: Map<String, String>): Set<String> {
        val keep = keptPluginIds()
        return discoveredIdByDirectory
            .filter { (_, id) -> id in keep }
            .keys
    }

    val programmingToolWindowIds: List<String> = listOf(
        "Project",
        "Commit",
        "Find",
        "Run",
        "Debug",
        "Hierarchy",
        "Structure",
        "Problems View",
        "Version Control",
        "TODO",
        "Services",
        "Terminal",
        "Database",
        "Favorites",
        "Bookmarks",
        "Ant",
        "Commander",
        "Messages",
        "Inspection Results",
        "Documentation",
        "Preview",
        "Endpoints",
        "Time Tracking",
        "Duplicates",
        "Extract Method",
        "Module Dependencies",
        "Dependency Viewer",
        "Dependencies",
        "Build",
        "Coverage",
        "Profiler",
        "Learn",
        "Pull Requests",
        "Gradle",
        "Maven",
    )

    fun shouldKeepToolWindow(
        id: String,
        session: MemberSession = MemberSession.Anonymous,
    ): Boolean {
        if (id == CommunityNavModel.TOOL_WINDOW_ID) {
            return true
        }
        if (!(id.startsWith("LINUX DO") || id.startsWith("IntelliDo"))) {
            return false
        }
        return session !is MemberSession.Anonymous
    }
}
