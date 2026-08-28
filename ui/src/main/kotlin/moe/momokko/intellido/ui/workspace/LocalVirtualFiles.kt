package moe.momokko.intellido.ui.workspace

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

/**
 * Topic stubs are created with NIO, so a first open must refresh VFS.
 * Callers resolve on a pooled thread (VFS persistence reads are forbidden on the
 * EDT); when a caller is still on the EDT the refresh needs write-intent
 * (IntelliJ 2026.2), so only wrap in that case.
 */
internal object LocalVirtualFiles {
    fun find(nio: Path): VirtualFile? {
        val local = LocalFileSystem.getInstance()
        return locate(local.findFileByNioFile(nio)) {
            if (!ApplicationManager.getApplication().isDispatchThread) {
                return@locate local.refreshAndFindFileByNioFile(nio)
            }
            var refreshed: VirtualFile? = null
            ApplicationManager.getApplication().runWriteIntentReadAction<Unit, RuntimeException> {
                refreshed = local.refreshAndFindFileByNioFile(nio)
            }
            refreshed
        }
    }

    fun <T> locate(existing: T?, refresh: () -> T?): T? = existing ?: refresh()
}
