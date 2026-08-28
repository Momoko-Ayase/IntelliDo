package moe.momokko.intellido.ui.content

import com.intellij.ide.BrowserUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.SVGLoader
import moe.momokko.intellido.domain.content.CookedBlock
import moe.momokko.intellido.domain.content.CookedAlignment
import moe.momokko.intellido.domain.content.CookedDocument
import moe.momokko.intellido.domain.content.CookedHtmlParser
import moe.momokko.intellido.domain.content.CookedSpan
import moe.momokko.intellido.domain.content.DefinitionItem
import moe.momokko.intellido.domain.content.TableRow
import moe.momokko.intellido.platform.reading.ReadingAppearance
import moe.momokko.intellido.ui.guest.GuestUi
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Rectangle2D
import java.io.ByteArrayInputStream
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JViewport
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder

/**
 * Native cooked-HTML body. Paragraphs wrap with FontMetrics so CJK breaks
 * between ideographs. The document is never rewritten, so copy stays one
 * paragraph.
 */
class PostBodyPane(
    bodyHtml: String,
    private val onNavigate: (String) -> Boolean = { false },
) : FlowColumn() {
    private var stopListening: (() -> Unit)? = null
    private var lastHtml: String = bodyHtml
    private var lastDocument: CookedDocument = CookedDocument(emptyList())
    private var lastMedia: Map<String, String> = emptyMap()

    constructor(
        document: CookedDocument,
        onNavigate: (String) -> Boolean = { false },
        media: Map<String, String> = emptyMap(),
    ) : this("", onNavigate) {
        applyDocument(document, media)
    }

    init {
        alignmentX = LEFT_ALIGNMENT
        isOpaque = false
        isFocusable = true
        HashtagChips.register()
        val copyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_C, GuestUi.menuShortcutMask())
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(copyStroke, "copy-body")
        actionMap.put(
            "copy-body",
            object : AbstractAction() {
                override fun actionPerformed(event: java.awt.event.ActionEvent) {
                    val selected = selectedText()
                    val payload = selected.ifBlank { visibleText() }
                    if (payload.isNotBlank()) {
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(payload), null)
                    }
                }
            },
        )
        if (bodyHtml.isNotBlank()) {
            applyHtml(bodyHtml)
        }
    }

    fun updateHtml(bodyHtml: String) {
        applyHtml(bodyHtml)
        revalidate()
        repaint()
    }

    fun update(document: CookedDocument, media: Map<String, String> = lastMedia) {
        applyDocument(document, media)
        revalidate()
        repaint()
    }

    fun wrapped(): JComponent = this

    fun visibleText(): String = buildString {
        var first = true
        walkRuns { run ->
            if (!first) {
                append('\n')
            }
            first = false
            append(run.copyText())
            false
        }
    }

    fun layoutText(): String = buildString {
        var first = true
        walkRuns { run ->
            if (!first) {
                append('\n')
            }
            first = false
            append(run.layoutText())
            false
        }
    }

    fun charBox(index: Int): Rectangle2D? {
        var offset = 0
        var found: Rectangle2D? = null
        walkRuns { run ->
            val len = run.layoutText().length
            if (index < offset + len) {
                val local = run.charBox(index - offset) ?: return@walkRuns false
                found = Rectangle2D.Double(
                    local.x + run.x,
                    local.y + run.y,
                    local.width,
                    local.height,
                )
                return@walkRuns true
            }
            offset += len + 1
            false
        }
        return found
    }

    fun lineAdvances(): List<Float> {
        val out = mutableListOf<Float>()
        walkRuns { run ->
            out += run.lineAdvances()
            false
        }
        return out
    }

    fun lineCount(): Int {
        var total = 0
        walkRuns { run ->
            total += run.lineCount()
            false
        }
        return total
    }

    fun inlineImageCount(): Int {
        var total = 0
        walkRuns { run ->
            total += run.inlineImageCount()
            false
        }
        return total
    }

    fun dumpLayout(): String = buildString {
        walkRuns { run ->
            append("WrapRunPane lines=").append(run.lineCount())
            append(" advances=").append(run.lineAdvances().map { it.toInt() })
            append(" images=").append(run.inlineImageCount())
            append(" [").append(run.copyText().replace("\n", "\\n").take(48)).append("]\n")
            false
        }
    }

    fun clickText(needle: String): Boolean {
        doLayout()
        var clicked = false
        walkRuns { run ->
            if (run.clickText(needle)) {
                clicked = true
                true
            } else {
                false
            }
        }
        return clicked
    }

    fun selectedText(): String {
        val parts = mutableListOf<String>()
        walkRuns { run ->
            val piece = run.selectedCopy()
            if (piece.isNotEmpty()) {
                parts += piece
            }
            false
        }
        return parts.joinToString("\n")
    }

    override fun addNotify() {
        super.addNotify()
        if (stopListening == null) {
            stopListening = ReadingAppearance.listen { applyReadingStyle() }
        }
        applyReadingStyle()
    }

    override fun removeNotify() {
        stopListening?.invoke()
        stopListening = null
        super.removeNotify()
    }

    private fun applyReadingStyle() {
        HashtagChips.register()
        if (lastDocument.blocks.isNotEmpty()) {
            applyDocument(lastDocument, lastMedia)
        } else if (lastHtml.isNotBlank()) {
            applyHtml(lastHtml)
        }
        revalidate()
        repaint()
    }

    private fun applyHtml(bodyHtml: String) {
        lastHtml = bodyHtml
        applyDocument(CookedHtmlParser().parse(unwrap(bodyHtml)), lastMedia)
    }

    private fun applyDocument(document: CookedDocument, media: Map<String, String>) {
        lastDocument = document
        lastMedia = media
        removeAll()
        val style = textStyle()
        document.blocks.forEach { block ->
            addBlock(this, block, style, media)
        }
    }

    private fun addBlock(column: FlowColumn, block: CookedBlock, style: TextStyle, media: Map<String, String>) {
        when (block) {
            is CookedBlock.Paragraph -> {
                val atoms = flatten(block.spans, style, media)
                if (atoms.isNotEmpty()) {
                    column.add(WrapRunPane(atoms, ::handleNavigate, alignment = block.alignment))
                }
            }
            is CookedBlock.Heading -> {
                val factor = when (block.level.coerceIn(1, 6)) {
                    1 -> 1.55f
                    2 -> 1.38f
                    3 -> 1.23f
                    4 -> 1.12f
                    5 -> 1.02f
                    else -> 0.95f
                }
                val heading = style.copy(
                    font = style.font.deriveFont(Font.BOLD, style.font.size2D * factor),
                )
                val atoms = flatten(block.spans, heading, media)
                if (atoms.isNotEmpty()) {
                    column.add(
                        WrapRunPane(atoms, ::handleNavigate, bottomGap = JBUI.scale(10), alignment = block.alignment).apply {
                            name = block.anchor?.let { "anchor:$it" }
                        },
                    )
                }
            }
            is CookedBlock.Quote -> column.add(QuotePane(block, style, media, ::handleNavigate))
            is CookedBlock.ListBlock -> column.add(ListPane(block, style, media, ::handleNavigate))
            is CookedBlock.CodeBlock -> {
                val pane = PostCodePane(block.code, block.language)
                pane.alignmentX = LEFT_ALIGNMENT
                column.add(pane)
            }
            is CookedBlock.Image -> {
                column.add(
                    NativeGraphicPane(
                        image = resolveImage(block.src, media),
                        declaredWidth = block.width,
                        declaredHeight = block.height,
                        description = block.caption ?: block.alt,
                        fallbackLabel = block.alt.ifBlank { "图片" },
                        href = block.original,
                        style = style,
                        onNavigate = ::handleNavigate,
                    ),
                )
            }
            is CookedBlock.ImageGrid -> column.add(ImageGridPane(block, style, media, ::handleNavigate))
            is CookedBlock.Table -> column.add(TablePane(block.rows, style, media, ::handleNavigate))
            is CookedBlock.Onebox -> column.add(OneboxPane(block, style, ::handleNavigate))
            is CookedBlock.Details -> {
                val body = PostBodyPane(CookedDocument(block.children), ::handleNavigate, media)
                val label = block.summary.ifBlank { "详细内容" }
                column.add(PostFoldPane(label, label, body, block.initiallyOpen))
            }
            is CookedBlock.Spoiler -> {
                val body = PostBodyPane(CookedDocument(block.children), ::handleNavigate, media)
                column.add(PostFoldPane("显示隐藏内容", "隐藏内容", body))
            }
            is CookedBlock.Poll -> {
                column.add(PostPollPane(block.title ?: "投票", block.options))
            }
            is CookedBlock.Callout -> column.add(CalloutPane(block, style, media, ::handleNavigate))
            is CookedBlock.Media -> column.add(MediaCardPane(block, style, ::handleNavigate))
            is CookedBlock.Math -> {
                val mathStyle = style.copy(font = Font(Font.MONOSPACED, Font.PLAIN, style.font.size + 1), code = true)
                column.add(
                    WrapRunPane(
                        listOf(mathStyle.atom(block.latex)),
                        ::handleNavigate,
                        bottomGap = JBUI.scale(12),
                        alignment = CookedAlignment.Center,
                    ),
                )
            }
            is CookedBlock.Footnotes -> {
                column.add(RulePane())
                val footnoteStyle = style.copy(font = style.font.deriveFont(style.font.size2D - 1f))
                block.entries.forEach { entry ->
                    column.add(
                        ListItemRow(
                            "${entry.number}.",
                            listOf(CookedBlock.Paragraph(entry.spans)),
                            footnoteStyle,
                            media,
                            ::handleNavigate,
                        ).apply { name = "anchor:${entry.id}" },
                    )
                }
            }
            is CookedBlock.Policy -> column.add(
                CalloutPane(
                    CookedBlock.Callout(CookedBlock.Callout.Kind.Important, "社区规则", block.children),
                    style,
                    media,
                    ::handleNavigate,
                ),
            )
            is CookedBlock.ChatTranscript -> {
                val by = listOfNotNull(block.channel?.let { "#$it" }, block.username.takeIf { it.isNotBlank() })
                    .joinToString(" · ")
                column.add(QuotePane(CookedBlock.Quote(block.children, by), style, media, ::handleNavigate))
            }
            is CookedBlock.DefinitionList -> column.add(DefinitionListPane(block.items, style, media, ::handleNavigate))
            is CookedBlock.Svg -> {
                val svg = runCatching {
                    ByteArrayInputStream(block.source.toByteArray(Charsets.UTF_8)).use { SVGLoader.load(it, 1f) }
                }.getOrNull()
                if (svg != null) {
                    column.add(
                        NativeGraphicPane(
                            image = svg,
                            declaredWidth = block.width,
                            declaredHeight = block.height,
                            description = block.title,
                            fallbackLabel = "SVG 图像",
                            href = null,
                            style = style,
                            onNavigate = ::handleNavigate,
                        ),
                    )
                } else {
                    column.add(
                        RichContentCard(
                            title = block.title,
                            detail = listOfNotNull(block.width, block.height).joinToString(" × ").ifBlank { "SVG" },
                            href = null,
                            style = style,
                            onNavigate = ::handleNavigate,
                        ),
                    )
                }
            }
            CookedBlock.ThematicBreak -> column.add(RulePane())
            is CookedBlock.Unknown -> if (block.children.isNotEmpty()) {
                column.add(PostBodyPane(CookedDocument(block.children), ::handleNavigate, media))
            }
        }
    }

    private fun handleNavigate(raw: String): Boolean {
        if (raw.startsWith('#')) {
            val id = raw.drop(1)
            val target = findAnchor(this, id)
            if (target != null) {
                val bounds = SwingUtilities.convertRectangle(target.parent, target.bounds, this)
                scrollRectToVisible(Rectangle(bounds.x, (bounds.y - JBUI.scale(12)).coerceAtLeast(0), bounds.width, bounds.height))
                return true
            }
        }
        return onNavigate(raw)
    }

    private fun findAnchor(component: Component, id: String): JComponent? {
        if (component is JComponent && component.name == "anchor:$id") return component
        if (component is Container) {
            component.components.forEach { child -> findAnchor(child, id)?.let { return it } }
        }
        return null
    }

    private fun walkRuns(visitor: (WrapRunPane) -> Boolean) {
        fun walk(component: Component): Boolean {
            if (component !== this && !component.isVisible) {
                return false
            }
            if (component is WrapRunPane) {
                return visitor(component)
            }
            if (component is Container) {
                component.components.forEach { child ->
                    if (walk(child)) {
                        return true
                    }
                }
            }
            return false
        }
        walk(this)
    }

    private fun textStyle(): TextStyle {
        val font = UIUtil.getLabelFont().deriveFont(ReadingAppearance.current.fontSize.toFloat())
        return TextStyle(font = font, color = UIUtil.getLabelForeground())
    }

    companion object {
        const val CONTENT_WIDTH: Int = 900

        fun registerHashtagIcons() {
            HashtagChips.register()
        }

        fun capWidth(available: Int): Int {
            val cap = ReadingAppearance.current.maxWidth
            if (cap <= 0) {
                return available
            }
            return available.coerceAtMost(JBUI.scale(cap))
        }

        internal fun unwrap(html: String): String {
            var value = html.trim()
            var stripped = value.removePrefix("<html>").removeSuffix("</html>").trim()
            stripped = stripped.removePrefix("<body>").removeSuffix("</body>").trim()
            return stripped
        }
    }
}

