package moe.momokko.intellido.ui.directory

import com.intellij.openapi.fileTypes.FileType
import com.intellij.util.ui.EmptyIcon
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspaceLayout
import javax.swing.Icon

class DirectoryFileType : FileType {
    override fun getName(): String = NAME

    override fun getDescription(): String = "IntelliDo Directory"

    override fun getDefaultExtension(): String = EXTENSION

    override fun getIcon(): Icon = EmptyIcon.ICON_16

    override fun isBinary(): Boolean = false

    override fun isReadOnly(): Boolean = true

    companion object {
        const val NAME: String = "IntelliDoDirectory"
        val EXTENSION: String = IntelliDoWorkspaceLayout.DIRECTORY_EXTENSION
        @JvmField
        val INSTANCE: DirectoryFileType = DirectoryFileType()
    }
}
