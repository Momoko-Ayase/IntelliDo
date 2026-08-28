package moe.momokko.intellido.ui.home

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class HomeFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean =
        file.fileType == HomeFileType.INSTANCE ||
            file.name == moe.momokko.intellido.ui.workspace.IntelliDoWorkspaceLayout.HOME_FILE_NAME

    override fun createEditor(project: Project, file: VirtualFile): FileEditor = HomeFileEditor(project, file)

    override fun getEditorTypeId(): String = EDITOR_TYPE_ID

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

    companion object {
        const val EDITOR_TYPE_ID: String = "intellido-home"
    }
}