internal fun wrapWidth(component: JComponent, fallback: Int = 0): Int {
    val viewport = SwingUtilities.getAncestorOfClass(JViewport::class.java, component) as? JViewport
    val extent = viewport?.extentSize?.width ?: 0
    var stolen = 0
    var child: Component = component
    var host: Container? = component.parent
    while (host != null && host !== viewport) {
        stolen += (host as? JComponent)?.insets?.let { it.left + it.right } ?: 0
        val layout = host.layout
        if (layout is BorderLayout) {
            val west = layout.getLayoutComponent(BorderLayout.WEST)
            val east = layout.getLayoutComponent(BorderLayout.EAST)
            if (west != null && west !== child) {
                stolen += west.width.takeIf { it > 0 } ?: west.preferredSize.width
            }
            if (east != null && east !== child) {
                stolen += east.width.takeIf { it > 0 } ?: east.preferredSize.width
            }
        }
        child = host
        host = host.parent
    }
    val fromViewport = if (extent > 40) (extent - stolen).coerceAtLeast(40) else 0
    val self = if (component.width > 1) component.width - component.insets.left - component.insets.right else 0
    val parentW = (component.parent as? JComponent)?.let { parent ->
        if (parent.width > 1) (parent.width - parent.insets.left - parent.insets.right).coerceAtLeast(0) else 0
    } ?: 0
    val chosen = listOf(fromViewport, parentW, self, fallback).filter { it > 1 }.minOrNull()
        ?: JBUI.scale(PostBodyPane.CONTENT_WIDTH)
    return PostBodyPane.capWidth(chosen.coerceAtLeast(1))
}

