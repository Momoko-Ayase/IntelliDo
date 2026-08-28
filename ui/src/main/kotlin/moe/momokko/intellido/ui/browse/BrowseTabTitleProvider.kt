package moe.momokko.intellido.ui.browse

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import moe.momokko.intellido.domain.browse.TrustedOrigins
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspaceLayout

class BrowseTabTitleProvider : EditorTabTitleProvider {
    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
        if (file.fileType != BrowseFileType.INSTANCE &&
            !file.name.endsWith(".${IntelliDoWorkspaceLayout.BROWSE_EXTENSION}")
        ) {
            return null
        }
        val url = runCatching { String(file.contentsToByteArray(), Charsets.UTF_8).trim() }.getOrNull().orEmpty()
        return TrustedOrigins.originOf(url) ?: file.nameWithoutExtension
    }
}
