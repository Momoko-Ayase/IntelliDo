package moe.momokko.intellido.ui.home

import com.intellij.openapi.fileTypes.FileType
import com.intellij.util.ui.EmptyIcon
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspaceLayout
import javax.swing.Icon

class HomeFileType : FileType {
    override fun getName(): String = NAME

    override fun getDescription(): String = "IntelliDo Home"

    override fun getDefaultExtension(): String = EXTENSION

    override fun getIcon(): Icon = EmptyIcon.ICON_16

    override fun isBinary(): Boolean = false

    override fun isReadOnly(): Boolean = true

    companion object {
        const val NAME: String = "IntelliDoHome"
        val EXTENSION: String = IntelliDoWorkspaceLayout.HOME_FILE_NAME.substringAfterLast('.')
        @JvmField
        val INSTANCE: HomeFileType = HomeFileType()
    }
}