/**
 * Width available on the physical window, in the same units as TextLayout.advance.
 * JComponent.width can be larger than what CopyFromScreen shows when JBR sysScale
 * is 1.5, which is why CJK either clipped or left a huge gutter.
 */
internal fun onScreenTextWidth(component: JComponent, fallback: Int = 0): Int {
    // A BoxLayout can keep a child's old width while an enclosing viewport is
    // being resized.  In that state `component.width` is wider than the area
    // Swing will actually paint and paragraphs otherwise disappear below the
    // right-hand clip.  `visibleRect` is expressed in the same user-space
    // coordinates as TextLayout and remains reliable on JRE-managed HiDPI.
    val fromClip = component.visibleRect.width.takeIf { it > 40 } ?: 0
    val sx = com.intellij.ui.scale.JBUIScale.sysScale(component).coerceAtLeast(1f)
    val fromWindow = runCatching {
        if (!component.isShowing) {
            return@runCatching 0
        }
        val window = SwingUtilities.getWindowAncestor(component) ?: return@runCatching 0
        val left = component.locationOnScreen.x - window.locationOnScreen.x
        val pad = JBUI.scale(16)
        ((window.width - left - pad) / sx).toInt()
    }.getOrDefault(0)
    val raw = wrapWidth(component, fallback)
    val chosen = listOf(fromClip, fromWindow, raw).filter { it > 40 }.minOrNull()
        ?: raw.coerceAtLeast(40)
    return chosen.coerceAtLeast(40)
}

interface FlowBlock {
    fun measureHeight(widthPx: Int): Int
}

abstract class FlowColumn : JBPanel<JBPanel<*>>(null), FlowBlock {
    init {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
    }

    override fun setBounds(x: Int, y: Int, w: Int, h: Int) {
        super.setBounds(x, y, w, h)
        doLayout()
    }

    override fun doLayout() {
        val inner = (width - insets.left - insets.right).coerceAtLeast(1)
        var y = insets.top
        components.forEach { child ->
            val h = heightOf(child, inner)
            child.setBounds(insets.left, y, inner, h)
            y += h
        }
    }

    override fun getPreferredSize(): Dimension {
        val w = onScreenTextWidth(this, availableWidth())
        return Dimension(w, measureHeight(w).coerceAtLeast(1))
    }

    override fun getMaximumSize(): Dimension =
        Dimension(onScreenTextWidth(this, if (width > 1) width else 0), Integer.MAX_VALUE)

    override fun getMinimumSize(): Dimension = Dimension(0, preferredSize.height)

    override fun measureHeight(widthPx: Int): Int {
        val inner = (widthPx - insets.left - insets.right).coerceAtLeast(1)
        var height = insets.top + insets.bottom
        components.forEach { child ->
            height += heightOf(child, inner)
        }
        return height.coerceAtLeast(1)
    }

    protected fun availableWidth(): Int = wrapWidth(this, if (width > 1) width else 0)

    private fun heightOf(child: Component, inner: Int): Int {
        if (child is FlowBlock) {
            return child.measureHeight(inner).coerceAtLeast(1)
        }
        child.size = Dimension(inner, child.preferredSize.height.coerceAtLeast(1))
        return child.preferredSize.height.coerceAtLeast(1)
    }
}

private class QuotePane(
    block: CookedBlock.Quote,
    style: TextStyle,
    media: Map<String, String>,
    onNavigate: (String) -> Boolean,
) : FlowColumn() {
    init {
        isOpaque = true
        background = QUOTE_BG
        border = CompoundBorder(
            MatteBorder(0, JBUI.scale(4), 0, 0, GuestUi.signal),
            EmptyBorder(JBUI.scale(8), JBUI.scale(12), JBUI.scale(8), JBUI.scale(12)),
        )
        alignmentX = LEFT_ALIGNMENT
        block.attribution?.takeIf { it.isNotBlank() }?.let { who ->
            add(
                WrapRunPane(
                    listOf(InlineAtom(text = who, font = GuestUi.metaFont(style.font), color = GuestUi.muted)),
                    onNavigate,
                    bottomGap = JBUI.scale(4),
                ),
            )
        }
        val host = PostBodyPane(CookedDocument(block.children), onNavigate, media)
        host.alignmentX = LEFT_ALIGNMENT
        add(host)
    }
}

