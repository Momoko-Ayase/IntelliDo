package moe.momokko.intellido.ui.nav

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import javax.swing.Icon

class CommunityNavToolWindowFactory : ToolWindowFactory, DumbAware {
    override val icon: Icon
        get() = LINUX_DO_ICON

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.setIcon(LINUX_DO_ICON)
        val content = ContentFactory.getInstance().createContent(CommunityNavPanel(project), "", false)
        content.isCloseable = false
        toolWindow.contentManager.addContent(content)
        toolWindow.setToHideOnEmptyContent(false)
        if (!toolWindow.isVisible) {
            toolWindow.show()
        }
    }

    override fun shouldBeAvailable(project: Project): Boolean = true

    companion object {
        private val LINUX_DO_ICON = IconLoader.getIcon("/icons/linuxdo.svg", CommunityNavToolWindowFactory::class.java)
    }
}
