package moe.momokko.intellido.ui.content

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
import moe.momokko.intellido.ui.workspace.LocalVirtualFiles
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

/**
 * Opens cooked post images in the bundled IntelliJ image editor tab
 * (`com.intellij.platform.images`), not a custom window.
 */
object PostImages {
    fun extension(bytes: ByteArray): String {
        if (bytes.size >= 6) {
            val header = bytes.decodeToString(0, 6)
            if (header == "GIF89a" || header == "GIF87a") {
                return "gif"
            }
        }
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
            return "jpg"
        }
        if (bytes.size >= 12 && bytes.copyOfRange(8, 12).decodeToString() == "WEBP") {
            return "webp"
        }
        if (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) {
            return "png"
        }
        return "png"
    }

    fun open(component: JComponent, bytes: ByteArray) {
        if (bytes.isEmpty()) {
            return
        }
        val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(component))
            ?: ProjectManager.getInstance().openProjects.firstOrNull()
            ?: return
        val ext = extension(bytes)
        val name = Integer.toUnsignedString(bytes.contentHashCode(), 16)
        val path = Path.of(PathManager.getTempPath(), "intellido-images", "$name.$ext")
        Files.createDirectories(path.parent)
        if (!path.exists()) {
            path.writeBytes(bytes)
        }
        val file = LocalVirtualFiles.find(path) ?: return
        val open = Runnable { FileEditorManager.getInstance(project).openFile(file, true) }
        if (ApplicationManager.getApplication().isDispatchThread) {
            open.run()
        } else {
            ApplicationManager.getApplication().invokeLater(open)
        }
    }
}
