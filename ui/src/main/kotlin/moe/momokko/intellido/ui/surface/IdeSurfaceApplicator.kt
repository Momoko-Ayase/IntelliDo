package moe.momokko.intellido.ui.surface

import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import moe.momokko.intellido.domain.session.MemberSession
import moe.momokko.intellido.platform.surface.IdeSurfacePolicy
import moe.momokko.intellido.ui.startup.IntelliDoRuntime

object IdeSurfaceApplicator {
    private val logger = Logger.getInstance(IdeSurfaceApplicator::class.java)
    private val toolWindowListenerInstalled: Key<Boolean> = Key.create("IntelliDoToolWindowSurfaceListener")

    fun applyApplicationSurface(
        actionManager: ActionManager = ActionManager.getInstance(),
        unregisterWidgets: Boolean = true,
    ) {
        removeMainMenuGroups(actionManager, IdeSurfacePolicy.programmingMenuGroupIds)
        stripToolbarGroups(actionManager)
        if (unregisterWidgets) {
            unregisterActions(
                actionManager,
                IdeSurfacePolicy.actionsUnregisteredAtStartup() + listOf("ViewNavigationBar", "ShowNavBar"),
            )
        }
        if (IdeSurfacePolicy.hideNavigationBar) {
            runCatching {
                val settings = UISettings.getInstance()
                if (settings.showNavigationBar) {
                    settings.showNavigationBar = false
                    settings.fireUISettingsChanged()
                }
            }.onFailure { error -> logger.warn("Could not hide navigation bar", error) }
        }
    }

    fun applyProjectSurface(project: Project) {
        if (project.isDisposed) {
            return
        }
        applyApplicationSurface()
        suppressProgrammingToolWindows(project)
        listenForNewToolWindows(project)
    }

    private fun suppressProgrammingToolWindows(project: Project) {
        ToolWindowManager.getInstance(project).invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }
            val manager = ToolWindowManager.getInstance(project)
            (manager.toolWindowIds + IdeSurfacePolicy.programmingToolWindowIds).distinct().forEach { id ->
                suppressToolWindow(project, id)
            }
        }
    }

    private fun listenForNewToolWindows(project: Project) {
        if (project.getUserData(toolWindowListenerInstalled) == true) {
            return
        }
        project.putUserData(toolWindowListenerInstalled, true)
        project.messageBus.connect().subscribe(
            ToolWindowManagerListener.TOPIC,
            object : ToolWindowManagerListener {
                override fun toolWindowsRegistered(ids: List<String>, toolWindowManager: ToolWindowManager) {
                    ids.forEach { id -> suppressToolWindow(project, id) }
                }
            },
        )
    }

    private fun suppressToolWindow(project: Project, id: String) {
        if (project.isDisposed || IdeSurfacePolicy.shouldKeepToolWindow(id, currentSession())) {
            return
        }
        removeActivateToolWindowAction(id)
        val window = ToolWindowManager.getInstance(project).getToolWindow(id) ?: return
        if (window.isAvailable) {
            window.setAvailable(false)
        }
    }

    private fun removeActivateToolWindowAction(toolWindowId: String) {
        val manager = ActionManager.getInstance()
        val actionId = ActivateToolWindowAction.Manager.getActionIdForToolWindow(toolWindowId)
        val action = manager.getAction(actionId) ?: return
        val group = manager.getAction("ActivateToolWindowActions") as? DefaultActionGroup
        if (group != null) {
            runCatching { group.remove(action) }
        }
        if (actionId.startsWith("Activate") && actionId.endsWith("ToolWindow")) {
            runCatching { manager.unregisterAction(actionId) }
                .onFailure { error -> logger.warn("Could not unregister $actionId", error) }
        }
    }

    private fun currentSession(): MemberSession =
        runCatching { service<IntelliDoRuntime>().session }.getOrDefault(MemberSession.Anonymous)

    private fun unregisterActions(manager: ActionManager, actionIds: List<String>) {
        actionIds.forEach { id ->
            if (manager.getAction(id) != null) {
                runCatching { manager.unregisterAction(id) }
            }
        }
    }

    private fun removeMainMenuGroups(manager: ActionManager, groupIds: List<String>) {
        val mainMenu = manager.getAction("MainMenu") as? DefaultActionGroup ?: return
        groupIds.forEach { id ->
            val child = manager.getAction(id) ?: return@forEach
            runCatching { mainMenu.remove(child) }
                .onFailure { error -> logger.warn("Could not remove menu group $id", error) }
        }
    }

    private fun stripToolbarGroups(manager: ActionManager) {
        val childIds = IdeSurfacePolicy.actionsUnregisteredAtStartup() + listOf(
            "ExecutionTargetsToolbarGroup",
            "SegmentedVcsActionsBarGroup",
            "NavBarVcsGroup",
            "VcsToolbarActions",
            "VcsNavBarToolbarActions",
        )
        IdeSurfacePolicy.toolbarGroupIdsToStrip.forEach { parentId ->
            val parent = manager.getAction(parentId) as? DefaultActionGroup ?: return@forEach
            childIds.forEach { id ->
                val child = manager.getAction(id) ?: return@forEach
                runCatching { parent.remove(child) }
            }
        }
    }
}
