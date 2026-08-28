package moe.momokko.intellido.ui.home

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspaceLayout
import java.util.Locale

class HomeTabTitleProvider : EditorTabTitleProvider {
    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? =
        if (file.fileType == HomeFileType.INSTANCE || file.name == IntelliDoWorkspaceLayout.HOME_FILE_NAME) {
            IntelliDoStrings.message("tab.home", Locale.getDefault())
        } else {
            null
        }
}