private class ListPane(
    block: CookedBlock.ListBlock,
    style: TextStyle,
    media: Map<String, String>,
    onNavigate: (String) -> Boolean,
) : FlowColumn() {
    init {
        alignmentX = LEFT_ALIGNMENT
        block.items.forEachIndexed { index, item ->
            val mark = if (block.ordered) "${block.start + index}." else "•"
            add(ListItemRow(mark, item, style, media, onNavigate))
        }
    }
}

private class ListItemRow(
    mark: String,
    item: List<CookedBlock>,
    style: TextStyle,
    media: Map<String, String>,
    onNavigate: (String) -> Boolean,
) : JComponent(), FlowBlock {
    private val bullet = JBLabel(mark)
    private val body = PostBodyPane(CookedDocument(item), onNavigate, media)

    init {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        bullet.foreground = GuestUi.muted
        bullet.font = style.font
        add(bullet)
        add(body)
    }

    override fun setBounds(x: Int, y: Int, w: Int, h: Int) {
        super.setBounds(x, y, w, h)
        doLayout()
    }

    override fun doLayout() {
        val mw = markWidth()
        val mh = bullet.preferredSize.height
        bullet.setBounds(0, JBUI.scale(2), mw, mh)
        val bw = (width - mw).coerceAtLeast(1)
        val bh = body.measureHeight(bw)
        body.setBounds(mw, 0, bw, bh)
    }

    override fun measureHeight(widthPx: Int): Int {
        val mw = markWidth()
        return maxOf(bullet.preferredSize.height, body.measureHeight((widthPx - mw).coerceAtLeast(1)))
    }

    override fun getPreferredSize(): Dimension {
        val w = if (width > 1) width else JBUI.scale(PostBodyPane.CONTENT_WIDTH)
        return Dimension(w, measureHeight(w))
    }

    override fun getMaximumSize(): Dimension = Dimension(Integer.MAX_VALUE, preferredSize.height)

    private fun markWidth(): Int = bullet.preferredSize.width.coerceAtLeast(JBUI.scale(16)) + JBUI.scale(8)
}

private class TablePane(
    rows: List<TableRow>,
    style: TextStyle,
    media: Map<String, String>,
    onNavigate: (String) -> Boolean,
) : JComponent(), FlowBlock {
    private val cells: List<List<WrapRunPane>> = rows.mapIndexed { rowIndex, row ->
        row.cells.map { cell ->
            val header = cell.header || row.header
            val font = if (header) style.font.deriveFont(Font.BOLD) else style.font
            WrapRunPane(flatten(cell.spans, style.copy(font = font), media), onNavigate, bottomGap = 0).apply {
                isOpaque = true
                background = when {
                    header -> TABLE_HEADER_BG
                    rowIndex % 2 == 1 -> TABLE_STRIPE_BG
                    else -> UIUtil.getPanelBackground()
                }
                border = CompoundBorder(
                    MatteBorder(0, 0, JBUI.scale(1), JBUI.scale(1), JBColor.border()),
                    EmptyBorder(JBUI.scale(6), JBUI.scale(8), JBUI.scale(6), JBUI.scale(8)),
                )
            }
        }
    }

    init {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        border = JBUI.Borders.empty(4, 0, 8, 0)
        cells.flatten().forEach { add(it) }
    }

    override fun setBounds(x: Int, y: Int, w: Int, h: Int) {
        super.setBounds(x, y, w, h)
        doLayout()
    }

    override fun doLayout() {
        layoutCells(width.coerceAtLeast(1), apply = true)
    }

    override fun measureHeight(widthPx: Int): Int = layoutCells(widthPx, apply = false)

    override fun getPreferredSize(): Dimension {
        val w = if (width > 1) width else JBUI.scale(PostBodyPane.CONTENT_WIDTH)
        return Dimension(w, measureHeight(w))
    }

    override fun getMaximumSize(): Dimension = Dimension(Integer.MAX_VALUE, preferredSize.height)

    private fun layoutCells(widthPx: Int, apply: Boolean): Int {
        val cols = cells.maxOfOrNull { it.size } ?: 1
        val inner = (widthPx - insets.left - insets.right).coerceAtLeast(cols)
        val cw = (inner / cols).coerceAtLeast(1)
        var y = insets.top
        cells.forEach { row ->
            val heights = row.map { it.measureHeight(cw) }
            val rh = heights.maxOrNull() ?: 1
            if (apply) {
                row.forEachIndexed { index, cell ->
                    val x = insets.left + index * cw
                    val cellWidth = if (index == cols - 1) inner - index * cw else cw
                    cell.setBounds(x, y, cellWidth.coerceAtLeast(1), rh)
                }
            }
            y += rh
        }
        return (y + insets.bottom).coerceAtLeast(1)
    }
}

