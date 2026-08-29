package moe.momokko.intellido.ui.toolwindow

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.ui.guest.GuestUi
import moe.momokko.intellido.ui.startup.IntelliDoRuntime
import java.awt.BorderLayout
import java.util.Locale
import javax.swing.BoxLayout

abstract class PlaceholderToolWindowFactory(private val titleKey: String) : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val locale = runCatching { service<IntelliDoRuntime>().locale }.getOrDefault(Locale.getDefault())
        val panel = JBPanel<JBPanel<*>>(BorderLayout())
        val column = JBPanel<JBPanel<*>>()
        column.layout = BoxLayout(column, BoxLayout.Y_AXIS)
        column.isOpaque = false
        column.border = JBUI.Borders.empty(12)
        column.add(JBLabel(IntelliDoStrings.message(titleKey, locale)))
        val session = runCatching { service<IntelliDoRuntime>().session }.getOrNull()
        val hintKey = if (session is moe.momokko.intellido.domain.session.MemberSession.SignedIn) {
            "toolwindow.notInSlice"
        } else {
            "toolwindow.needsSignIn"
        }
        val hint = JBLabel(IntelliDoStrings.message(hintKey, locale))
        hint.foreground = GuestUi.muted
        hint.font = GuestUi.metaFont(hint.font)
        hint.border = JBUI.Borders.emptyTop(6)
        column.add(hint)
        panel.add(column, BorderLayout.NORTH)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

class ConnectToolWindowFactory : PlaceholderToolWindowFactory("toolwindow.connect")
class NotificationsToolWindowFactory : PlaceholderToolWindowFactory("toolwindow.notifications")
class MessagesToolWindowFactory : PlaceholderToolWindowFactory("toolwindow.messages")
class ChatToolWindowFactory : PlaceholderToolWindowFactory("toolwindow.chat")
class BookmarksToolWindowFactory : PlaceholderToolWindowFactory("toolwindow.bookmarks")
class DraftsToolWindowFactory : PlaceholderToolWindowFactory("toolwindow.drafts")
