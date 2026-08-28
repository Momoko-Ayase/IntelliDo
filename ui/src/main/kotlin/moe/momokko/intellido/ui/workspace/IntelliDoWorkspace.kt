package moe.momokko.intellido.ui.workspace

import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.ui.Messages
import moe.momokko.intellido.domain.browse.BrowseDecision
import moe.momokko.intellido.domain.browse.BrowseRouter
import moe.momokko.intellido.domain.content.Attachments
import moe.momokko.intellido.platform.nav.CommunityNavModel
import moe.momokko.intellido.domain.topic.DiscourseLink
import moe.momokko.intellido.domain.topic.HomeTopic
import moe.momokko.intellido.platform.catalog.DirectoryKind
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.Instant
import moe.momokko.intellido.ui.home.HomeFileEditor
import moe.momokko.intellido.ui.home.HomeFileType
import moe.momokko.intellido.ui.startup.IntelliDoRuntime
import moe.momokko.intellido.ui.surface.IdeSurfaceApplicator
import moe.momokko.intellido.ui.topic.TopicFileType
import moe.momokko.intellido.ui.welcome.WelcomeFileType
import java.awt.Frame
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.SwingUtilities
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

object IntelliDoWorkspace {
    private val logger = Logger.getInstance(IntelliDoWorkspace::class.java)
    private val tabListenerInstalled: Key<Boolean> = Key.create("IntelliDoWorkspaceTabListener")

    fun directory(): Path = Path(PathManager.getSystemPath(), IntelliDoWorkspaceLayout.DIRECTORY_NAME)

    fun homePath(): Path = directory().resolve(IntelliDoWorkspaceLayout.HOME_FILE_NAME)

    fun welcomePath(): Path = directory().resolve(IntelliDoWorkspaceLayout.WELCOME_FILE_NAME)

    fun topicPath(topicId: Long): Path = directory().resolve(IntelliDoWorkspaceLayout.topicFileName(topicId))

    fun directoryPath(kind: DirectoryKind): Path = directory().resolve(kind.fileName)

    fun openOrFocus(): Project? {
        val existing = ProjectManager.getInstance().openProjects.firstOrNull()
        if (existing != null) {
            bringToFront(existing)
            return existing
        }
        return open()
    }

    fun open(): Project? {
        ensureWorkspaceFiles()
        val dir = directory()
        TrustedProjects.setProjectTrusted(dir, true)
        val task = OpenProjectTask {
            projectName = "IntelliDo"
            isNewProject = !dir.resolve(".idea").exists()
            createModule = false
            showWelcomeScreen = false
            runConfigurators = false
            runConversionBeforeOpen = false
            forceOpenInNewFrame = false
            preventIprLookup = true
        }
        val project = ProjectManagerEx.getInstanceEx().openProject(dir, task)
        if (project == null) {
            logger.warn("Failed to open IntelliDo workspace at $dir")
            return null
        }
        RecentProjectsManager.getInstance().removePath(dir.toString())
        RecentProjectsManager.getInstance().removePath(dir.toAbsolutePath().toString().replace('\\', '/'))
        configure(project)
        return project
    }

    fun configure(project: Project) {
        val apply = Runnable {
            if (project.isDisposed) {
                return@Runnable
            }
            IdeSurfaceApplicator.applyProjectSurface(project)
            closeRestoredContent(project)
            openTabs(project)
            reloadHome(project)
            showCommunityNav(project)
            bringToFront(project)
            if (project.getUserData(tabListenerInstalled) != true) {
                project.putUserData(tabListenerInstalled, true)
                listenForTabClose(project)
            }
        }
        if (SwingUtilities.isEventDispatchThread()) {
            apply.run()
        } else {
            ApplicationManager.getApplication().invokeLater(apply)
        }
    }

