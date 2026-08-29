package moe.momokko.intellido.ui.topic

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.domain.content.Attachments
import moe.momokko.intellido.domain.content.CookedDocument
import moe.momokko.intellido.domain.content.CookedHtml
import moe.momokko.intellido.domain.content.CookedHtmlParser
import moe.momokko.intellido.domain.content.MediaUrls
import moe.momokko.intellido.domain.content.TwemojiAssets
import moe.momokko.intellido.domain.icon.FaGlyphs
import moe.momokko.intellido.domain.live.TopicLiveMerge
import moe.momokko.intellido.domain.topic.DiscourseLinks
import moe.momokko.intellido.domain.topic.HomeTopic
import moe.momokko.intellido.domain.topic.PostIdentity
import moe.momokko.intellido.domain.topic.TopicFind
import moe.momokko.intellido.domain.topic.TopicFindHit
import moe.momokko.intellido.domain.topic.TopicPost
import moe.momokko.intellido.domain.topic.TopicThread
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.platform.reading.ReadingAppearance
import moe.momokko.intellido.platform.time.RelativeTime
import moe.momokko.intellido.platform.topic.DiscourseNumber
import moe.momokko.intellido.platform.topic.TopicActionLabels
import moe.momokko.intellido.ui.content.PostBodyPane
import moe.momokko.intellido.ui.content.PostCodePane
import moe.momokko.intellido.ui.content.PostFoldPane
import moe.momokko.intellido.ui.content.PostImage
import moe.momokko.intellido.ui.content.PostPollPane
import moe.momokko.intellido.ui.guest.FaMark
import moe.momokko.intellido.ui.guest.GuestAvatar
import moe.momokko.intellido.ui.guest.GuestUi
import moe.momokko.intellido.ui.content.InlineMedia
import moe.momokko.intellido.ui.guest.InitialsAvatar
import moe.momokko.intellido.ui.guest.LoadPulse
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.util.Locale
import javax.swing.AbstractAction
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JViewport
import javax.swing.KeyStroke
import javax.swing.Scrollable
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

