package moe.momokko.intellido.ui.browse

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspaceLayout

class BrowseFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean =
        file.fileType == BrowseFileType.INSTANCE ||
            file.name.endsWith(".${IntelliDoWorkspaceLayout.BROWSE_EXTENSION}")

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val url = runCatching { String(file.contentsToByteArray(), Charsets.UTF_8).trim() }.getOrNull()
            ?: "https://linux.do"
        return BrowseFileEditor(project, file, url)
    }

    override fun getEditorTypeId(): String = EDITOR_TYPE_ID

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

    companion object {
        const val EDITOR_TYPE_ID: String = "intellido-browse"
    }
}
