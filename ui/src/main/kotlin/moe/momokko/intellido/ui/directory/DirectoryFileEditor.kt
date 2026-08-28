package moe.momokko.intellido.ui.directory

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import moe.momokko.intellido.platform.catalog.DirectoryKind
import moe.momokko.intellido.ui.startup.IntelliDoRuntime
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspace
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent

class DirectoryFileEditor(
    private val project: Project,
    private val file: VirtualFile,
    kind: DirectoryKind,
) : UserDataHolderBase(), FileEditor {
    private val listeners = PropertyChangeSupport(this)
    private val panel: DirectoryPanel

    init {
        val runtime = service<IntelliDoRuntime>()
        panel = DirectoryPanel(
            kind,
            runtime.communityClient,
            runtime.locale,
            onOpenTopic = { topic, pin -> IntelliDoWorkspace.openTopicPreview(project, topic, pin) },
            onOpenUser = { username -> IntelliDoWorkspace.openUser(project, username) },
            onOpenUrl = { url -> IntelliDoWorkspace.openFromUrl(project, url) },
        )
    }

    override fun getFile(): VirtualFile = file

    override fun getComponent(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent = panel

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

    override fun dispose() = Unit
}
