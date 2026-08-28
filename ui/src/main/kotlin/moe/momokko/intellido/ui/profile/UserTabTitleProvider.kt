package moe.momokko.intellido.ui.profile

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspaceLayout

class UserTabTitleProvider : EditorTabTitleProvider {
    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
        if (file.fileType != UserFileType.INSTANCE &&
            !file.name.endsWith(".${IntelliDoWorkspaceLayout.USER_EXTENSION}")
        ) {
            return null
        }
        return IntelliDoWorkspaceLayout.usernameFrom(file.name) ?: file.nameWithoutExtension
    }
}