private class NativeGraphicPane(
    private val image: java.awt.Image?,
    private val declaredWidth: Int?,
    private val declaredHeight: Int?,
    description: String,
    fallbackLabel: String,
    private val href: String?,
    style: TextStyle,
    private val onNavigate: (String) -> Boolean,
) : JComponent(), FlowBlock {
    private val label = fallbackLabel.takeIf { it.isNotBlank() } ?: "媒体"
    private val caption = description.takeIf { it.isNotBlank() }?.let { text ->
        WrapRunPane(
            listOf(InlineAtom(text = text, font = style.font.deriveFont(Font.ITALIC), color = GuestUi.muted)),
            onNavigate,
            bottomGap = JBUI.scale(2),
        )
    }
    private var imageBounds = Rectangle()

    init {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        border = JBUI.Borders.empty(4, 0, 10, 0)
        caption?.let(::add)
        if (!href.isNullOrBlank()) {
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = href
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    if (!imageBounds.contains(event.point) || onNavigate(href)) return
                    if (href.startsWith("https://") || href.startsWith("http://")) BrowserUtil.browse(href)
                }
            })
        }
    }

    override fun setBounds(x: Int, y: Int, w: Int, h: Int) {
        super.setBounds(x, y, w, h)
        doLayout()
    }

    override fun doLayout() {
        val layout = layoutFor(width.coerceAtLeast(1))
        imageBounds = layout.first
        caption?.setBounds(insets.left, imageBounds.y + imageBounds.height + JBUI.scale(5), layout.second, layout.third)
    }

    override fun measureHeight(widthPx: Int): Int {
        val layout = layoutFor(widthPx)
        val captionHeight = if (layout.third > 0) JBUI.scale(5) + layout.third else 0
        return (layout.first.y + layout.first.height + captionHeight + insets.bottom)
            .coerceAtLeast(1)
    }

    override fun getPreferredSize(): Dimension {
        val w = if (width > 1) width else JBUI.scale(PostBodyPane.CONTENT_WIDTH)
        return Dimension(w, measureHeight(w))
    }

    override fun getMaximumSize(): Dimension = Dimension(Integer.MAX_VALUE, preferredSize.height)

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g = graphics as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.color = CODE_BG
        g.fillRoundRect(imageBounds.x, imageBounds.y, imageBounds.width, imageBounds.height, JBUI.scale(8), JBUI.scale(8))
        if (image != null) {
            UIUtil.drawImage(g, image, imageBounds, null, null)
        } else {
            g.color = GuestUi.muted
            g.font = UIUtil.getLabelFont().deriveFont(Font.ITALIC)
            val baseline = imageBounds.y + (imageBounds.height + g.fontMetrics.ascent - g.fontMetrics.descent) / 2
            g.drawString(label.take(64), imageBounds.x + JBUI.scale(12), baseline)
        }
        g.color = JBColor.border()
        g.drawRoundRect(imageBounds.x, imageBounds.y, imageBounds.width - 1, imageBounds.height - 1, JBUI.scale(8), JBUI.scale(8))
    }

    private fun layoutFor(widthPx: Int): Triple<Rectangle, Int, Int> {
        val inner = (widthPx - insets.left - insets.right).coerceAtLeast(1)
        val naturalWidth = declaredWidth ?: image?.let(ImageUtil::getUserWidth)?.takeIf { it > 0 } ?: inner
        val naturalHeight = declaredHeight ?: image?.let(ImageUtil::getUserHeight)?.takeIf { it > 0 } ?: JBUI.scale(112)
        val scale = minOf(
            1.0,
            inner.toDouble() / naturalWidth.coerceAtLeast(1),
            JBUI.scale(720).toDouble() / naturalWidth.coerceAtLeast(1),
            JBUI.scale(560).toDouble() / naturalHeight.coerceAtLeast(1),
        )
        val drawWidth = (naturalWidth * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (naturalHeight * scale).toInt().coerceAtLeast(1)
        val captionHeight = caption?.measureHeight(inner) ?: 0
        return Triple(Rectangle(insets.left, insets.top, drawWidth, scaledHeight), inner, captionHeight)
    }
}

private class OneboxPane(
    block: moe.momokko.intellido.domain.content.CookedBlock.Onebox,
    style: TextStyle,
    onNavigate: (String) -> Boolean,
) : FlowColumn() {
    init {
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1),
            JBUI.Borders.empty(8, 12),
        )
        alignmentX = LEFT_ALIGNMENT
        val title = block.title.trim().ifBlank { block.url }
        val titleAtoms = if (block.url.isNotBlank()) {
            listOf(
                InlineAtom(
                    text = title,
                    font = style.font.deriveFont(Font.BOLD),
                    color = GuestUi.signal,
                    href = block.url,
                    underline = true,
                ),
            )
        } else {
            listOf(InlineAtom(text = title, font = style.font.deriveFont(Font.BOLD), color = style.color))
        }
        add(WrapRunPane(titleAtoms, onNavigate, bottomGap = JBUI.scale(4)))
        if (block.description.isNotBlank()) {
            add(WrapRunPane(listOf(InlineAtom(text = block.description, font = style.font, color = GuestUi.muted)), onNavigate, bottomGap = JBUI.scale(2)))
        }
        if (block.site.isNotBlank()) {
            add(WrapRunPane(listOf(InlineAtom(text = block.site, font = GuestUi.metaFont(style.font), color = GuestUi.muted)), onNavigate, bottomGap = JBUI.scale(2)))
        }
    }
}

private class CalloutPane(
    block: CookedBlock.Callout,
    style: TextStyle,
    media: Map<String, String>,
    onNavigate: (String) -> Boolean,
) : FlowColumn() {
    init {
        val accent = calloutColor(block.kind)
        isOpaque = true
        background = calloutBackground(block.kind)
        border = CompoundBorder(
            MatteBorder(0, JBUI.scale(4), 0, 0, accent),
            EmptyBorder(JBUI.scale(9), JBUI.scale(12), JBUI.scale(9), JBUI.scale(12)),
        )
        val body = PostBodyPane(CookedDocument(block.children), onNavigate, media)
        if (block.collapsible) {
            add(PostFoldPane(block.title, block.title, body, block.initiallyOpen))
        } else {
            add(
                WrapRunPane(
                    listOf(InlineAtom(text = block.title, font = style.font.deriveFont(Font.BOLD), color = accent)),
                    onNavigate,
                    bottomGap = JBUI.scale(6),
                ),
            )
            add(body)
        }
    }
}

private open class RichContentCard(
    title: String,
    detail: String,
    href: String?,
    style: TextStyle,
    onNavigate: (String) -> Boolean,
) : FlowColumn() {
    init {
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1),
            JBUI.Borders.empty(9, 12),
        )
        val titleStyle = style.copy(
            font = style.font.deriveFont(Font.BOLD),
            color = if (href.isNullOrBlank()) style.color else GuestUi.signal,
            href = href,
            underline = !href.isNullOrBlank(),
        )
        add(WrapRunPane(listOf(titleStyle.atom(title)), onNavigate, bottomGap = JBUI.scale(4)))
        if (detail.isNotBlank()) {
            add(
                WrapRunPane(
                    listOf(InlineAtom(text = detail, font = GuestUi.metaFont(style.font), color = GuestUi.muted)),
                    onNavigate,
                    bottomGap = JBUI.scale(2),
                ),
            )
        }
    }
}

