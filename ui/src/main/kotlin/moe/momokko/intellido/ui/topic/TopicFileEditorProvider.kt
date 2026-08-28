package moe.momokko.intellido.ui.topic

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspaceLayout

class TopicFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean =
        file.fileType == TopicFileType.INSTANCE ||
            file.name.endsWith(".${IntelliDoWorkspaceLayout.TOPIC_EXTENSION}")

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val id = file.nameWithoutExtension.removePrefix("topic-").toLongOrNull() ?: 0L
        return TopicFileEditor(project, file, id)
    }

    override fun getEditorTypeId(): String = EDITOR_TYPE_ID

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

    companion object {
        const val EDITOR_TYPE_ID: String = "intellido-topic"
    }
}
