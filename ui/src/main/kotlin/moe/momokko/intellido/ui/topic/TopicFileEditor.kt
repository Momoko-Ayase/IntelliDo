package moe.momokko.intellido.ui.topic

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import moe.momokko.intellido.ui.workspace.IntelliDoWorkspace
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.domain.content.CookedHtml
import moe.momokko.intellido.domain.content.CookedHtmlParser
import moe.momokko.intellido.domain.content.MediaUrls
import moe.momokko.intellido.domain.content.TwemojiAssets
import moe.momokko.intellido.domain.live.GuestLiveEvent
import moe.momokko.intellido.domain.live.LivePresenceUser
import moe.momokko.intellido.domain.live.TopicLiveMerge
import moe.momokko.intellido.domain.live.TopicPresenceState
import moe.momokko.intellido.domain.topic.DiscourseLinks
import moe.momokko.intellido.domain.topic.LinuxDoAvatar
import moe.momokko.intellido.domain.topic.TopicThread
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.ui.startup.IntelliDoRuntime
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent

class TopicFileEditor(
    private val project: Project,
    private val file: VirtualFile,
    private val topicId: Long,
) : UserDataHolderBase(), FileEditor {
    private val listeners = PropertyChangeSupport(this)
    private val host: JBPanel<*> = JBPanel<JBPanel<*>>(BorderLayout())
    @Volatile
    private var loadingMore: Boolean = false
    @Volatile
    private var thread: TopicThread? = null
    @Volatile
    private var panel: TopicPanel? = null
    private val runtime: IntelliDoRuntime = service()
    private val presence = TopicPresenceState()
    private val requestedMedia = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val disposed = AtomicBoolean(false)
    private val liveListener: (List<GuestLiveEvent>) -> Unit = { events -> onLiveEvents(events) }

    init {
        host.border = JBUI.Borders.empty(24)
        host.add(JBLabel(IntelliDoStrings.message("topic.loading", runtime.locale)), BorderLayout.NORTH)
        ApplicationManager.getApplication().executeOnPooledThread {
            runtime.awaitCommunity()
            val first = runCatching { runtime.communityClient.loadTopic(topicId) }
            ApplicationManager.getApplication().invokeLater {
                if (disposed.get() || project.isDisposed) {
                    return@invokeLater
                }
                host.removeAll()
                first.onSuccess { loaded ->
                    thread = loaded
                    host.border = JBUI.Borders.empty()
                    val topicPanel = TopicPanel(
                        loaded,
                        runtime.locale,
                        onNeedMore = { loadMorePosts() },
                        onNeedAround = { index -> loadAround(index) },
                        onNavigate = { url -> IntelliDoWorkspace.openFromUrl(project, url) },
                        onOpenUser = { username -> IntelliDoWorkspace.openUser(project, username) },
                        onNeedReplies = { postId, done -> loadReplies(postId, done) },
                        onLoadOriginal = { url, done -> loadOriginal(url, done) },
                        onCopyLink = { url -> IntelliDoWorkspace.copyText(url) },
                        onSaveAttachment = { url -> IntelliDoWorkspace.saveAttachment(project, url) },
                    )
                    panel = topicPanel
                    host.layout = BorderLayout()
                    host.add(topicPanel, BorderLayout.CENTER)
                    host.revalidate()
                    host.repaint()
                    loadMedia(runtime, loaded, topicPanel)
                    watchLive(loaded)
                }.onFailure { error ->
                    logger.warn("Failed to render topic $topicId from ${file.name}", error)
                    host.add(JBLabel(IntelliDoStrings.message("topic.loadFailed", runtime.locale)), BorderLayout.NORTH)
                    host.revalidate()
                    host.repaint()
                }
            }
        }
    }

    override fun getFile(): VirtualFile = file

    override fun getComponent(): JComponent = host

    override fun getPreferredFocusedComponent(): JComponent = host

    override fun getName(): String = file.nameWithoutExtension

    override fun setState(state: FileEditorState) = Unit

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = !disposed.get()

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        listeners.addPropertyChangeListener(listener)
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        listeners.removePropertyChangeListener(listener)
    }

    override fun dispose() {
        disposed.set(true)
        runtime.liveSession?.removeListener(liveListener)
        runtime.liveSession?.unwatchTopic(topicId)
    }

    fun showFind() {
        panel?.showFind()
    }

    fun copyTopicLink() {
        val current = thread ?: return
        IntelliDoWorkspace.copyText(DiscourseLinks.canonical(current.topic.id, current.topic.slug))
    }

    private fun loadOriginal(url: String, done: (ByteArray?) -> Unit) {
        val loader = runtime.mediaLoader
        if (loader == null || url.isBlank()) {
            done(null)
            return
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            val bytes = runCatching {
                val loaded = loader.load(listOf(url), 4096)
                loaded.entries.firstOrNull { MediaUrls.key(it.key) == MediaUrls.key(url) }?.value
                    ?: loaded.values.firstOrNull()
            }
                .onFailure { logger.warn("Failed to load original image $url", it) }
                .getOrNull()
            ApplicationManager.getApplication().invokeLater {
                if (!disposed.get()) {
                    done(bytes)
                }
            }
        }
    }

    private fun loadReplies(postId: Long, done: (List<moe.momokko.intellido.domain.topic.TopicPost>) -> Unit) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val replies = runCatching { runtime.communityClient.loadPostReplies(postId) }
                .onFailure { logger.warn("Failed to load replies for post $postId", it) }
                .getOrDefault(emptyList())
            ApplicationManager.getApplication().invokeLater {
                if (!disposed.get()) {
                    done(replies)
                }
            }
        }
    }

    private fun loadMorePosts() {
        val current = thread ?: return
        val topicPanel = panel ?: return
        if (loadingMore || !topicPanel.hasMorePosts()) {
            return
        }
        loadingMore = true
        ApplicationManager.getApplication().executeOnPooledThread {
            val next = runCatching { runtime.communityClient.loadNextPosts(current) }
            ApplicationManager.getApplication().invokeLater {
                loadingMore = false
                if (disposed.get()) {
                    return@invokeLater
                }
                next.onSuccess { updated ->
                    if (updated.posts.size <= current.posts.size) {
                        topicPanel.notifyEmptyFetch(current.posts.size)
                        return@onSuccess
                    }
                    thread = updated
                    topicPanel.appendPosts(updated)
                    loadMedia(runtime, updated, topicPanel)
                }.onFailure { error ->
                    logger.warn("Failed to load more posts for topic $topicId", error)
                }
            }
        }
    }

    private fun loadAround(streamIndex: Int) {
        val current = thread ?: return
        val topicPanel = panel ?: return
        if (loadingMore) {
            return
        }
        loadingMore = true
        ApplicationManager.getApplication().executeOnPooledThread {
            val next = runCatching { runtime.communityClient.loadPostsAround(current, streamIndex) }
            ApplicationManager.getApplication().invokeLater {
                loadingMore = false
                if (disposed.get()) {
                    return@invokeLater
                }
                next.onSuccess { updated ->
                    thread = updated
                    topicPanel.reveal(updated, streamIndex)
                    if (updated.posts.size > current.posts.size) {
                        loadMedia(runtime, updated, topicPanel)
                    }
                }.onFailure { error ->
                    logger.warn("Failed to load posts around $streamIndex for topic $topicId", error)
                }
            }
        }
    }

    private fun loadMedia(
        runtime: IntelliDoRuntime,
        thread: TopicThread,
        panel: TopicPanel,
    ) {
        val loader = runtime.mediaLoader ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            val parser = CookedHtmlParser()
            val documents = thread.posts.map { post ->
                parser.parse(post.cookedHtml.ifBlank { "<p>${post.plainText}</p>" })
            }
            val avatars = (
                thread.posts.mapNotNull { it.avatarUrl() } +
                    thread.posts.flatMap { post -> post.boosts.mapNotNull { it.avatarUrl(48) } } +
                    thread.posts.mapNotNull { it.replyTo?.avatarUrl(48) }
                ).distinct().let { fresh(it) }
            val images = (
                documents.flatMap { CookedHtml.trustedMediaUrls(it) } +
                    thread.posts.flatMap { CookedHtml.uploadUrls(it.cookedHtml) }
                ).distinct().take(12).let { fresh(it) }
            val emojis = documents.flatMap { CookedHtml.emojiUrls(it) }.distinct()
                .filterNot { TwemojiAssets.has("", it) }
                .take(80)
                .let { fresh(it) }
            if (avatars.isEmpty() && images.isEmpty() && emojis.isEmpty()) {
                return@executeOnPooledThread
            }
            logger.info("Topic $topicId media: ${avatars.size} avatars, ${images.size} images, ${emojis.size} emoji")
            val avatarBytes = runCatching { loader.load(avatars, 96) }.getOrDefault(emptyMap())
            if (avatarBytes.isNotEmpty()) {
                ApplicationManager.getApplication().invokeLater {
                    if (!disposed.get()) {
                        panel.putMedia(avatarBytes)
                    }
                }
            }
            val imageBytes = runCatching { loader.load(images, 800) }.getOrDefault(emptyMap())
            if (imageBytes.isNotEmpty()) {
                ApplicationManager.getApplication().invokeLater {
                    if (!disposed.get()) {
                        panel.putMedia(imageBytes)
                    }
                }
            }
            val emojiBytes = runCatching { loader.load(emojis, 48) }.getOrDefault(emptyMap())
            if (emojiBytes.isNotEmpty()) {
                ApplicationManager.getApplication().invokeLater {
                    if (!disposed.get()) {
                        panel.putMedia(emojiBytes)
                    }
                }
            }
            logger.info(
                "Topic $topicId loaded ${avatarBytes.size} avatar files, ${imageBytes.size} image files, ${emojiBytes.size} emoji files",
            )
        }
    }

    /**
     * Live post events re-walk the whole thread, so drop anything already
     * requested: otherwise every incoming reply re-downloads all thread media.
     */
    private fun fresh(urls: List<String>): List<String> =
        urls.filter { url -> requestedMedia.add(MediaUrls.key(url)) }

    private fun watchLive(loaded: TopicThread) {
        if (disposed.get()) {
            return
        }
        val session = runtime.liveSession ?: return
        session.watchTopic(topicId, loaded.messageBusLastId)
        session.addListener(liveListener)
    }

    private fun onLiveEvents(events: List<GuestLiveEvent>) {
        if (disposed.get()) {
            return
        }
        val mine = events.filter { event ->
            when (event) {
                is GuestLiveEvent.TopicPostChanged -> event.topicId == topicId
                is GuestLiveEvent.TopicPresence -> event.topicId == topicId
                else -> false
            }
        }
        if (mine.isEmpty()) {
            return
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            if (disposed.get()) {
                return@executeOnPooledThread
            }
            mine.filterIsInstance<GuestLiveEvent.TopicPostChanged>().forEach { event ->
                applyPostEvent(event)
            }
            val presenceEvents = mine.filterIsInstance<GuestLiveEvent.TopicPresence>()
            if (presenceEvents.isNotEmpty()) {
                synchronized(presence) {
                    presenceEvents.forEach { presence.apply(it) }
                }
                val users = synchronized(presence) { presence.snapshot() }
                ApplicationManager.getApplication().invokeLater {
                    if (!disposed.get()) {
                        panel?.showPresence(users)
                    }
                }
                loadPresenceAvatars(users)
            }
        }
    }

    private fun applyPostEvent(event: GuestLiveEvent.TopicPostChanged) {
        if (event.destroyed || event.deleted) {
            ApplicationManager.getApplication().invokeLater {
                if (disposed.get()) {
                    return@invokeLater
                }
                val current = thread ?: return@invokeLater
                val topicPanel = panel ?: return@invokeLater
                thread = if (event.destroyed) {
                    TopicLiveMerge.remove(current, event.postId)
                } else {
                    TopicLiveMerge.hide(current, event.postId)
                }
                if (event.destroyed) {
                    topicPanel.removePost(event.postId)
                } else {
                    topicPanel.markDeleted(event.postId)
                }
            }
            return
        }
        val title = thread?.topic?.title ?: return
        val posts = runCatching {
            runtime.communityClient.loadTopicPosts(topicId, listOf(event.postId), title)
        }.onFailure { error ->
            logger.warn("Failed to fetch live post ${event.postId} for topic $topicId", error)
        }.getOrDefault(emptyList())
        if (posts.isEmpty() || disposed.get()) {
            return
        }
        ApplicationManager.getApplication().invokeLater {
            if (disposed.get()) {
                return@invokeLater
            }
            val current = thread ?: return@invokeLater
            val topicPanel = panel ?: return@invokeLater
            val updated = if (event.created) {
                TopicLiveMerge.insert(current, posts)
            } else {
                TopicLiveMerge.replace(current, posts)
            }
            thread = updated
            if (event.created) {
                topicPanel.appendPosts(updated)
                topicPanel.noteIncomingPosts()
            } else {
                posts.forEach { topicPanel.replacePost(it) }
            }
            loadMedia(runtime, updated, topicPanel)
        }
    }


    private fun loadPresenceAvatars(users: List<LivePresenceUser>) {
        val loader = runtime.mediaLoader ?: return
        val urls = users.mapNotNull { LinuxDoAvatar.url(it.avatarTemplate, 48) }
        if (urls.isEmpty()) {
            return
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            val bytes = runCatching { loader.load(urls, 48) }.getOrDefault(emptyMap())
            if (bytes.isEmpty()) {
                return@executeOnPooledThread
            }
            ApplicationManager.getApplication().invokeLater {
                if (!disposed.get()) {
                    panel?.showPresence(synchronized(presence) { presence.snapshot() }, bytes)
                }
            }
        }
    }

    companion object {
        private val logger = Logger.getInstance(TopicFileEditor::class.java)
    }
}
