package moe.momokko.intellido.platform.reset

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

data class LocalDataCategory(
    val id: String,
    val path: String,
)

/**
 * Guest-safe complete wipe of IntelliDo-managed local state. Never deletes
 * files the member saved outside IntelliDo directories.
 */
object LocalDataReset {
    fun confirmPhrase(locale: Locale): String =
        if (locale.language == "zh") CONFIRM_ZH else CONFIRM_EN

    fun matches(typed: String, locale: Locale): Boolean =
        typed.trim() == confirmPhrase(locale)

    fun preview(
        browserProfile: Path,
        preferencesFile: Path,
        workspaceDir: Path,
        logsDir: Path,
        cacheDirs: List<Path>,
    ): List<LocalDataCategory> = buildList {
        add(LocalDataCategory("browser", browserProfile.toAbsolutePath().toString()))
        add(LocalDataCategory("preferences", preferencesFile.toAbsolutePath().toString()))
        add(LocalDataCategory("workspace", workspaceDir.toAbsolutePath().toString()))
        add(LocalDataCategory("logs", logsDir.toAbsolutePath().toString()))
        cacheDirs.forEach { dir ->
            add(LocalDataCategory("cache", dir.toAbsolutePath().toString()))
        }
    }

    fun apply(
        browserProfile: Path,
        preferencesFile: Path,
        workspaceDir: Path,
        logsDir: Path,
        cacheDirs: List<Path>,
    ) {
        wipe(browserProfile)
        Files.deleteIfExists(preferencesFile)
        wipe(workspaceDir)
        wipe(logsDir)
        cacheDirs.forEach { wipe(it) }
    }

    private fun wipe(path: Path) {
        if (!Files.exists(path)) {
            return
        }
        if (Files.isRegularFile(path)) {
            Files.deleteIfExists(path)
            return
        }
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    const val CONFIRM_ZH: String = "清除所有本地数据"
    const val CONFIRM_EN: String = "delete all local data"
}
