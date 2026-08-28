package moe.momokko.intellido.ui.profile

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.ui.startup.IntelliDoRuntime
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspace
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent

class UserFileEditor(
    private val project: Project,
    private val file: VirtualFile,
    private val username: String,
) : UserDataHolderBase(), FileEditor {
    private val listeners = PropertyChangeSupport(this)
    private val host: JBPanel<*> = JBPanel<JBPanel<*>>(BorderLayout())
    private val runtime: IntelliDoRuntime = service()

    init {
        host.border = JBUI.Borders.empty(24)
        host.add(JBLabel(IntelliDoStrings.message("profile.loading", runtime.locale)), BorderLayout.NORTH)
        ApplicationManager.getApplication().executeOnPooledThread {
            runtime.awaitCommunity()
            val loaded = runCatching { runtime.communityClient.loadPublicProfile(username) }
            ApplicationManager.getApplication().invokeLater {
                host.removeAll()
                loaded.onSuccess { profile ->
                    val panel = UserPanel(
                        profile,
                        runtime.locale,
                        onNavigate = { url -> IntelliDoWorkspace.openFromUrl(project, url) },
                        onOpenUser = { name -> IntelliDoWorkspace.openUser(project, name) },
                    )
                    host.border = JBUI.Borders.empty()
                    host.add(panel, BorderLayout.CENTER)
                    host.revalidate()
                    host.repaint()
                    val urls = (
                        listOfNotNull(profile.avatarUrl(120)) +
                            (profile.summary?.let { summary ->
                                (summary.mostLikedBy + summary.mostLiked + summary.mostRepliedTo)
                                    .mapNotNull { it.avatarUrl(48) }
                            } ?: emptyList())
                        ).distinct()
                    if (urls.isNotEmpty()) {
                        val loader = runtime.mediaLoader
                        if (loader != null) {
                            ApplicationManager.getApplication().executeOnPooledThread {
                                val bytes = runCatching { loader.load(urls, 120) }.getOrDefault(emptyMap())
                                ApplicationManager.getApplication().invokeLater {
                                    panel.applyMedia(bytes)
                                }
                            }
                        }
                    }
                }.onFailure { error ->
                    logger.warn("Failed to load public profile $username", error)
                    host.add(JBLabel(IntelliDoStrings.message("profile.loadFailed", runtime.locale)), BorderLayout.NORTH)
                    host.revalidate()
                    host.repaint()
                }
            }
        }
    }

    override fun getFile(): VirtualFile = file

    override fun getComponent(): JComponent = host

    override fun getPreferredFocusedComponent(): JComponent = host

    override fun getName(): String = username

    override fun setState(state: FileEditorState) = Unit

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = true

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        listeners.addPropertyChangeListener(listener)
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        listeners.removePropertyChangeListener(listener)
    }

    override fun dispose() = Unit

    companion object {
        private val logger = Logger.getInstance(UserFileEditor::class.java)
    }
}