private class MediaCardPane(
    block: CookedBlock.Media,
    style: TextStyle,
    onNavigate: (String) -> Boolean,
) : RichContentCard(
    title = block.title.ifBlank {
        when (block.kind) {
            CookedBlock.Media.Kind.Audio -> "音频"
            CookedBlock.Media.Kind.Voice -> "语音消息"
            CookedBlock.Media.Kind.Video -> "视频"
            CookedBlock.Media.Kind.Embed -> "嵌入内容"
        }
    },
    detail = listOfNotNull(
        block.mime,
        block.width?.let { width -> block.height?.let { height -> "$width × $height" } },
        block.src.takeIf { it.isNotBlank() }?.let { src -> runCatching { java.net.URI(src).host }.getOrNull() ?: src },
    ).joinToString(" · "),
    href = block.src.takeIf { it.startsWith("https://") || it.startsWith("http://") },
    style = style,
    onNavigate = onNavigate,
)

private class DefinitionListPane(
    items: List<DefinitionItem>,
    style: TextStyle,
    media: Map<String, String>,
    onNavigate: (String) -> Boolean,
) : FlowColumn() {
    init {
        items.forEach { item ->
            val term = flatten(item.term, style.copy(font = style.font.deriveFont(Font.BOLD)), media)
            if (term.isNotEmpty()) add(WrapRunPane(term, onNavigate, bottomGap = JBUI.scale(4)))
            item.definitions.forEach { definition ->
                val body = PostBodyPane(CookedDocument(definition), onNavigate, media)
                body.border = JBUI.Borders.empty(0, 24, 4, 0)
                add(body)
            }
        }
    }
}

private class ImageGridPane(
    block: CookedBlock.ImageGrid,
    private val style: TextStyle,
    media: Map<String, String>,
    private val onNavigate: (String) -> Boolean,
) : JComponent(), FlowBlock {
    private val columns = if (block.carousel) 1 else block.columns.coerceIn(1, 4)
    private val cells = block.images.map { image -> image to resolveImage(image.src, media) }
    private var rectangles: List<Rectangle> = emptyList()

    init {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        border = JBUI.Borders.empty(4, 0, 10, 0)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                val index = rectangles.indexOfFirst { it.contains(event.point) }
                val target = cells.getOrNull(index)?.first?.original.orEmpty()
                if (target.isBlank() || onNavigate(target)) return
                if (target.startsWith("https://") || target.startsWith("http://")) BrowserUtil.browse(target)
            }
        })
    }

    override fun measureHeight(widthPx: Int): Int = layoutCells(widthPx).second

    override fun getPreferredSize(): Dimension {
        val w = if (width > 1) width else JBUI.scale(PostBodyPane.CONTENT_WIDTH)
        return Dimension(w, measureHeight(w))
    }

    override fun getMaximumSize(): Dimension = Dimension(Integer.MAX_VALUE, preferredSize.height)

    override fun paintComponent(graphics: Graphics) {
        val g = graphics as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        val layout = layoutCells(width.coerceAtLeast(1))
        rectangles = layout.first
        cells.forEachIndexed { index, (image, content) ->
            val rect = rectangles.getOrNull(index) ?: return@forEachIndexed
            g.color = CODE_BG
            g.fillRoundRect(rect.x, rect.y, rect.width, rect.height, JBUI.scale(8), JBUI.scale(8))
            if (content != null) {
                UIUtil.drawImage(g, content, rect, null, null)
            } else if (image.alt.isNotBlank()) {
                g.color = GuestUi.muted
                g.font = style.font.deriveFont(Font.ITALIC)
                g.drawString(image.alt.take(64), rect.x + JBUI.scale(10), rect.y + JBUI.scale(22))
            }
            g.color = JBColor.border()
            g.drawRoundRect(rect.x, rect.y, rect.width - 1, rect.height - 1, JBUI.scale(8), JBUI.scale(8))
        }
    }

    private fun layoutCells(widthPx: Int): Pair<List<Rectangle>, Int> {
        if (cells.isEmpty()) return emptyList<Rectangle>() to JBUI.scale(1)
        val gap = JBUI.scale(8)
        val inner = (widthPx - insets.left - insets.right).coerceAtLeast(1)
        val cellWidth = ((inner - gap * (columns - 1)).coerceAtLeast(columns) / columns).coerceAtLeast(1)
        val rowHeights = cells.chunked(columns).map { row ->
            row.maxOf { (_, image) ->
                if (image == null) JBUI.scale(112) else {
                    val iw = ImageUtil.getUserWidth(image).coerceAtLeast(1)
                    val ih = ImageUtil.getUserHeight(image).coerceAtLeast(1)
                    (ih.toDouble() * cellWidth / iw).toInt().coerceIn(JBUI.scale(72), JBUI.scale(280))
                }
            }
        }
        val result = mutableListOf<Rectangle>()
        var y = insets.top
        cells.forEachIndexed { index, _ ->
            val row = index / columns
            val column = index % columns
            result += Rectangle(insets.left + column * (cellWidth + gap), y, cellWidth, rowHeights[row])
            if (column == columns - 1 || index == cells.lastIndex) y += rowHeights[row] + gap
        }
        return result to (y + insets.bottom).coerceAtLeast(1)
    }
}

private fun calloutColor(kind: CookedBlock.Callout.Kind): JBColor = when (kind) {
    CookedBlock.Callout.Kind.Tip, CookedBlock.Callout.Kind.Success -> JBColor(Color(0x2E, 0x8B, 0x57), Color(0x69, 0xC7, 0x8E))
    CookedBlock.Callout.Kind.Warning, CookedBlock.Callout.Kind.Question -> JBColor(Color(0xB0, 0x72, 0x19), Color(0xE0, 0xA8, 0x42))
    CookedBlock.Callout.Kind.Danger -> JBColor(Color(0xC7, 0x39, 0x2F), Color(0xF0, 0x71, 0x78))
    CookedBlock.Callout.Kind.Important -> JBColor(Color(0x7E, 0x57, 0xC2), Color(0xB3, 0x8D, 0xE8))
    else -> GuestUi.signal
}

private fun calloutBackground(kind: CookedBlock.Callout.Kind): JBColor = when (kind) {
    CookedBlock.Callout.Kind.Warning, CookedBlock.Callout.Kind.Question -> JBColor(Color(0xFF, 0xF8, 0xE7), Color(0x35, 0x30, 0x23))
    CookedBlock.Callout.Kind.Danger -> JBColor(Color(0xFF, 0xF1, 0xF0), Color(0x38, 0x25, 0x27))
    CookedBlock.Callout.Kind.Tip, CookedBlock.Callout.Kind.Success -> JBColor(Color(0xEF, 0xFA, 0xF2), Color(0x22, 0x34, 0x29))
    else -> JBColor(Color(0xF1, 0xF6, 0xFD), Color(0x25, 0x2D, 0x38))
}

