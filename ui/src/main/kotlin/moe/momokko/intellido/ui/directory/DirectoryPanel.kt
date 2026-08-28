package moe.momokko.intellido.ui.directory

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.domain.catalog.CommunityAbout
import moe.momokko.intellido.domain.topic.HomeTopic
import moe.momokko.intellido.platform.catalog.DirectoryKind
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.transport.LinuxDoCommunityClient
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants

class DirectoryPanel(
    private val kind: DirectoryKind,
    private val client: LinuxDoCommunityClient,
    private val locale: Locale,
    private val onOpenTopic: (HomeTopic, pin: Boolean) -> Unit,
    private val onOpenUser: (String) -> Unit = {},
    private val onOpenUrl: (String) -> Unit = {},
    private val dispatch: (pooled: Boolean, work: () -> Unit) -> Unit = defaultDispatch,
) : JBPanel<DirectoryPanel>(BorderLayout()) {
    private val listModel = DefaultListModel<DirectoryRow>()
    private val avatars = ConcurrentHashMap<String, ByteArray>()
    private val list = JBList(listModel)
    private val center = JBPanel<JBPanel<*>>(BorderLayout())
    private var topicRows: List<HomeTopic> = emptyList()
    private var showingTopics: Boolean = false
    private var rootRows: List<DirectoryRow> = emptyList()
    private val loadGen = AtomicInteger(0)

    init {
        border = JBUI.Borders.empty(16)
        val header = JBPanel<JBPanel<*>>(BorderLayout())
        val title = JBLabel(IntelliDoStrings.message(kind.titleKey, locale))
        title.getAccessibleContext().accessibleName = title.text
        header.add(title, BorderLayout.WEST)
        if (kind == DirectoryKind.CATEGORIES || kind == DirectoryKind.TAGS || kind == DirectoryKind.GROUPS) {
            val back = JButton(IntelliDoStrings.message("directory.back", locale))
            back.addActionListener { showRoot() }
            header.add(back, BorderLayout.EAST)
        }
        add(header, BorderLayout.NORTH)
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.cellRenderer = DirectoryRenderer(locale, avatars)
        list.getAccessibleContext().accessibleName = IntelliDoStrings.message(kind.titleKey, locale)
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                val index = list.locationToIndex(event.point)
                if (index < 0) {
                    return
                }
                activate(index, pin = event.clickCount >= 2)
            }
        })
        list.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(event: KeyEvent) {
                if (event.keyCode == KeyEvent.VK_ENTER && list.selectedIndex >= 0) {
                    activate(list.selectedIndex, pin = event.isShiftDown)
                }
            }
        })
        center.add(JBScrollPane(list), BorderLayout.CENTER)
        add(center, BorderLayout.CENTER)
        showRoot()
    }

    fun displayedRows(): List<String> =
        (0 until listModel.size()).map { listModel.getElementAt(it).label() }

    fun activateAt(index: Int, pin: Boolean = false) {
        activate(index, pin)
    }

    private fun showRoot() {
        showingTopics = false
        topicRows = emptyList()
        if (kind == DirectoryKind.ABOUT) {
            paintMessage(IntelliDoStrings.message("directory.loading", locale))
            val gen = loadGen.incrementAndGet()
            dispatch(true) {
                val result = runCatching { client.loadAbout() }
                dispatch(false) {
                    if (gen != loadGen.get()) {
                        return@dispatch
                    }
                    result.onSuccess { about -> showAbout(about) }
                        .onFailure { error ->
                            paintMessage(error.message ?: IntelliDoStrings.message("home.loadFailed", locale))
                        }
                }
            }
            return
        }
        paintMessage(IntelliDoStrings.message("directory.loading", locale))
        val gen = loadGen.incrementAndGet()
        dispatch(true) {
            val result = runCatching { loadRootRows() }
            dispatch(false) {
                if (gen != loadGen.get()) {
                    return@dispatch
                }
                result.onSuccess { rows ->
                    rootRows = rows
                    replaceRows(rows.ifEmpty { listOf(DirectoryRow.Message(IntelliDoStrings.message("directory.empty", locale))) })
                }.onFailure { error ->
                    paintMessage(error.message ?: IntelliDoStrings.message("home.loadFailed", locale))
                }
            }
        }
    }

    private fun showAbout(about: CommunityAbout) {
        center.removeAll()
        center.add(AboutPanel(about, locale, onOpenUser, onOpenUrl), BorderLayout.CENTER)
        center.revalidate()
        center.repaint()
    }

    private fun loadRootRows(): List<DirectoryRow> = when (kind) {
        DirectoryKind.CATEGORIES -> client.loadCategories().map { DirectoryRow.Category(it) }
        DirectoryKind.TAGS -> client.loadTags().map { DirectoryRow.Tag(it) }
        DirectoryKind.GROUPS -> client.loadGroups().map { DirectoryRow.Group(it) }
        DirectoryKind.BADGES -> client.loadBadges().map { DirectoryRow.Badge(it) }
        DirectoryKind.MEMBERS -> client.loadMembers().map { DirectoryRow.Member(it) }
        DirectoryKind.ABOUT -> emptyList()
    }

    private fun activate(index: Int, pin: Boolean) {
        if (showingTopics) {
            if (index in topicRows.indices) {
                onOpenTopic(topicRows[index], pin)
            }
            return
        }
        val row = listModel.getElementAt(index)
        when (row) {
            is DirectoryRow.Category -> loadTopics { client.loadCategoryTopics(row.category.id) }
            is DirectoryRow.Tag -> loadTopics { client.loadTagTopics(row.tag.name) }
            is DirectoryRow.Group -> onOpenUrl("https://linux.do/g/${row.group.name}")
            is DirectoryRow.Member -> onOpenUser(row.member.username)
            is DirectoryRow.Badge, is DirectoryRow.Message, is DirectoryRow.Topic -> Unit
        }
    }

    private fun loadTopics(block: () -> List<HomeTopic>) {
        paintMessage(IntelliDoStrings.message("directory.loading", locale))
        val gen = loadGen.incrementAndGet()
        dispatch(true) {
            val result = runCatching(block)
            dispatch(false) {
                if (gen != loadGen.get()) {
                    return@dispatch
                }
                result.onSuccess { topics ->
                    showingTopics = true
                    topicRows = topics
                    replaceRows(
                        topics.map { DirectoryRow.Topic(it) }
                            .ifEmpty { listOf(DirectoryRow.Message(IntelliDoStrings.message("directory.empty", locale))) },
                    )
                }.onFailure { error ->
                    paintMessage(error.message ?: IntelliDoStrings.message("home.loadFailed", locale))
                }
            }
        }
    }

    private fun paintMessage(text: String) {
        center.removeAll()
        center.add(JBScrollPane(list), BorderLayout.CENTER)
        replaceRows(listOf(DirectoryRow.Message(text)))
        center.revalidate()
        center.repaint()
    }

    private fun replaceRows(rows: List<DirectoryRow>) {
        listModel.clear()
        rows.forEach { listModel.addElement(it) }
    }

    companion object {
        val defaultDispatch: (Boolean, () -> Unit) -> Unit = { pooled, work ->
            val app = ApplicationManager.getApplication()
            if (pooled) {
                app.executeOnPooledThread(work)
            } else {
                app.invokeLater(work)
            }
        }
    }
}