    fun openFromUrl(project: Project, url: String): Boolean {
        if (Attachments.isAttachmentUrl(url)) {
            saveAttachment(project, url)
            return true
        }
        return when (val decision = BrowseRouter.decide(url)) {
            is BrowseDecision.Native -> openNative(project, decision.link)
            is BrowseDecision.InApp -> {
                openBrowse(project, decision.url)
                true
            }
            is BrowseDecision.External -> {
                BrowserUtil.browse(decision.url)
                true
            }
            is BrowseDecision.Confirm -> confirmAndOpen(project, decision.url)
            is BrowseDecision.CopyOnly -> {
                copyText(decision.url)
                true
            }
            BrowseDecision.Blocked -> false
        }
    }

    fun openNative(project: Project, link: DiscourseLink): Boolean {
        when (link) {
            is DiscourseLink.Topic -> openTopic(project, link.topicId)
            is DiscourseLink.Tag -> openTagTopics(project, link.name)
            is DiscourseLink.Category -> openCategoryTopics(project, link.categoryId)
            is DiscourseLink.User -> openUser(project, link.username)
            is DiscourseLink.Directory -> openDirectory(project, directoryKind(link.page))
            is DiscourseLink.Group -> openBrowse(project, "https://linux.do/g/${link.name}")
            is DiscourseLink.Page -> openBrowse(project, "https://linux.do${link.path}")
        }
        return true
    }

    fun openBrowse(project: Project, url: String) {
        openPrepared(project, { browseVirtualFile(url) })
    }