private class RulePane : JComponent(), FlowBlock {
    init {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        border = JBUI.Borders.empty(8, 0)
    }

    override fun measureHeight(widthPx: Int): Int = JBUI.scale(16)

    override fun getPreferredSize(): Dimension = Dimension(width.coerceAtLeast(1), JBUI.scale(16))

    override fun getMaximumSize(): Dimension = Dimension(Integer.MAX_VALUE, JBUI.scale(16))

    override fun paintComponent(g: Graphics) {
        g.color = JBColor.border()
        val y = height / 2
        g.drawLine(0, y, width, y)
    }
}

internal data class TextStyle(
    val font: Font,
    val color: Color,
    val href: String? = null,
    val code: Boolean = false,
    val highlight: Boolean = false,
    val strike: Boolean = false,
    val underline: Boolean = false,
    val superscript: Boolean = false,
    val subscript: Boolean = false,
    val background: Color? = null,
    val spoilerId: Int? = null,
)

internal fun flatten(spans: List<CookedSpan>, style: TextStyle, media: Map<String, String>): List<InlineAtom> {
    val out = mutableListOf<InlineAtom>()
    spans.forEach { span -> out += flattenSpan(span, style, media) }
    return out
}

private fun flattenSpan(span: CookedSpan, style: TextStyle, media: Map<String, String>): List<InlineAtom> = when (span) {
    is CookedSpan.Text -> listOf(style.atom(span.text))
    CookedSpan.Break -> listOf(style.atom("\n"))
    is CookedSpan.Strong -> flatten(span.children, style.copy(font = style.font.deriveFont(Font.BOLD)), media)
    is CookedSpan.Emphasis -> flatten(span.children, style.copy(font = style.font.deriveFont(Font.ITALIC)), media)
    is CookedSpan.Strike -> flatten(span.children, style.copy(strike = true, color = GuestUi.muted), media)
    is CookedSpan.Underline -> flatten(span.children, style.copy(underline = true), media)
    is CookedSpan.Highlight -> flatten(span.children, style.copy(highlight = true), media)
    is CookedSpan.Subscript -> flatten(
        span.children,
        style.copy(subscript = true, font = style.font.deriveFont((style.font.size2D * 0.78f).coerceAtLeast(7f))),
        media,
    )
    is CookedSpan.Superscript -> flatten(
        span.children,
        style.copy(superscript = true, font = style.font.deriveFont((style.font.size2D * 0.78f).coerceAtLeast(7f))),
        media,
    )
    is CookedSpan.Colored -> {
        val parsed = cssColor(span.color) ?: style.color
        val background = cssColor(span.background) ?: style.background
        flatten(span.children, style.copy(color = parsed, background = background), media)
    }
    is CookedSpan.Sized -> {
        val size = (style.font.size2D * span.scale.coerceIn(0.5f, 3f)).coerceAtLeast(7f)
        flatten(span.children, style.copy(font = style.font.deriveFont(size)), media)
    }
    is CookedSpan.Code -> listOf(style.copy(code = true, font = Font(Font.MONOSPACED, Font.PLAIN, style.font.size)).atom(span.text))
    is CookedSpan.Kbd -> listOf(style.copy(code = true, font = Font(Font.MONOSPACED, Font.PLAIN, style.font.size)).atom(span.text))
    is CookedSpan.Link -> flattenLink(span, style, media)
    is CookedSpan.Image -> imageAtom(span.src, span.alt, style, media)
    is CookedSpan.Emoji -> emojiAtom(span, style, media)
    is CookedSpan.Spoiler -> flatten(
        span.children,
        style.copy(spoilerId = System.identityHashCode(span)),
        media,
    )
    is CookedSpan.FootnoteRef -> listOf(
        style.copy(
            href = "#${span.id}",
            color = GuestUi.signal,
            superscript = true,
            font = style.font.deriveFont((style.font.size2D * 0.78f).coerceAtLeast(7f)),
        ).atom(span.number),
    )
    is CookedSpan.LocalDate -> listOf(style.copy(code = true, color = GuestUi.signal).atom(span.text.ifBlank { span.date }))
    is CookedSpan.Math -> listOf(
        style.copy(font = Font(Font.SERIF, Font.ITALIC, style.font.size), code = true).atom(span.latex),
    )
    is CookedSpan.ClickCount -> listOf(
        style.copy(font = GuestUi.metaFont(style.font), code = true, color = GuestUi.muted).atom(span.text),
    )
}

private fun flattenLink(span: CookedSpan.Link, style: TextStyle, media: Map<String, String>): List<InlineAtom> {
    val href = if (span.href.startsWith("/")) "https://linux.do${span.href}" else span.href
    if (span.kind == CookedSpan.Link.Kind.Hashtag) {
        val name = span.children.joinToString("") { child ->
            when (child) {
                is CookedSpan.Text -> child.text
                else -> ""
            }
        }.trim().trimStart('#')
        HashtagChips.register()
        val image = InlineMedia.image("intellido-media:chip:$name") ?: InlineMedia.image("chip:$name")
        if (image != null) {
            return listOf(
                InlineAtom(
                    font = style.font,
                    color = GuestUi.signal,
                    href = href,
                    image = image,
                    imageW = ImageUtil.getUserWidth(image).coerceAtLeast(1),
                    imageH = ImageUtil.getUserHeight(image).coerceAtLeast(1),
                    copy = name,
                ),
            )
        }
    }
    return flatten(span.children, style.copy(href = href, color = GuestUi.signal, underline = true), media)
}

private fun imageAtom(src: String, alt: String, style: TextStyle, media: Map<String, String>): List<InlineAtom> {
    val image = resolveImage(src, media) ?: InlineMedia.image(src)
    if (image == null) {
        return if (alt.isBlank()) emptyList() else listOf(style.copy(color = GuestUi.muted, font = style.font.deriveFont(Font.ITALIC)).atom(alt))
    }
    var w = ImageUtil.getUserWidth(image).coerceAtLeast(1)
    var h = ImageUtil.getUserHeight(image).coerceAtLeast(1)
    val chip = src.contains(":chip:") || h <= JBUI.scale(28)
    if (!chip && w > JBUI.scale(720)) {
        val scale = JBUI.scale(720).toFloat() / w
        w = JBUI.scale(720)
        h = (h * scale).toInt().coerceAtLeast(1)
    }
    if (chip) {
        h = h.coerceAtMost(JBUI.scale(HashtagChips.HEIGHT + 4))
    }
    return listOf(
        InlineAtom(
            font = style.font,
            color = style.color,
            href = style.href,
            image = image,
            imageW = w,
            imageH = h,
            copy = alt,
            background = style.background != null,
            spoilerId = style.spoilerId,
        ),
    )
}

