package moe.momokko.intellido.ui.directory

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import moe.momokko.intellido.platform.catalog.DirectoryKind
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspaceLayout

class DirectoryFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean =
        file.fileType == DirectoryFileType.INSTANCE ||
            file.name.endsWith(".${IntelliDoWorkspaceLayout.DIRECTORY_EXTENSION}")

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val kind = DirectoryKind.entries.firstOrNull { it.fileName == file.name } ?: DirectoryKind.ABOUT
        return DirectoryFileEditor(project, file, kind)
    }

    override fun getEditorTypeId(): String = EDITOR_TYPE_ID

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

    companion object {
        const val EDITOR_TYPE_ID: String = "intellido-directory"
    }
}
