package moe.momokko.intellido.ui.welcome

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspaceLayout
import java.util.Locale

class WelcomeTabTitleProvider : EditorTabTitleProvider {
    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? =
        if (file.fileType == WelcomeFileType.INSTANCE || file.name == IntelliDoWorkspaceLayout.WELCOME_FILE_NAME) {
            IntelliDoStrings.message("tab.welcome", Locale.getDefault())
        } else {
            null
        }
}
