package moe.momokko.intellido.ui.home

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.domain.content.MediaUrls
import moe.momokko.intellido.domain.live.GuestLiveEvent
import moe.momokko.intellido.domain.topic.HomeTopic
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.platform.live.GuestLiveSession
import moe.momokko.intellido.ui.guest.GuestUi
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.concurrent.ConcurrentHashMap
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Locale
import javax.swing.JButton
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder

class HomePanel(
    private val locale: Locale,
    private val onOpenTopic: (HomeTopic, pin: Boolean) -> Unit = { _, _ -> },
) : JBPanel<HomePanel>(BorderLayout()) {
    private val avatars = ConcurrentHashMap<String, ByteArray>()
    private val topicTable = HomeTopicsTable(locale, avatars)
    @Volatile
    private var loading: Boolean = false
    private var filter: Filter = Filter.Latest
    private val latestPill: JButton
    private val hotPill: JButton
    private val topPill: JButton
    private val listScroll: JBScrollPane
    private val loadMorePlaceholder: HomeLoadPlaceholder
    private val incomingBar: IncomingTopicsBar
    private val incomingWrap: JBPanel<*>
    private var liveSession: GuestLiveSession? = null
    private var pendingLoad: (() -> List<HomeTopic>)? = null
    private val liveListener: (List<GuestLiveEvent>) -> Unit = { events -> onLiveEvents(events) }

    private sealed class Filter {
        data object Latest : Filter()
        data object Hot : Filter()
        data object Top : Filter()
        data class Category(val id: Long) : Filter()
        data class Tag(val name: String) : Filter()
    }

    init {
        border = JBUI.Borders.empty(8, 16, 8, 8)
        topicTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                val index = topicTable.indexAt(event.point)
                if (index < 0) {
                    return
                }
                openAt(index, pin = event.clickCount >= 2)
            }
        })
        topicTable.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(event: KeyEvent) {
                if (event.keyCode == KeyEvent.VK_ENTER) {
                    val index = topicTable.selectedIndex()
                    if (index >= 0) {
                        openAt(index, pin = event.isShiftDown)
                    }
                }
            }
        })
        latestPill = navItem(IntelliDoStrings.message("nav.latest", locale)) { showLatest() }
        hotPill = navItem(IntelliDoStrings.message("nav.hot", locale)) { showHot() }
        topPill = navItem(IntelliDoStrings.message("nav.top", locale)) { showTop() }
        val nav = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 4, 0))
        nav.isOpaque = false
        nav.alignmentX = LEFT_ALIGNMENT
        nav.maximumSize = Dimension(Integer.MAX_VALUE, JBUI.scale(40))
        nav.add(latestPill)
        nav.add(hotPill)
        nav.add(topPill)
        incomingBar = IncomingTopicsBar(locale) { refresh() }
        incomingWrap = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.CENTER, 0, 0))
        incomingWrap.isOpaque = false
        incomingWrap.isVisible = false
        incomingWrap.alignmentX = LEFT_ALIGNMENT
        incomingWrap.maximumSize = Dimension(Integer.MAX_VALUE, JBUI.scale(36))
        incomingWrap.add(incomingBar)
        val north = JBPanel<JBPanel<*>>()
        north.layout = javax.swing.BoxLayout(north, javax.swing.BoxLayout.Y_AXIS)
        north.isOpaque = false
        north.border = JBUI.Borders.empty(0, 0, 4, 0)
        north.add(nav)
        north.add(incomingWrap)
        add(north, BorderLayout.NORTH)
        listScroll = HomeTopicsTable.wrap(topicTable)
        listScroll.verticalScrollBar.addAdjustmentListener {
            maybeLoadMore()
        }
        add(listScroll, BorderLayout.CENTER)
        loadMorePlaceholder = HomeLoadPlaceholder(locale)
        add(loadMorePlaceholder, BorderLayout.SOUTH)
        paintPills()
    }

    fun displayedTitles(): List<String> = topicTable.titles()

    override fun addNotify() {
        super.addNotify()
        reload()
    }

    fun disposeLive() {
        liveSession?.removeListener(liveListener)
        liveSession = null
    }

    fun refresh() {
        if (loading) {
            return
        }
        incomingBar.setCount(0)
        incomingWrap.isVisible = false
        val current = filter
        topicTable.replace(emptyList())
        topicTable.showLoading()
        loadOnPool {
            when (current) {
                Filter.Latest -> runtime().homeController.load()
                Filter.Hot -> runtime().homeController.loadHot()
                Filter.Top -> runtime().homeController.loadTop()
                is Filter.Category -> runtime().homeController.loadCategory(current.id)
                is Filter.Tag -> runtime().homeController.loadTag(current.name)
            }
        }
    }

    fun reload() {
        if (loading) {
            return
        }
        if (topicTable.hasTopics()) {
            return
        }
        topicTable.showLoading()
        loadOnPool { runtime().homeController.load() }
    }

    fun showLatest() {
        loadFilter(Filter.Latest) { runtime().homeController.load() }
    }

    fun showHot() {
        loadFilter(Filter.Hot) { runtime().homeController.loadHot() }
    }

    fun showTop() {
        loadFilter(Filter.Top) { runtime().homeController.loadTop() }
    }

    fun showCategory(categoryId: Long) {
        loadFilter(Filter.Category(categoryId)) { runtime().homeController.loadCategory(categoryId) }
    }

    fun showTag(name: String) {
        loadFilter(Filter.Tag(name)) { runtime().homeController.loadTag(name) }
    }

    private fun loadFilter(next: Filter, block: () -> List<HomeTopic>) {
        if (filter == next && topicTable.hasTopics() && !loading) {
            return
        }
        filter = next
        paintPills()
        topicTable.replace(emptyList())
        topicTable.showLoading()
        // A slow first load must not swallow the click: the pill moves immediately
        // and the request is queued behind the one in flight.
        if (loading) {
            pendingLoad = block
            return
        }
        loadOnPool(block)
    }

    private fun navItem(text: String, action: () -> Unit): JButton {
        val button = JButton(text)
        button.putClientProperty("JButton.buttonType", "borderless")
        button.isOpaque = false
        button.addActionListener { action() }
        return button
    }

    private fun paintPills() {
        stylePill(latestPill, filter == Filter.Latest)
        stylePill(hotPill, filter == Filter.Hot)
        stylePill(topPill, filter == Filter.Top)
    }

    private fun stylePill(button: JButton, selected: Boolean) {
        button.foreground = if (selected) GuestUi.signal else null
        button.border = CompoundBorder(
            MatteBorder(0, 0, JBUI.scale(2), 0, if (selected) GuestUi.signal else java.awt.Color(0, 0, 0, 0)),
            EmptyBorder(JBUI.scale(6), JBUI.scale(10), JBUI.scale(6), JBUI.scale(10)),
        )
    }

    private fun runtime() = com.intellij.openapi.components.service<moe.momokko.intellido.ui.startup.IntelliDoRuntime>()

    private fun loadOnPool(block: () -> List<HomeTopic>) {
        if (loading) {
            return
        }
        loading = true
        if (!topicTable.hasTopics()) {
            topicTable.showLoading()
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            runtime().awaitCommunity()
            bindLive()
            val result = runCatching(block)
            ApplicationManager.getApplication().invokeLater {
                loading = false
                val queued = pendingLoad
                if (queued != null) {
                    // The member switched filters mid-flight: this result is stale.
                    pendingLoad = null
                    loadOnPool(queued)
                    return@invokeLater
                }
                paintIncoming()
                result.onSuccess { topics ->
                    topicTable.replace(topics)
                    loadAvatars(topics)
                }.onFailure {
                    topicTable.showFailed()
                }
            }
        }
    }

    private fun loadAvatars(topics: List<HomeTopic>) {
        val loader = runtime().mediaLoader ?: return
        val wanted = topics.flatMap { topic ->
            topic.posters.mapNotNull { poster -> poster.username to poster.avatarUrl(48) }
        }.filter { it.second != null }
        if (wanted.isEmpty()) {
            return
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            val bytes = runCatching { loader.load(wanted.mapNotNull { it.second }, 48) }.getOrDefault(emptyMap())
            if (bytes.isEmpty()) {
                return@executeOnPooledThread
            }
            ApplicationManager.getApplication().invokeLater {
                wanted.forEach { (username, url) ->
                    val match = bytes.entries.firstOrNull { MediaUrls.key(it.key) == MediaUrls.key(url.orEmpty()) }
                    if (match != null) {
                        avatars[username] = match.value
                    }
                }
                topicTable.repaint()
            }
        }
    }

    private fun bindLive() {
        val session = runtime().liveSession ?: return
        if (liveSession === session) {
            return
        }
        liveSession?.removeListener(liveListener)
        liveSession = session
        session.addListener(liveListener)
    }

    private fun onLiveEvents(events: List<GuestLiveEvent>) {
        val latest = events.filterIsInstance<GuestLiveEvent.LatestTopic>()
        if (latest.isEmpty()) {
            return
        }
        ApplicationManager.getApplication().invokeLater {
            val current = filter
            latest.forEach { event ->
                when (current) {
                    Filter.Latest -> runtime().homeController.noteIncoming(event.topicId)
                    is Filter.Category -> {
                        if (event.categoryId == null || event.categoryId == current.id) {
                            runtime().homeController.noteIncoming(event.topicId)
                        }
                    }
                    else -> return@forEach
                }
            }
            paintIncoming()
            revalidate()
            repaint()
        }
    }

    private fun paintIncoming() {
        val tracking = filter is Filter.Latest || filter is Filter.Category
        incomingBar.setCount(if (tracking) runtime().homeController.incomingCount() else 0)
        incomingWrap.isVisible = incomingBar.isVisible
        incomingWrap.revalidate()
    }

    private fun openAt(index: Int, pin: Boolean) {
        if (index in 0 until topicTable.topicCount()) {
            onOpenTopic(topicTable.at(index), pin)
        }
    }

    private fun maybeLoadMore() {
        if (loading || !topicTable.hasTopics()) {
            return
        }
        if (!runtime().homeController.hasMore()) {
            return
        }
        val bar = listScroll.verticalScrollBar
        if (bar.maximum <= 0) {
            return
        }
        if (bar.value + bar.visibleAmount >= bar.maximum - JBUI.scale(48)) {
            loadMoreOnPool()
        }
    }

    private fun loadMoreOnPool() {
        if (loading) {
            return
        }
        loading = true
        loadMorePlaceholder.isVisible = true
        revalidate()
        ApplicationManager.getApplication().executeOnPooledThread {
            runtime().awaitCommunity()
            val result = runCatching { runtime().homeController.loadMore() }
            ApplicationManager.getApplication().invokeLater {
                loading = false
                loadMorePlaceholder.isVisible = false
                result.onSuccess { extra ->
                    topicTable.append(extra)
                    if (extra.isNotEmpty()) {
                        loadAvatars(extra)
                    }
                }
                revalidate()
                repaint()
            }
        }
    }
}
