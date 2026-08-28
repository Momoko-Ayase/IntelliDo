package moe.momokko.intellido.ui.home

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.ui.startup.IntelliDoRuntime
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspace
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import java.util.Locale
import javax.swing.JComponent

class HomeFileEditor(
    private val project: Project,
    private val file: VirtualFile,
) : UserDataHolderBase(), FileEditor {
    private val listeners = PropertyChangeSupport(this)
    private val panel: HomePanel

    init {
        val runtime = service<IntelliDoRuntime>()
        panel = HomePanel(
            runtime.locale,
            onOpenTopic = { topic, pin -> IntelliDoWorkspace.openTopicPreview(project, topic, pin) },
        )
    }

    fun reload() {
        panel.reload()
    }

    fun showLatest() {
        panel.showLatest()
    }

    fun showHot() {
        panel.showHot()
    }

    fun showTop() {
        panel.showTop()
    }

    fun showCategory(categoryId: Long) {
        panel.showCategory(categoryId)
    }

    fun showTag(name: String) {
        panel.showTag(name)
    }

    override fun getFile(): VirtualFile = file

    override fun getComponent(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent = panel

    override fun getName(): String = IntelliDoStrings.message("tab.home", Locale.getDefault())

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
        panel.disposeLive()
    }
}
