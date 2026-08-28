package moe.momokko.intellido.ui.welcome

import com.intellij.openapi.fileTypes.FileType
import com.intellij.util.ui.EmptyIcon
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspaceLayout
import javax.swing.Icon

class WelcomeFileType : FileType {
    override fun getName(): String = NAME

    override fun getDescription(): String = "IntelliDo Welcome"

    override fun getDefaultExtension(): String = EXTENSION

    override fun getIcon(): Icon = EmptyIcon.ICON_16

    override fun isBinary(): Boolean = false

    override fun isReadOnly(): Boolean = true

    companion object {
        const val NAME: String = "IntelliDoWelcome"
        val EXTENSION: String = IntelliDoWorkspaceLayout.WELCOME_FILE_NAME.substringAfterLast('.')
        @JvmField
        val INSTANCE: WelcomeFileType = WelcomeFileType()
    }
}