class TopicPanel(
    private var thread: TopicThread,
    private val locale: Locale,
    private val onNeedMore: () -> Unit = {},
    private val onNeedAround: (Int) -> Unit = {},
    private val onNavigate: (String) -> Boolean = { false },
    private val onOpenUser: (String) -> Unit = {},
    private val onNeedReplies: (Long, (List<TopicPost>) -> Unit) -> Unit = { _, cb -> cb(emptyList()) },
    private val onLoadOriginal: (String, (ByteArray?) -> Unit) -> Unit = { _, done -> done(null) },
    private val onCopyLink: (String) -> Unit = {},
    private val onSaveAttachment: (String) -> Unit = {},
    private val onSearchTopic: (String) -> List<TopicFindHit> = { query -> TopicFind.search(thread.posts, query) },
) : JBPanel<TopicPanel>(BorderLayout()) {
    private val avatarHosts = mutableListOf<GuestAvatar>()
    private val boostPanes = mutableListOf<BoostListPane>()
    private val imageSlots = mutableListOf<ImageSlot>()
    private val bodies = mutableListOf<Pair<PostBodyPane, CookedDocument>>()
    private val postViews = mutableListOf<JComponent>()
    private val mediaBytes = linkedMapOf<String, ByteArray>()
    private val parser = CookedHtmlParser()
    private val stream = WidthTrackingPanel()
    private val scroll: JBScrollPane
    private val timeline: TopicTimeline?
    private val positionLabel = JBLabel()
    private var topicMapView: JComponent? = null
    private val loadPulse = LoadPulse(IntelliDoStrings.message("topic.loading", locale))
    private val presenceBar = TopicPresenceBar(locale)
    private val findBar: TopicFindBar
    private val newPostsBar: TopicNewPostsBar
    private var findResults: List<TopicFindHit> = emptyList()
    private var findIndex: Int = 0
    private var incomingPosts: Int = 0
    private var noMoreAfter: Boolean = false
    private var noMoreBefore: Boolean = false

    init {
        border = JBUI.Borders.empty(12, 24, 20, 20)
        preferredSize = Dimension(JBUI.scale(640), JBUI.scale(480))
        stream.add(topicHeader(thread))
        if (thread.posts.isEmpty()) {
            stream.add(JBLabel(IntelliDoStrings.message("topic.empty", locale)))
        } else {
            thread.posts.sortedBy { it.postNumber }.forEach { post ->
                val view = postView(post, parser, locale)
                postViews += view
                stream.add(view)
                if (post.postNumber == 1) {
                    topicMapView = topicMap(thread.topic, locale).also { stream.add(it) }
                }
            }
        }
        refreshSkeletons()
        scroll = JBScrollPane(
            stream,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
        )
        scroll.border = JBUI.Borders.empty()
        scroll.viewportBorder = JBUI.Borders.empty()
        findBar = TopicFindBar(
            locale,
            onQuery = { query -> runFind(query) },
            onNext = { stepFind(1) },
            onPrevious = { stepFind(-1) },
            onClose = { findBar.hideBar() },
        )
        newPostsBar = TopicNewPostsBar(locale) { consumeIncoming() }
        add(findBar, BorderLayout.NORTH)
        add(scroll, BorderLayout.CENTER)
        val south = JBPanel<JBPanel<*>>(BorderLayout())
        south.isOpaque = false
        south.add(newPostsBar, BorderLayout.NORTH)
        south.add(presenceBar, BorderLayout.SOUTH)
        add(south, BorderLayout.SOUTH)
        val findStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F, GuestUi.menuShortcutMask())
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(findStroke, "find-topic")
        actionMap.put(
            "find-topic",
            object : AbstractAction() {
                override fun actionPerformed(event: java.awt.event.ActionEvent) {
                    showFind()
                }
            },
        )
        isFocusable = true
        timeline = if (thread.topic.postsCount > 1 || thread.posts.size > 1 || thread.streamIds.size > 1) {
            TopicTimeline(thread, locale) { index, load -> jumpToStream(index, load) }.also {
                add(it, BorderLayout.EAST)
            }
        } else {
            null
        }
        scroll.verticalScrollBar.addAdjustmentListener {
            syncTimeline()
            maybeLoadMore()
        }
        SwingUtilities.invokeLater { scrollToTop() }
    }

    fun hasMorePosts(): Boolean = thread.posts.size < thread.streamIds.size

    fun replacePost(post: TopicPost) {
        val index = postViews.indexOfFirst { (it.getClientProperty(POST_KEY) as? TopicPost)?.id == post.id }
        if (index < 0) {
            appendPosts(TopicLiveMerge.insert(thread, listOf(post)))
            return
        }
        thread = TopicLiveMerge.replace(thread, listOf(post))
        val old = postViews[index]
        forgetView(old)
        val next = postView(post, parser, locale)
        next.isVisible = old.isVisible
        val streamIndex = (0 until stream.componentCount).firstOrNull { stream.getComponent(it) === old }
        postViews[index] = next
        if (streamIndex != null) {
            stream.remove(old)
            stream.add(next, streamIndex)
        }
        revalidate()
        repaint()
        syncTimeline()
    }

    fun showPresence(
        users: List<moe.momokko.intellido.domain.live.LivePresenceUser>,
        avatars: Map<String, ByteArray> = emptyMap(),
    ) {
        presenceBar.showUsers(users, avatars)
    }

    fun isNearBottom(): Boolean {
        val bar = scroll.verticalScrollBar
        if (bar.maximum <= 0) {
            return true
        }
        return bar.value + bar.visibleAmount >= bar.maximum - JBUI.scale(64)
    }

    fun scrollToLast() {
        val last = postViews.lastOrNull { it.isVisible } ?: return
        SwingUtilities.invokeLater {
            scroll.verticalScrollBar.value = last.y.coerceAtLeast(0)
            syncTimeline()
        }
    }

    fun appendPosts(next: TopicThread) {
        val pos = scroll.viewport.viewPosition
        val have = thread.posts.map { it.id }.toSet()
        val extra = next.posts.filter { it.id !in have }.sortedBy { it.postNumber }
        thread = next
        extra.forEach { post ->
            insertPostView(postView(post, parser, locale))
        }
        topicMapView?.isVisible = postViews.any { view ->
            (view.getClientProperty(POST_KEY) as? TopicPost)?.postNumber == 1 && view.isVisible
        }
        refreshSkeletons()
        revalidate()
        repaint()
        scroll.viewport.viewPosition = pos
        syncTimeline()
    }

    fun reveal(next: TopicThread, focusStreamIndex: Int) {
        val keepViewport = postViews.any { it.isVisible }
        val pos = scroll.viewport.viewPosition
        val beforeHeight = stream.preferredSize.height
        val firstVisibleId = (postViews.firstOrNull { it.isVisible }?.getClientProperty(POST_KEY) as? TopicPost)?.id
        val have = thread.posts.map { it.id }.toSet()
        val extra = next.posts.filter { it.id !in have }.sortedBy { it.postNumber }
        thread = next
        if (extra.isEmpty()) {
            notifyEmptyFetch(focusStreamIndex)
            return
        }
        noMoreAfter = false
        noMoreBefore = false
        extra.forEach { post ->
            insertPostView(postView(post, parser, locale))
        }
        applyWindow(focusStreamIndex, scrollToFocus = !keepViewport)
        if (keepViewport) {
            stream.validate()
            val firstIndex = firstVisibleId?.let { thread.streamIds.indexOf(it) } ?: 0
            val insertedAbove = extra.any { thread.streamIds.indexOf(it.id) in 0 until firstIndex }
            if (insertedAbove) {
                val delta = (stream.preferredSize.height - beforeHeight).coerceAtLeast(0)
                scroll.viewport.viewPosition = Point(pos.x, pos.y + delta)
            } else {
                scroll.viewport.viewPosition = pos
            }
            syncTimeline()
        }
    }

    fun jumpToStream(index: Int, load: Boolean = true) {
        if (thread.streamIds.isEmpty()) {
            return
        }
        val clamped = index.coerceIn(0, thread.streamIds.lastIndex)
        val id = thread.streamIds[clamped]
        val view = postViews.find { (it.getClientProperty(POST_KEY) as? TopicPost)?.id == id }
        updatePositionLabel(clamped)
        if (view != null) {
            applyWindow(clamped)
            return
        }
        if (!load) {
            return
        }
        if (noMoreAfter && clamped >= visibleStreamEnd().coerceAtLeast(0)) {
            return
        }
        if (noMoreBefore && clamped <= visibleStreamStart()) {
            return
        }
        applyWindow(clamped)
        onNeedAround(clamped)
    }

    fun notifyEmptyFetch(aroundIndex: Int) {
        val end = visibleStreamEnd()
        val start = visibleStreamStart()
        if (aroundIndex >= end) {
            noMoreAfter = true
        }
        if (aroundIndex <= start) {
            noMoreBefore = true
        }
        if (postViews.none { it.isVisible }) {
            val loadedId = thread.posts.maxByOrNull { it.postNumber }?.id
            val loadedIndex = loadedId?.let { thread.streamIds.indexOf(it) } ?: -1
            if (loadedIndex >= 0) {
                applyWindow(loadedIndex, scrollToFocus = true)
                return
            }
            postViews.forEach { it.isVisible = true }
            topicMapView?.isVisible = true
        }
        refreshSkeletons()
        revalidate()
        repaint()
        syncTimeline()
    }

    fun timelineBar(): TopicTimeline? = timeline

    fun putMedia(bytesByUrl: Map<String, ByteArray>) {
        if (bytesByUrl.isEmpty()) {
            return
        }
        mediaBytes.putAll(bytesByUrl)
        bytesByUrl.forEach { (url, bytes) -> InlineMedia.put(url, bytes) }
        avatarHosts.forEach { host -> host.apply(bytesByUrl) }
        boostPanes.forEach { pane -> pane.applyMedia(bytesByUrl) }
        imageSlots.forEach { slot -> slot.apply(bytesByUrl) }
        val uris = mediaLookup()
        if (uris.isNotEmpty()) {
            bodies.forEach { (pane, document) ->
                pane.update(document, uris)
            }
        }
        revalidate()
        repaint()
    }

    fun scrollToTop() {
        scroll.verticalScrollBar.value = 0
        scroll.viewport.viewPosition = Point(0, 0)
    }

    fun showFind() {
        findBar.showBar()
        revalidate()
        repaint()
    }

    fun find(query: String) {
        showFind()
        runFind(query)
    }

    fun findHits(): List<TopicFindHit> = findResults

    fun noteIncomingPosts(count: Int = 1) {
        incomingPosts += count.coerceAtLeast(1)
        newPostsBar.setCount(incomingPosts)
        revalidate()
        repaint()
    }

    fun incomingCount(): Int = incomingPosts

    fun consumeIncoming() {
        incomingPosts = 0
        newPostsBar.setCount(0)
        scrollToLast()
        revalidate()
        repaint()
    }

    fun markDeleted(postId: Long) {
        thread = TopicLiveMerge.hide(thread, postId)
        thread.posts.firstOrNull { it.id == postId }?.let { replacePost(it) }
    }

    fun removePost(postId: Long) {
        thread = TopicLiveMerge.remove(thread, postId)
        val index = postViews.indexOfFirst { (it.getClientProperty(POST_KEY) as? TopicPost)?.id == postId }
        if (index < 0) {
            return
        }
        val old = postViews.removeAt(index)
        forgetView(old)
        stream.remove(old)
        revalidate()
        repaint()
        syncTimeline()
    }

    private fun handleNavigate(url: String): Boolean {
        if (Attachments.isAttachmentUrl(url)) {
            onSaveAttachment(url)
            return true
        }
        return onNavigate(url)
    }

    private fun runFind(query: String) {
        findResults = onSearchTopic(query)
        findIndex = 0
        findBar.setStatus(findIndex, findResults.size)
        findResults.firstOrNull()?.let { jumpToPostNumber(it.postNumber) }
    }

    private fun stepFind(delta: Int) {
        if (findResults.isEmpty()) {
            runFind(findBar.query())
            return
        }
        findIndex = Math.floorMod(findIndex + delta, findResults.size)
        findBar.setStatus(findIndex, findResults.size)
        jumpToPostNumber(findResults[findIndex].postNumber)
    }

    private fun topicHeader(thread: TopicThread): JComponent {
        val header = JBPanel<JBPanel<*>>()
        header.layout = BoxLayout(header, BoxLayout.Y_AXIS)
        header.isOpaque = false
        header.alignmentX = LEFT_ALIGNMENT
        header.maximumSize = Dimension(readingCap(), Integer.MAX_VALUE)
        header.border = JBUI.Borders.empty(4, 0, 16, 0)

        val title = JBLabel(thread.topic.title)
        title.font = GuestUi.titleFont(title.font).deriveFont(Font.BOLD, 22f)
        title.setAllowAutoWrapping(true)
        val heading = JBPanel<JBPanel<*>>(BorderLayout())
        heading.isOpaque = false
        heading.alignmentX = LEFT_ALIGNMENT
        if (thread.topic.pinned) {
            val pin = JBPanel<JBPanel<*>>(BorderLayout())
            pin.isOpaque = false
            pin.border = JBUI.Borders.empty(6, 0, 0, 8)
            pin.add(FaMark("thumbtack", GuestUi.muted, 14), BorderLayout.NORTH)
            heading.add(pin, BorderLayout.WEST)
        }
        heading.add(title, BorderLayout.CENTER)
        val total = thread.topic.postsCount.coerceAtLeast(thread.posts.size).coerceAtLeast(1)
        val firstNo = thread.posts.firstOrNull()?.postNumber ?: 1
        positionLabel.text = IntelliDoStrings.message("topic.timeline.position", locale, firstNo, total)
        positionLabel.foreground = GuestUi.muted
        positionLabel.font = GuestUi.metaFont(positionLabel.font)
        heading.add(positionLabel, BorderLayout.EAST)

        val meta = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 8, 4))
        meta.isOpaque = false
        meta.alignmentX = LEFT_ALIGNMENT
        thread.topic.categoryName?.let { name ->
            meta.add(
                GuestUi.categoryBadge(
                    name,
                    thread.topic.categoryColor,
                    thread.topic.categoryIcon,
                    restricted = thread.topic.categoryRestricted,
                ),
            )
        }
        thread.topic.tags.forEach { tag ->
            meta.add(GuestUi.tagBadge(tag))
        }
        val copy = JBLabel(IntelliDoStrings.message("topic.copyLink", locale))
        copy.foreground = GuestUi.muted
        copy.font = GuestUi.metaFont(copy.font)
        copy.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        copy.getAccessibleContext().accessibleName = copy.text
        copy.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                onCopyLink(DiscourseLinks.canonical(thread.topic.id, thread.topic.slug))
            }
        })
        meta.add(copy)
        header.add(heading)
        header.add(meta)
        if (thread.topic.closed) {
            header.add(statusBanner(IntelliDoStrings.message("topic.closed", locale), "lock"))
        }
        if (thread.topic.archived) {
            header.add(statusBanner(IntelliDoStrings.message("topic.archived", locale), "box-archive"))
        }
        return header
    }

    private fun statusBanner(text: String, icon: String): JComponent {
        val row = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0))
        row.isOpaque = false
        row.alignmentX = LEFT_ALIGNMENT
        row.border = JBUI.Borders.empty(8, 0, 4, 0)
        row.add(FaMark(icon, GuestUi.muted, 12))
        val label = JBLabel(text)
        label.foreground = GuestUi.muted
        label.font = GuestUi.metaFont(label.font)
        row.add(label)
        return row
    }

    private fun postView(post: TopicPost, parser: CookedHtmlParser, locale: Locale): JComponent {
        if (post.isSmallAction) {
            return smallActionView(post, locale)
        }
        val postPanel = JBPanel<JBPanel<*>>(BorderLayout())
        postPanel.alignmentX = LEFT_ALIGNMENT
        postPanel.maximumSize = Dimension(readingCap(), Integer.MAX_VALUE)
        postPanel.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
            JBUI.Borders.empty(16, 0, 18, 4),
        )
        val avatar = GuestAvatar(post.username, post.avatarUrl(), InitialsAvatar.SIZE, 12)
        avatar.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        avatar.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                onOpenUser(post.username)
            }
        })
        avatarHosts += avatar
        postPanel.add(avatar, BorderLayout.WEST)

        val body = JBPanel<JBPanel<*>>(BorderLayout())
        body.isOpaque = false
        body.add(postMeta(post, locale), BorderLayout.NORTH)
        if (post.hidden || post.userDeleted) {
            val notice = JBLabel(
                IntelliDoStrings.message(if (post.userDeleted) "topic.deleted" else "topic.hidden", locale),
            )
            notice.foreground = GuestUi.muted
            notice.border = JBUI.Borders.empty(8, 0, 0, 0)
            body.add(notice, BorderLayout.CENTER)
            val actions = postActions(post, locale)
            if (actions.componentCount > 0) {
                body.add(actions, BorderLayout.SOUTH)
            }
            postPanel.add(body, BorderLayout.CENTER)
            postPanel.putClientProperty(POST_KEY, post)
            return postPanel
        }
        val cooked = post.cookedHtml.ifBlank { "<p>${post.plainText}</p>" }
        val document = parser.parse(cooked)
        registerBundledTwemoji(document)
        val column = JBPanel<JBPanel<*>>()
        column.layout = BoxLayout(column, BoxLayout.Y_AXIS)
        column.isOpaque = false
        column.alignmentX = LEFT_ALIGNMENT
        column.border = JBUI.Borders.empty(6, 0, 0, 0)
        val parts = CookedHtml.nativeParts(document)
        val seen = collectImageSrcs(parts)
        addParts(column, parts, locale)
        CookedHtml.uploadUrls(post.cookedHtml).filter { it !in seen }.forEach { src ->
            addImageSlot(column, src, MediaUrls.original(src))
        }
        body.add(column, BorderLayout.CENTER)
        val actions = postActions(post, locale)
        if (actions.componentCount > 0) {
            body.add(actions, BorderLayout.SOUTH)
        }
        postPanel.add(body, BorderLayout.CENTER)
        postPanel.putClientProperty(POST_KEY, post)
        return postPanel
    }

    private fun addParts(column: JBPanel<*>, parts: List<CookedHtml.Part>, locale: Locale) {
        parts.forEach { part ->
            when (part) {
                is CookedHtml.Part.Html -> {
                    if (part.document.blocks.isNotEmpty()) {
                        val pane = PostBodyPane(part.document, ::handleNavigate, mediaLookup())
                        pane.alignmentX = LEFT_ALIGNMENT
                        bodies += pane to part.document
                        column.add(pane.wrapped())
                    }
                }
                is CookedHtml.Part.Image -> addImageSlot(column, part.src, part.originalSrc)
                is CookedHtml.Part.Code -> {
                    val pane = PostCodePane(part.code, part.language)
                    pane.alignmentX = LEFT_ALIGNMENT
                    column.add(pane)
                }
                is CookedHtml.Part.Details -> {
                    val summary = part.summary.ifBlank { IntelliDoStrings.message("content.details", locale) }
                    column.add(foldPane(summary, summary, part.inner, locale, part.initiallyOpen))
                }
                is CookedHtml.Part.Spoiler -> {
                    column.add(
                        foldPane(
                            IntelliDoStrings.message("content.spoiler", locale),
                            IntelliDoStrings.message("content.spoiler.hide", locale),
                            part.inner,
                            locale,
                        ),
                    )
                }
                is CookedHtml.Part.Poll -> {
                    column.add(
                        PostPollPane(
                            part.title ?: IntelliDoStrings.message("content.poll", locale),
                            part.options,
                            part.multiple,
                            part.status,
                        ),
                    )
                }
            }
        }
    }

    private fun foldPane(
        closed: String,
        open: String,
        inner: List<CookedHtml.Part>,
        locale: Locale,
        initiallyOpen: Boolean = false,
    ): PostFoldPane {
        val body = JBPanel<JBPanel<*>>()
        body.layout = BoxLayout(body, BoxLayout.Y_AXIS)
        body.isOpaque = false
        addParts(body, inner, locale)
        return PostFoldPane(closed, open, body, initiallyOpen)
    }

    private fun collectImageSrcs(parts: List<CookedHtml.Part>): Set<String> {
        val urls = linkedSetOf<String>()
        fun walk(items: List<CookedHtml.Part>) {
            items.forEach { part ->
                when (part) {
                    is CookedHtml.Part.Image -> urls += part.src
                    is CookedHtml.Part.Details -> walk(part.inner)
                    is CookedHtml.Part.Spoiler -> walk(part.inner)
                    else -> Unit
                }
            }
        }
        walk(parts)
        return urls
    }

    private fun addImageSlot(column: JBPanel<*>, src: String, originalSrc: String = src) {
        val slot = ImageSlot(src, originalSrc, onLoadOriginal)
        imageSlots += slot
        column.add(slot)
    }

    private fun postMeta(post: TopicPost, locale: Locale): JComponent {
        val names = ClipBar()
        val identity = PostIdentity.names(post.username, post.displayName)
        val primary = JBLabel(identity.primary)
        primary.font = primary.font.deriveFont(Font.BOLD, (primary.font.size + 1).toFloat())
        primary.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        primary.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                if (javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    onOpenUser(post.username)
                }
            }
        })
        names.add(primary)
        if (post.staff) {
            names.add(FaMark("shield-halved", GuestUi.muted, 12))
        }
        flairIcon(post)?.let { names.add(it) }
        if (post.acceptedAnswer) {
            names.add(FaMark("check", HEAT, 12))
            val accepted = JBLabel(IntelliDoStrings.message("topic.accepted", locale))
            accepted.foreground = HEAT
            accepted.font = GuestUi.metaFont(accepted.font)
            names.add(accepted)
        }
        if (post.wiki) {
            val wiki = JBLabel(IntelliDoStrings.message("topic.wiki", locale))
            wiki.foreground = GuestUi.muted
            wiki.font = GuestUi.metaFont(wiki.font)
            names.add(wiki)
        }
        if (post.postNumber > 1 && post.username == thread.topic.authorUsername) {
            val owner = JBLabel(IntelliDoStrings.message("topic.owner", locale))
            owner.foreground = GuestUi.signal
            owner.font = GuestUi.metaFont(owner.font)
            names.add(owner)
        }
        identity.secondary?.let { username ->
            val handle = JBLabel(username)
            handle.foreground = GuestUi.muted
            handle.font = GuestUi.metaFont(handle.font)
            names.add(handle)
        }
        post.userTitle?.takeIf { it.isNotBlank() }?.let { title ->
            val badge = JBLabel(title)
            badge.foreground = GuestUi.muted
            badge.font = GuestUi.metaFont(badge.font)
            names.add(badge)
        }
        post.replyTo?.let { reply ->
            val replyLabel = JBLabel(IntelliDoStrings.message("topic.replyTo", locale, reply.username))
            replyLabel.foreground = GuestUi.signal
            replyLabel.font = GuestUi.metaFont(replyLabel.font)
            replyLabel.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
            replyLabel.addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    jumpToPostNumber(reply.postNumber)
                }
            })
            names.add(FaMark("reply", GuestUi.muted, 11))
            names.add(replyLabel)
        }
        val infos = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 8, 0))
        infos.isOpaque = false
        if (post.edited) {
            infos.add(FaMark("pencil", GuestUi.muted, 11))
            val edits = JBLabel((post.version - 1).toString())
            edits.foreground = GuestUi.muted
            edits.font = GuestUi.metaFont(edits.font)
            edits.toolTipText = IntelliDoStrings.message("topic.edited", locale)
            infos.add(edits)
        }
        val time = JBLabel(RelativeTime.format(post.createdAt, locale = locale))
        time.foreground = GuestUi.muted
        time.font = GuestUi.metaFont(time.font)
        val number = JBLabel("#${post.postNumber}", SwingConstants.RIGHT)
        number.foreground = GuestUi.muted
        number.font = GuestUi.metaFont(number.font)
        infos.add(time)
        infos.add(number)
        val infoW = JBUI.scale(INFO_COL)
        infos.preferredSize = Dimension(infoW, JBUI.scale(20))
        infos.minimumSize = Dimension(infoW, JBUI.scale(20))
        infos.maximumSize = Dimension(infoW, JBUI.scale(40))
        val meta = JBPanel<JBPanel<*>>(BorderLayout(JBUI.scale(8), 0))
        meta.isOpaque = false
        meta.add(names, BorderLayout.CENTER)
        meta.add(infos, BorderLayout.EAST)
        return meta
    }

    private fun smallActionView(post: TopicPost, locale: Locale): JComponent {
        val row = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0))
        row.alignmentX = LEFT_ALIGNMENT
        row.isOpaque = false
        row.maximumSize = Dimension(readingCap(), JBUI.scale(40))
        row.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
            JBUI.Borders.empty(8, 0, 8, 4),
        )
        val avatar = GuestAvatar(post.username, post.avatarUrl(48), 24, 8)
        avatarHosts += avatar
        row.add(avatar)
        actionIcon(post.actionCode)?.let { icon ->
            row.add(FaMark(icon, GuestUi.muted, 12))
        }
        val text = JBLabel(smallActionText(post, locale))
        text.foreground = GuestUi.muted
        text.font = GuestUi.metaFont(text.font)
        row.add(text)
        row.putClientProperty(POST_KEY, post)
        return row
    }

    private fun smallActionText(post: TopicPost, locale: Locale): String {
        val label = TopicActionLabels.label(post.actionCode, locale, post.plainText.takeUnless { it == post.actionCode }.orEmpty())
        val time = RelativeTime.format(post.createdAt, locale = locale)
        return if (locale.language == "zh") {
            val stamped = when {
                time.endsWith("分钟") || time.endsWith("小时") || time.endsWith("天") -> "${time}前"
                else -> time
            }
            stamped + label
        } else {
            "$time $label"
        }
    }

    private fun actionIcon(code: String?): String? = TopicActionLabels.icon(code)

    private fun postActions(post: TopicPost, locale: Locale): JBPanel<*> {
        val column = JBPanel<JBPanel<*>>()
        column.layout = BoxLayout(column, BoxLayout.Y_AXIS)
        column.isOpaque = false
        column.alignmentX = LEFT_ALIGNMENT
        column.border = JBUI.Borders.empty(8, 0, 0, 0)
        val row = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 12, 0))
        row.isOpaque = false
        row.alignmentX = LEFT_ALIGNMENT
        if (post.reactions.isNotEmpty()) {
            row.add(ReactionRow(post.reactions, post.visibleReactionCount))
        } else if (post.likeCount > 0) {
            val like = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 4, 0))
            like.isOpaque = false
            like.add(FaMark("heart", HEART, 14))
            val count = JBLabel(DiscourseNumber.compact(post.likeCount))
            count.foreground = GuestUi.muted
            count.font = GuestUi.metaFont(count.font)
            like.add(count)
            row.add(like)
        }
        val copy = JBLabel(IntelliDoStrings.message("topic.copyLink", locale))
        copy.foreground = GuestUi.muted
        copy.font = GuestUi.metaFont(copy.font)
        copy.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        copy.getAccessibleContext().accessibleName = copy.text
        copy.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                onCopyLink(DiscourseLinks.canonical(thread.topic.id, thread.topic.slug, post.postNumber))
            }
        })
        row.add(FaMark("link", GuestUi.muted, 12))
        row.add(copy)
        if (row.componentCount > 0) {
            column.add(row)
        }
        if (post.replyCount > 0) {
            column.add(ReplyListPane(post, locale, ::jumpToPostNumber, onNeedReplies))
        }
        if (post.boosts.isNotEmpty()) {
            val boosts = BoostListPane(post.boosts)
            boosts.alignmentX = LEFT_ALIGNMENT
            boostPanes += boosts
            column.add(boosts)
        }
        return column
    }

    private fun flairIcon(post: TopicPost): JComponent? {
        val url = post.flairUrl?.trim().orEmpty()
        if (url.isEmpty() || '/' in url || '.' in url || ':' in url) {
            return null
        }
        if (FaGlyphs.get(url) == null) {
            return null
        }
        return FaMark(url, GuestUi.muted, 12)
    }

    fun jumpToPostNumber(number: Int) {
        val loaded = thread.posts.firstOrNull { it.postNumber == number }
        val index = when {
            loaded != null -> thread.streamIds.indexOf(loaded.id)
            number in 1..thread.streamIds.size -> number - 1
            else -> -1
        }
        if (index >= 0) {
            jumpToStream(index)
        }
    }

    private fun topicMap(topic: HomeTopic, locale: Locale): JComponent {
        val bar = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, JBUI.scale(28), 4))
        bar.isOpaque = false
        bar.alignmentX = LEFT_ALIGNMENT
        bar.maximumSize = Dimension(readingCap(), Integer.MAX_VALUE)
        bar.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1, 0, 1, 0),
            JBUI.Borders.empty(10, 0, 12, 0),
        )
        bar.add(mapStat(DiscourseNumber.compact(topic.views), IntelliDoStrings.message("topic.map.views", locale), DiscourseNumber.hotViews(topic.views)))
        bar.add(mapStat(DiscourseNumber.compact(topic.likeCount), IntelliDoStrings.message("topic.map.likes", locale), topic.likeCount >= 100))
        if (topic.linkCount > 0) {
            bar.add(mapStat(topic.linkCount.toString(), IntelliDoStrings.message("topic.map.links", locale), false))
        }
        if (topic.participantCount > 0) {
            bar.add(mapStat(DiscourseNumber.compact(topic.participantCount), IntelliDoStrings.message("topic.map.users", locale), false))
        }
        val minutes = topic.readingMinutes()
        if (minutes > 0) {
            bar.add(mapStat("${minutes}", IntelliDoStrings.message("topic.map.read", locale), false))
        }
        return bar
    }

    private fun mapStat(number: String, label: String, hot: Boolean): JComponent {
        val cell = JBPanel<JBPanel<*>>()
        cell.layout = BoxLayout(cell, BoxLayout.Y_AXIS)
        cell.isOpaque = false
        val n = JBLabel(number)
        n.font = n.font.deriveFont(Font.BOLD, 16f)
        if (hot) {
            n.foreground = HEAT
        }
        n.alignmentX = LEFT_ALIGNMENT
        val caption = JBLabel(label)
        caption.foreground = GuestUi.muted
        caption.font = GuestUi.metaFont(caption.font)
        caption.alignmentX = LEFT_ALIGNMENT
        cell.add(n)
        cell.add(caption)
        return cell
    }

    companion object {
        const val READING_WIDTH: Int = 1100
        const val INFO_COL: Int = 128
        const val POST_KEY: String = "intellido.post"
        const val SKELETONS: Int = 5
        val HEAT: JBColor = JBColor(0xFE7A15, 0xFF9A40)
        val HEART: JBColor = JBColor(0xE45735, 0xFF6B6B)
    }

    private fun maybeLoadMore() {
        val bar = scroll.verticalScrollBar
        if (bar.maximum <= 0) {
            return
        }
        val firstVisible = postViews.firstOrNull { it.isVisible }
        val lastVisible = postViews.lastOrNull { it.isVisible }
        if (!noMoreBefore && bar.value <= JBUI.scale(48)) {
            val firstPost = firstVisible?.getClientProperty(POST_KEY) as? TopicPost
            val firstIndex = firstPost?.let { thread.streamIds.indexOf(it.id) } ?: 0
            if (firstIndex > 0) {
                onNeedAround(firstIndex - 1)
                return
            }
        }
        if (noMoreAfter) {
            return
        }
        if (bar.value + bar.visibleAmount >= bar.maximum - JBUI.scale(96)) {
            val lastPost = lastVisible?.getClientProperty(POST_KEY) as? TopicPost
            val lastIndex = lastPost?.let { thread.streamIds.indexOf(it.id) } ?: -1
            if (lastIndex >= 0 && lastIndex < thread.streamIds.lastIndex) {
                if (visibleStreamStart() <= 0) {
                    onNeedMore()
                } else {
                    onNeedAround(lastIndex + 1)
                }
            }
        }
    }

    private fun applyWindow(focusStreamIndex: Int, scrollToFocus: Boolean = true) {
        val ids = thread.streamIds
        if (ids.isEmpty()) {
            return
        }
        val focus = focusStreamIndex.coerceIn(0, ids.lastIndex)
        val loaded = postViews.mapNotNull { view ->
            (view.getClientProperty(POST_KEY) as? TopicPost)?.id
        }.toSet()
        var start = -1
        var end = -1
        if (ids[focus] in loaded) {
            start = focus
            end = focus
            while (start > 0 && ids[start - 1] in loaded) {
                start--
            }
            while (end < ids.lastIndex && ids[end + 1] in loaded) {
                end++
            }
        }
        val visibleIds = if (start >= 0) ids.subList(start, end + 1).toSet() else emptySet()
        postViews.forEach { view ->
            val post = view.getClientProperty(POST_KEY) as? TopicPost
            view.isVisible = post != null && post.id in visibleIds
        }
        topicMapView?.isVisible = visibleIds.contains(ids.getOrNull(0))
        if (postViews.any { it.isVisible } && visibleStreamEnd() >= ids.lastIndex) {
            noMoreAfter = true
        }
        if (postViews.any { it.isVisible } && visibleStreamStart() <= 0) {
            noMoreBefore = true
        }
        refreshSkeletons()
        revalidate()
        repaint()
        val target = postViews.find { view ->
            view.isVisible && (view.getClientProperty(POST_KEY) as? TopicPost)?.id == ids[focus]
        }
        if (scrollToFocus && target != null) {
            SwingUtilities.invokeLater {
                scroll.verticalScrollBar.value = target.y.coerceAtLeast(0)
                syncTimeline()
            }
        } else {
            syncTimeline()
        }
    }

    private fun insertPostView(view: JComponent) {
        val post = view.getClientProperty(POST_KEY) as? TopicPost
        val streamIndex = post?.id?.let { thread.streamIds.indexOf(it) } ?: Int.MAX_VALUE
        val listAt = postViews.indexOfFirst { existing ->
            val id = (existing.getClientProperty(POST_KEY) as? TopicPost)?.id ?: return@indexOfFirst false
            val idx = thread.streamIds.indexOf(id)
            idx >= 0 && idx > streamIndex
        }.let { if (it < 0) postViews.size else it }
        postViews.add(listAt, view)
        val insertAt = (0 until stream.componentCount).firstOrNull { index ->
            val child = stream.getComponent(index)
            child is PostSkeleton || child is LoadPulse
        } ?: stream.componentCount
        stream.add(view, insertAt)
        if (post?.postNumber == 1 && topicMapView == null) {
            topicMapView = topicMap(thread.topic, locale)
            stream.add(topicMapView, insertAt + 1)
        }
    }

    private fun forgetView(view: JComponent) {
        fun walk(component: java.awt.Component) {
            when (component) {
                is moe.momokko.intellido.ui.guest.GuestAvatar -> avatarHosts.remove(component)
                is BoostListPane -> boostPanes.remove(component)
                is ImageSlot -> imageSlots.remove(component)
                is moe.momokko.intellido.ui.content.PostBodyPane -> bodies.removeAll { it.first === component }
            }
            if (component is java.awt.Container) {
                component.components.forEach(::walk)
            }
        }
        walk(view)
    }

    private fun refreshSkeletons() {
        stream.components.filter { it is PostSkeleton || it is LoadPulse }.forEach { stream.remove(it) }
        val moreBefore = !noMoreBefore && visibleStreamStart() > 0
        val moreAfter = !noMoreAfter && visibleStreamEnd() < thread.streamIds.lastIndex
        if (moreBefore) {
            val insertAt = skeletonInsertIndex()
            repeat(2) { stream.add(PostSkeleton(locale), insertAt) }
        }
        if (moreAfter || (!noMoreAfter && postViews.none { it.isVisible } && hasMorePosts())) {
            repeat(SKELETONS) { stream.add(PostSkeleton(locale)) }
            stream.add(loadPulse)
        }
    }

    private fun skeletonInsertIndex(): Int {
        val headerAndMap = stream.components.indexOfFirst { child ->
            child !is PostSkeleton && child !is LoadPulse &&
                ((child as? JComponent)?.getClientProperty(POST_KEY) as? TopicPost) == null &&
                child !== topicMapView
        }
        return (headerAndMap + 1).coerceAtLeast(1)
    }

    private fun visibleStreamStart(): Int =
        postViews.mapNotNull { view ->
            if (!view.isVisible) {
                return@mapNotNull null
            }
            val id = (view.getClientProperty(POST_KEY) as? TopicPost)?.id ?: return@mapNotNull null
            thread.streamIds.indexOf(id).takeIf { it >= 0 }
        }.minOrNull() ?: 0

    private fun visibleStreamEnd(): Int =
        postViews.mapNotNull { view ->
            if (!view.isVisible) {
                return@mapNotNull null
            }
            val id = (view.getClientProperty(POST_KEY) as? TopicPost)?.id ?: return@mapNotNull null
            thread.streamIds.indexOf(id).takeIf { it >= 0 }
        }.maxOrNull() ?: -1

    private fun updatePositionLabel(streamIndex: Int) {
        val id = thread.streamIds.getOrNull(streamIndex)
        val post = thread.posts.firstOrNull { it.id == id }
        val total = thread.topic.postsCount
            .coerceAtLeast(thread.streamIds.size)
            .coerceAtLeast(thread.posts.size)
            .coerceAtLeast(1)
        val number = (post?.postNumber ?: (streamIndex + 1)).coerceIn(1, total)
        positionLabel.text = IntelliDoStrings.message("topic.timeline.position", locale, number, total)
    }

    private fun syncTimeline() {
        val bar = timeline ?: return
        val visible = postViews.filter { it.isVisible }
        if (visible.isEmpty()) {
            return
        }
        val y = scroll.viewport.viewPosition.y
        val index = visible.indexOfLast { it.y <= y + JBUI.scale(40) }.coerceAtLeast(0)
        val post = visible[index].getClientProperty(POST_KEY) as? TopicPost
            ?: return
        val streamIndex = thread.streamIds.indexOf(post.id).let { found ->
            if (found >= 0) found else (post.postNumber - 1).coerceAtLeast(0)
        }
        bar.sync(post, streamIndex)
        updatePositionLabel(streamIndex)
    }

    private fun registerBundledTwemoji(document: CookedDocument) {
        CookedHtml.emojiUrls(document).forEach { src ->
            val bytes = TwemojiAssets.bytes("", src) ?: return@forEach
            mediaBytes[src] = bytes
            InlineMedia.put(src, bytes)
        }
    }

    private fun readingCap(): Int {
        val cap = ReadingAppearance.current.maxWidth
        return if (cap <= 0) Integer.MAX_VALUE else JBUI.scale(cap)
    }

    private fun mediaLookup(): Map<String, String> {
        if (mediaBytes.isEmpty()) {
            return emptyMap()
        }
        return mediaBytes.keys.associateWith { url -> InlineMedia.key(url) }
    }

    private class ClipBar : JBPanel<ClipBar>(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)) {
        init {
            isOpaque = false
        }

        override fun paintChildren(g: Graphics) {
            val clipped = g.create(0, 0, width.coerceAtLeast(0), height.coerceAtLeast(0))
            try {
                super.paintChildren(clipped)
            } finally {
                clipped.dispose()
            }
        }
    }

    private class WidthTrackingPanel : JPanel(), Scrollable {
        init {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT
        }

        override fun getScrollableTracksViewportWidth(): Boolean = true

        override fun getScrollableTracksViewportHeight(): Boolean = false

        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

        override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = 24

        override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
            (visibleRect.height * 0.9).toInt().coerceAtLeast(64)

        override fun getPreferredSize(): Dimension {
            val pref = super.getPreferredSize()
            val parent = parent
            return if (parent is JViewport && parent.extentSize.width > 0) {
                Dimension(parent.extentSize.width, pref.height)
            } else {
                pref
            }
        }

        override fun getMaximumSize(): Dimension = Dimension(Integer.MAX_VALUE, preferredSize.height)
    }

    private class ImageSlot(
        private val url: String,
        private val originalUrl: String,
        private val loadOriginal: (String, (ByteArray?) -> Unit) -> Unit,
    ) : JBPanel<ImageSlot>(BorderLayout()) {
        private var painted: Boolean = false

        init {
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT
            isVisible = false
            border = JBUI.Borders.empty(8, 0, 8, 0)
        }

        fun apply(bytesByUrl: Map<String, ByteArray>) {
            if (painted) {
                return
            }
            val bytes = bytesByUrl.entries.firstOrNull { MediaUrls.key(it.key) == MediaUrls.key(url) }?.value
                ?: return
            painted = true
            removeAll()
            val orig = if (MediaUrls.key(originalUrl) == MediaUrls.key(url)) "" else originalUrl
            add(PostImage(bytes, orig, loadOriginal), BorderLayout.CENTER)
            isVisible = true
            revalidate()
            repaint()
        }
    }
}
