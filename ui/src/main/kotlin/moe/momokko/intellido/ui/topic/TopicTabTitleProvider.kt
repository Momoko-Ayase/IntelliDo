package moe.momokko.intellido.ui.topic

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import moe.momokko.intellido.ui.startup.IntelliDoRuntime
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspaceLayout

class TopicTabTitleProvider : EditorTabTitleProvider {
    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
        if (file.fileType != TopicFileType.INSTANCE &&
            !file.name.endsWith(".${IntelliDoWorkspaceLayout.TOPIC_EXTENSION}")
        ) {
            return null
        }
        val stored = runCatching { String(file.contentsToByteArray(), Charsets.UTF_8).trim() }.getOrNull()
        if (!stored.isNullOrBlank()) {
            return stored
        }
        val id = file.nameWithoutExtension.removePrefix("topic-").toLongOrNull() ?: return file.name
        return service<IntelliDoRuntime>().homeController.snapshot().firstOrNull { it.id == id }?.title
            ?: file.nameWithoutExtension
    }
}
