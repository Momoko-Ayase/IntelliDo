package moe.momokko.intellido.ui.browse

import com.intellij.openapi.fileTypes.FileType
import com.intellij.util.ui.EmptyIcon
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspaceLayout
import javax.swing.Icon

class BrowseFileType : FileType {
    override fun getName(): String = NAME
    override fun getDescription(): String = "IntelliDo Browser"
    override fun getDefaultExtension(): String = EXTENSION
    override fun getIcon(): Icon = EmptyIcon.ICON_16
    override fun isBinary(): Boolean = false
    override fun isReadOnly(): Boolean = true

    companion object {
        const val NAME: String = "IntelliDoBrowse"
        val EXTENSION: String = IntelliDoWorkspaceLayout.BROWSE_EXTENSION
        @JvmField
        val INSTANCE: BrowseFileType = BrowseFileType()
    }
}
