package moe.momokko.intellido.ui.profile

import com.intellij.openapi.fileTypes.FileType
import com.intellij.util.ui.EmptyIcon
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspaceLayout
import javax.swing.Icon

class UserFileType : FileType {
    override fun getName(): String = NAME

    override fun getDescription(): String = "IntelliDo User"

    override fun getDefaultExtension(): String = EXTENSION

    override fun getIcon(): Icon = EmptyIcon.ICON_16

    override fun isBinary(): Boolean = false

    override fun isReadOnly(): Boolean = true

    companion object {
        const val NAME: String = "IntelliDoUser"
        val EXTENSION: String = IntelliDoWorkspaceLayout.USER_EXTENSION
        @JvmField
        val INSTANCE: UserFileType = UserFileType()
    }
}
