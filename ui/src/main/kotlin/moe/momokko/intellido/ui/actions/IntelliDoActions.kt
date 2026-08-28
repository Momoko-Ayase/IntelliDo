package moe.momokko.intellido.ui.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.Messages
import moe.momokko.intellido.browser.IsolatedBrowserProfiles
import moe.momokko.intellido.browser.JcefDiagnostics
import moe.momokko.intellido.platform.catalog.DirectoryKind
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.platform.reset.LocalDataReset
import moe.momokko.intellido.ui.settings.ReadingConfigurable
import moe.momokko.intellido.ui.startup.IntelliDoRuntime
import moe.momokko.intellido.ui.startup.IntelliDoStartup
import moe.momokko.intellido.ui.topic.TopicFileEditor
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspace
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.Path

class GoHomeAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        if (project != null) {
            IntelliDoWorkspace.focusHome(project)
        } else {
            IntelliDoStartup.focusHome()
        }
    }
}

class ReadingSettingsAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        ShowSettingsUtil.getInstance().showSettingsDialog(event.project, ReadingConfigurable::class.java)
    }
}

class AboutAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val runtime = service<IntelliDoRuntime>()
        val locale = currentLocale()
        Messages.showInfoMessage(
            IntelliDoStrings.message("about.body", locale, runtime.identity.productVersion) +
                "\n" +
                IntelliDoStrings.message("product.unofficial.full", locale),
            IntelliDoStrings.message("action.about", locale),
        )
    }
}

class SignInAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val locale = currentLocale()
        Messages.showInfoMessage(
            IntelliDoStrings.message("signIn.notWired", locale),
            IntelliDoStrings.message("action.signIn", locale),
        )
    }
}

abstract class OpenDirectoryAction(private val kind: DirectoryKind) : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        IntelliDoWorkspace.openDirectory(project, kind)
    }
}

class OpenCategoriesAction : OpenDirectoryAction(DirectoryKind.CATEGORIES)
class OpenTagsAction : OpenDirectoryAction(DirectoryKind.TAGS)
class OpenGroupsAction : OpenDirectoryAction(DirectoryKind.GROUPS)
class OpenBadgesAction : OpenDirectoryAction(DirectoryKind.BADGES)
class OpenMembersAction : OpenDirectoryAction(DirectoryKind.MEMBERS)
class OpenCommunityAboutAction : OpenDirectoryAction(DirectoryKind.ABOUT)

class ExitAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        ApplicationManager.getApplication().exit()
    }
}

class RetryJcefAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        IntelliDoStartup.launch()
    }
}

class CopyJcefDiagnosticsAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val locale = currentLocale()
        val diagnostics = runCatching { service<IntelliDoRuntime>().lastJcefDiagnostics }.getOrNull()
            ?: JcefDiagnostics.capture("no JCEF failure recorded", jcefReportedSupported = true)
        IntelliDoWorkspace.copyText(diagnostics.copyableText())
        Messages.showInfoMessage(
            diagnostics.copyableText(),
            IntelliDoStrings.message("recovery.copyDiagnostics", locale),
        )
    }
}

class OpenJcefRepairGuideAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val locale = currentLocale()
        val resource = if (locale.language == "zh") "docs/jcef-repair.zh.md" else "docs/jcef-repair.md"
        val body = javaClass.classLoader.getResourceAsStream(resource)?.bufferedReader()?.use { it.readText() }
            ?: IntelliDoStrings.message("recovery.openRepairGuide", locale)
        Messages.showInfoMessage(body, IntelliDoStrings.message("recovery.title", locale))
    }
}

class FindInTopicAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        (event.getData(PlatformDataKeys.FILE_EDITOR) as? TopicFileEditor)?.showFind()
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.getData(PlatformDataKeys.FILE_EDITOR) is TopicFileEditor
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class CopyTopicLinkAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        (event.getData(PlatformDataKeys.FILE_EDITOR) as? TopicFileEditor)?.copyTopicLink()
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.getData(PlatformDataKeys.FILE_EDITOR) is TopicFileEditor
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class ResetLocalDataAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val locale = currentLocale()
        val runtime = service<IntelliDoRuntime>()
        val phrase = LocalDataReset.confirmPhrase(locale)
        val browser = runtime.browserProfile?.userDataDirectory
            ?: Path(PathManager.getSystemPath(), "jcef")
        val prefs = IntelliDoStartup.preferenceFile()
        val workspace = IntelliDoWorkspace.directory()
        val logs = Path.of(PathManager.getLogPath())
        val caches = listOf(
            Path.of(PathManager.getSystemPath(), "intellido-media"),
            Path.of(PathManager.getSystemPath(), IsolatedBrowserProfiles.DEFAULT_CACHE_DIR),
        )
        val preview = LocalDataReset.preview(browser, prefs, workspace, logs, caches)
        val body = buildString {
            append(IntelliDoStrings.message("reset.body", locale))
            append("\n\n")
            preview.forEach { category ->
                append(IntelliDoStrings.message("reset.category.${category.id}", locale))
                append(": ")
                append(category.path)
                append('\n')
            }
            append('\n')
            append(IntelliDoStrings.message("reset.confirmPrompt", locale, phrase))
        }
        val typed = Messages.showInputDialog(
            event.project,
            body,
            IntelliDoStrings.message("reset.title", locale),
            Messages.getWarningIcon(),
        )
        if (!LocalDataReset.matches(typed.orEmpty(), locale)) {
            return
        }
        LocalDataReset.apply(browser, prefs, workspace, logs, caches)
        Messages.showInfoMessage(
            IntelliDoStrings.message("reset.done", locale),
            IntelliDoStrings.message("reset.title", locale),
        )
        ApplicationManager.getApplication().restart()
    }
}

private fun currentLocale(): Locale =
    runCatching { service<IntelliDoRuntime>().locale }.getOrDefault(Locale.getDefault())