private fun emojiAtom(span: CookedSpan.Emoji, style: TextStyle, media: Map<String, String>): List<InlineAtom> {
    val image = if (span.needsImage) resolveImage(span.src, media) ?: InlineMedia.image(span.src) else null
    if (image != null) {
        val edge = JBUI.scale(20)
        return listOf(
            InlineAtom(
                font = style.font,
                color = style.color,
                image = image,
                imageW = edge,
                imageH = edge,
                copy = span.glyph.ifBlank { ":${span.shortcode}:" },
                background = style.background != null,
                spoilerId = style.spoilerId,
            ),
        )
    }
    val glyph = span.glyph.ifBlank { ":${span.shortcode}:" }
    return listOf(style.atom(glyph))
}

private fun resolveImage(src: String, media: Map<String, String>): java.awt.Image? {
    if (src.isBlank()) {
        return null
    }
    InlineMedia.image(src)?.let { return it }
    val mapped = media[src] ?: media.entries.firstOrNull { it.key == src }?.value
    if (!mapped.isNullOrBlank()) {
        InlineMedia.image(mapped)?.let { return it }
        if (mapped.startsWith("intellido-media:") || mapped.startsWith("data:image")) {
            return InlineMedia.image(mapped)
        }
    }
    return null
}

private fun TextStyle.atom(text: String): InlineAtom = InlineAtom(
    text = text,
    font = font,
    color = color,
    href = href,
    copy = text,
    code = code,
    highlight = highlight,
    strike = strike,
    underline = underline,
    superscript = superscript,
    subscript = subscript,
    fill = background ?: if (code) CODE_BG else MARK_BG,
    background = background != null,
    spoilerId = spoilerId,
)

private fun cssColor(raw: String): Color? {
    val value = raw.trim().lowercase()
    if (value.isBlank()) return null
    if (value.startsWith("#")) {
        val hex = value.drop(1)
        val expanded = when (hex.length) {
            3, 4 -> hex.take(3).map { "$it$it" }.joinToString("")
            6, 8 -> hex.take(6)
            else -> return null
        }
        return runCatching { Color(expanded.toInt(16)) }.getOrNull()
    }
    val rgb = Regex("""rgba?\(\s*(\d{1,3})\s*[, ]\s*(\d{1,3})\s*[, ]\s*(\d{1,3})""", RegexOption.IGNORE_CASE)
        .find(value)
    if (rgb != null) {
        return Color(
            rgb.groupValues[1].toInt().coerceIn(0, 255),
            rgb.groupValues[2].toInt().coerceIn(0, 255),
            rgb.groupValues[3].toInt().coerceIn(0, 255),
        )
    }
    val hsl = Regex("""hsla?\(\s*(-?[\d.]+)(?:deg)?\s*[, ]\s*([\d.]+)%\s*[, ]\s*([\d.]+)%""")
        .find(value)
    if (hsl != null) {
        val hue = ((hsl.groupValues[1].toFloatOrNull() ?: 0f) % 360f + 360f) % 360f / 360f
        val saturation = (hsl.groupValues[2].toFloatOrNull() ?: 0f).coerceIn(0f, 100f) / 100f
        val lightness = (hsl.groupValues[3].toFloatOrNull() ?: 0f).coerceIn(0f, 100f) / 100f
        val chroma = (1f - kotlin.math.abs(2f * lightness - 1f)) * saturation
        val section = hue * 6f
        val secondary = chroma * (1f - kotlin.math.abs(section % 2f - 1f))
        val (r, g, b) = when (section.toInt()) {
            0 -> Triple(chroma, secondary, 0f)
            1 -> Triple(secondary, chroma, 0f)
            2 -> Triple(0f, chroma, secondary)
            3 -> Triple(0f, secondary, chroma)
            4 -> Triple(secondary, 0f, chroma)
            else -> Triple(chroma, 0f, secondary)
        }
        val match = lightness - chroma / 2f
        return Color(r + match, g + match, b + match)
    }
    return CSS_NAMED_COLORS[value]
}

private val QUOTE_BG: JBColor = JBColor(Color(0xFB, 0xF7, 0xEA), Color(0x2E, 0x2C, 0x24))
private val CODE_BG: JBColor = JBColor(Color(0xF3, 0xF3, 0xF3), Color(0x2B, 0x2B, 0x2B))
private val MARK_BG: JBColor = JBColor(Color(0xFF, 0xF3, 0xC4), Color(0x4A, 0x3F, 0x1F))
private val TABLE_HEADER_BG: JBColor = JBColor(Color(0xEE, 0xF2, 0xF6), Color(0x32, 0x36, 0x3D))
private val TABLE_STRIPE_BG: JBColor = JBColor(Color(0xFA, 0xFB, 0xFC), Color(0x2A, 0x2D, 0x32))
private val CSS_NAMED_COLORS: Map<String, Color> = mapOf(
    "black" to Color.BLACK,
    "silver" to Color(0xC0C0C0),
    "gray" to Color.GRAY,
    "white" to Color.WHITE,
    "maroon" to Color(0x800000),
    "red" to Color.RED,
    "purple" to Color(0x800080),
    "fuchsia" to Color.MAGENTA,
    "green" to Color(0x008000),
    "lime" to Color.GREEN,
    "olive" to Color(0x808000),
    "yellow" to Color.YELLOW,
    "navy" to Color(0x000080),
    "blue" to Color.BLUE,
    "teal" to Color(0x008080),
    "aqua" to Color.CYAN,
    "orange" to Color(0xFFA500),
    "pink" to Color(0xFFC0CB),
    "brown" to Color(0xA52A2A),
    "gold" to Color(0xFFD700),
    "indigo" to Color(0x4B0082),
    "violet" to Color(0xEE82EE),
    "coral" to Color(0xFF7F50),
    "salmon" to Color(0xFA8072),
)
