package moe.momokko.intellido.ui.browse

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.ui.startup.IntelliDoRuntime
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspace
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent

class BrowseFileEditor(
    private val project: Project,
    private val file: VirtualFile,
    url: String,
) : UserDataHolderBase(), FileEditor {
    private val listeners = PropertyChangeSupport(this)
    private val host: JBPanel<*> = JBPanel<JBPanel<*>>(BorderLayout())
    private var view: BrowseView? = null

    init {
        val runtime = service<IntelliDoRuntime>()
        var panel: BrowsePanel? = null
        val created = runCatching {
            JcefBrowseView(
                onNative = { link -> IntelliDoWorkspace.openNative(project, link) },
                onExternal = { BrowserUtil.browse(it) },
                onConfirm = { IntelliDoWorkspace.openFromUrl(project, it) },
                onDownload = { target, _ -> IntelliDoWorkspace.saveAttachment(project, target) },
                onCopy = { IntelliDoWorkspace.copyText(it) },
                onLocation = { panel?.refreshOrigin() },
            )
        }.getOrNull()
        if (created == null) {
            host.add(JBLabel(IntelliDoStrings.message("browse.blocked", runtime.locale)), BorderLayout.NORTH)
        } else {
            view = created
            panel = BrowsePanel(
                url,
                runtime.locale,
                created,
                onOpenExternal = { BrowserUtil.browse(it) },
            )
            host.add(panel, BorderLayout.CENTER)
        }
    }

    override fun getFile(): VirtualFile = file
    override fun getComponent(): JComponent = host
    override fun getPreferredFocusedComponent(): JComponent = host
    override fun getName(): String = file.nameWithoutExtension
    override fun setState(state: FileEditorState) = Unit
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        listeners.addPropertyChangeListener(listener)
    }
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        listeners.removePropertyChangeListener(listener)
    }
    override fun dispose() {
        view?.dispose()
    }
}