    /**
     * Workspace stubs are NIO files and resolving them touches VFS persistence,
     * which the platform forbids on the EDT. Prepare on a pooled thread, then
     * hop back to the EDT for the editor itself.
     */
    private fun openPrepared(
        project: Project,
        prepare: () -> VirtualFile?,
        onEdt: (FileEditorManager, VirtualFile) -> Unit = { manager, file -> manager.openFile(file, true) },
    ) {
        if (project.isDisposed || ApplicationManager.getApplication().isDisposed) {
            return
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            val file = runCatching(prepare)
                .onFailure { error -> logger.warn("Could not prepare an IntelliDo workspace file", error) }
                .getOrNull() ?: return@executeOnPooledThread
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) {
                    return@invokeLater
                }
                onEdt(FileEditorManager.getInstance(project), file)
                bringToFront(project)
            }
        }
    }

    fun copyText(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    fun saveAttachment(project: Project, url: String) {
        if (!Attachments.isAttachmentUrl(url)) {
            return
        }
        val runtime = service<IntelliDoRuntime>()
        val locale = runtime.locale
        val name = Attachments.suggestedName(url)
        val descriptor = FileSaverDescriptor(IntelliDoStrings.message("attachment.save", locale), "", "*")
        val wrapper = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project).save(name)
        val target = wrapper?.file ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            val bytes = runCatching {
                runtime.mediaLoader?.load(listOf(url), 4096)?.values?.firstOrNull()
            }.getOrNull()
            ApplicationManager.getApplication().invokeLater {
                if (bytes == null) {
                    Messages.showErrorDialog(
                        project,
                        IntelliDoStrings.message("attachment.failed", locale),
                        IntelliDoStrings.message("attachment.save", locale),
                    )
                    return@invokeLater
                }
                runCatching { Files.write(target.toPath(), bytes) }
            }
        }
    }

    private fun confirmAndOpen(project: Project, url: String): Boolean {
        val locale = service<IntelliDoRuntime>().locale
        val key = if (url.startsWith("mailto:", ignoreCase = true)) "browse.confirmMailto" else "browse.confirmHttp"
        val ok = Messages.showYesNoDialog(
            project,
            IntelliDoStrings.message(key, locale, url),
            IntelliDoStrings.message("browse.openExternal", locale),
            Messages.getWarningIcon(),
        ) == Messages.YES
        if (ok) {
            BrowserUtil.browse(url)
        }
        return true
    }

    private fun directoryKind(page: DiscourseLink.DirectoryPage): DirectoryKind = when (page) {
        DiscourseLink.DirectoryPage.ABOUT -> DirectoryKind.ABOUT
        DiscourseLink.DirectoryPage.CATEGORIES -> DirectoryKind.CATEGORIES
        DiscourseLink.DirectoryPage.TAGS -> DirectoryKind.TAGS
        DiscourseLink.DirectoryPage.GROUPS -> DirectoryKind.GROUPS
        DiscourseLink.DirectoryPage.BADGES -> DirectoryKind.BADGES
        DiscourseLink.DirectoryPage.MEMBERS -> DirectoryKind.MEMBERS
    }

    fun openTopic(project: Project, topicId: Long, pin: Boolean = false) {
        openTopicPreview(
            project,
            HomeTopic(
                id = topicId,
                title = "话题",
                slug = topicId.toString(),
                postsCount = 1,
                replyCount = 0,
                categoryName = null,
                authorUsername = "unknown",
                lastPostedAt = Instant.EPOCH,
            ),
            pin,
        )
    }

    fun openUser(project: Project, username: String) {
        openPrepared(project, { userVirtualFile(username) })
    }

    fun openTopicPreview(project: Project, topic: HomeTopic, pin: Boolean = false) {
        openPrepared(project, { topicVirtualFile(topic) }) { manager, file ->
            val runtime = service<IntelliDoRuntime>()
            val before = runtime.topicPreview.snapshot()
            val snapshot = runtime.topicPreview.activate(topic.id, pin)
            val removed = before.tabs.map { it.topicId }.filter { id -> snapshot.tabs.none { tab -> tab.topicId == id } }
            removed.forEach { id -> closeTopic(project, id) }
            manager.openFile(file, true)
            if (pin) {
                (manager as? FileEditorManagerImpl)?.setPinnedEditorTab(file, true)
            }
        }
    }

    fun openDirectory(project: Project, kind: DirectoryKind) {
        openPrepared(project, { directoryVirtualFile(kind) })
    }

    fun openLatest(project: Project) {
        focusHome(project)
        homeEditor(project)?.showLatest()
    }

    fun openCategoryTopics(project: Project, categoryId: Long) {
        focusHome(project)
        homeEditor(project)?.showCategory(categoryId)
    }

    fun openTagTopics(project: Project, tag: String) {
        focusHome(project)
        homeEditor(project)?.showTag(tag)
    }

    fun focusHome(project: Project) {
        openPrepared(project, { homeVirtualFile() }) { manager, file ->
            manager.openFile(file, true)
            (manager as? FileEditorManagerImpl)?.setPinnedEditorTab(file, true)
        }
    }

    fun bringToFront(project: Project) {
        SwingUtilities.invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }
            val frame = WindowManager.getInstance().getFrame(project) ?: return@invokeLater
            frame.extendedState = frame.extendedState and Frame.ICONIFIED.inv()
            frame.isVisible = true
            frame.toFront()
            frame.requestFocus()
        }
    }

    private fun closeRestoredContent(project: Project) {
        val manager = FileEditorManager.getInstance(project)
        manager.openFiles
            .filter { file -> !isHomeFile(file) && !isWelcomeFile(file) }
            .forEach { file -> manager.closeFile(file) }
    }

    private fun reloadHome(project: Project) {
        homeEditor(project)?.reload()
    }

    private fun homeEditor(project: Project): HomeFileEditor? =
        FileEditorManager.getInstance(project).allEditors.filterIsInstance<HomeFileEditor>().firstOrNull()

    private fun showCommunityNav(project: Project) {
        ToolWindowManager.getInstance(project).invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }
            val window = ToolWindowManager.getInstance(project).getToolWindow(CommunityNavModel.TOOL_WINDOW_ID)
                ?: return@invokeLater
            if (!window.isVisible) {
                window.show()
            }
        }
    }

    private fun openTabs(project: Project) {
        if (project.isDisposed || ApplicationManager.getApplication().isDisposed) {
            return
        }
        val wantsWelcome = service<IntelliDoRuntime>().welcomeVisibility.shouldShow()
        ApplicationManager.getApplication().executeOnPooledThread {
            val prepared = runCatching {
                ensureWorkspaceFiles()
                homeVirtualFile() to if (wantsWelcome) welcomeVirtualFile() else null
            }.onFailure { error -> logger.warn("Could not prepare the IntelliDo workspace tabs", error) }
                .getOrNull() ?: return@executeOnPooledThread
            val (home, welcome) = prepared
            if (home == null) {
                return@executeOnPooledThread
            }
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) {
                    return@invokeLater
                }
                val manager = FileEditorManager.getInstance(project)
                manager.openFile(home, false)
                (manager as? FileEditorManagerImpl)?.setPinnedEditorTab(home, true)
                if (welcome != null) {
                    manager.openFile(welcome, true)
                } else {
                    manager.openFile(home, true)
                }
            }
        }
    }

    private fun ensureWorkspaceFiles() {
        val dir = directory()
        Files.createDirectories(dir)
        if (!homePath().exists()) {
            homePath().writeText("")
        }
        if (!welcomePath().exists()) {
            welcomePath().writeText("")
        }
    }

    private fun ensureTopicFile(topic: HomeTopic) {
        ensureWorkspaceFiles()
        val path = topicPath(topic.id)
        path.writeText(topic.title)
    }

    private fun closeTopic(project: Project, topicId: Long) {
        // Resolve through already-open editors rather than VFS: this runs on the EDT.
        val name = IntelliDoWorkspaceLayout.topicFileName(topicId)
        val manager = FileEditorManager.getInstance(project)
        val file = manager.openFiles.firstOrNull { it.name == name } ?: return
        manager.closeFile(file)
    }

    private fun homeVirtualFile(): VirtualFile? = virtualFile(homePath())

    private fun welcomeVirtualFile(): VirtualFile? = virtualFile(welcomePath())

    private fun topicVirtualFile(topic: HomeTopic): VirtualFile? {
        ensureTopicFile(topic)
        return virtualFile(topicPath(topic.id))
    }

    private fun directoryVirtualFile(kind: DirectoryKind): VirtualFile? {
        ensureWorkspaceFiles()
        val path = directoryPath(kind)
        if (!path.exists()) {
            path.writeText("")
        }
        return virtualFile(path)
    }

    private fun userVirtualFile(username: String): VirtualFile? {
        ensureWorkspaceFiles()
        val path = directory().resolve(IntelliDoWorkspaceLayout.userFileName(username))
        if (!path.exists()) {
            path.writeText(username)
        }
        return virtualFile(path)
    }

    private fun browseVirtualFile(url: String): VirtualFile? {
        ensureWorkspaceFiles()
        val path = directory().resolve(IntelliDoWorkspaceLayout.browseFileName(url))
        path.writeText(url)
        return virtualFile(path)
    }

    private fun virtualFile(nio: Path): VirtualFile? = LocalVirtualFiles.find(nio)

    private fun listenForTabClose(project: Project) {
        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                    if (ApplicationManager.getApplication().isDisposed || project.isDisposed) {
                        return
                    }
                    if (isWelcomeFile(file)) {
                        if (project.isOpen) {
                            service<IntelliDoRuntime>().welcomeVisibility.dismiss()
                        }
                        return
                    }
                    if (isTopicFile(file)) {
                        val id = topicIdFrom(file)
                        if (id != null) {
                            service<IntelliDoRuntime>().topicPreview.close(id)
                        }
                        return
                    }
                    if (!isHomeFile(file)) {
                        return
                    }
                    ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed && project.isOpen) {
                            focusHome(project)
                        }
                    }
                }
            },
        )
    }

    private fun isHomeFile(file: VirtualFile): Boolean =
        file.fileType == HomeFileType.INSTANCE || file.name == IntelliDoWorkspaceLayout.HOME_FILE_NAME

    private fun isWelcomeFile(file: VirtualFile): Boolean =
        file.fileType == WelcomeFileType.INSTANCE || file.name == IntelliDoWorkspaceLayout.WELCOME_FILE_NAME

    private fun isTopicFile(file: VirtualFile): Boolean =
        file.fileType == TopicFileType.INSTANCE ||
            file.name.endsWith(".${IntelliDoWorkspaceLayout.TOPIC_EXTENSION}")

    private fun topicIdFrom(file: VirtualFile): Long? =
        file.nameWithoutExtension.removePrefix("topic-").toLongOrNull()
}
