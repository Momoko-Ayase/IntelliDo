package moe.momokko.intellido.ui.topic

import com.intellij.openapi.fileTypes.FileType
import com.intellij.util.ui.EmptyIcon
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspaceLayout
import javax.swing.Icon

class TopicFileType : FileType {
    override fun getName(): String = NAME

    override fun getDescription(): String = "IntelliDo Topic"

    override fun getDefaultExtension(): String = EXTENSION

    override fun getIcon(): Icon = EmptyIcon.ICON_16

    override fun isBinary(): Boolean = false

    override fun isReadOnly(): Boolean = true

    companion object {
        const val NAME: String = "IntelliDoTopic"
        val EXTENSION: String = IntelliDoWorkspaceLayout.TOPIC_EXTENSION
        @JvmField
        val INSTANCE: TopicFileType = TopicFileType()
    }
}
