package moe.momokko.intellido.ui.directory

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import moe.momokko.intellido.platform.catalog.DirectoryKind
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspaceLayout
import java.util.Locale

class DirectoryTabTitleProvider : EditorTabTitleProvider {
    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
        if (file.fileType != DirectoryFileType.INSTANCE &&
            !file.name.endsWith(".${IntelliDoWorkspaceLayout.DIRECTORY_EXTENSION}")
        ) {
            return null
        }
        val kind = DirectoryKind.entries.firstOrNull { it.fileName == file.name } ?: return file.name
        return IntelliDoStrings.message(kind.titleKey, Locale.getDefault())
    }
}
