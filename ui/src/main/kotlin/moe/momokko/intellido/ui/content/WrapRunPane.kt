package moe.momokko.intellido.ui.content

import com.intellij.ide.BrowserUtil
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import moe.momokko.intellido.platform.reading.ReadingAppearance
import moe.momokko.intellido.platform.reading.ReadingStyle
import moe.momokko.intellido.domain.content.CookedAlignment
import moe.momokko.intellido.ui.guest.GuestUi
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.FontRenderContext
import java.awt.font.TextLayout
import java.awt.geom.Rectangle2D
import kotlin.math.roundToInt
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.ceil
import kotlin.math.max

/**
 * One paragraph of cooked inline runs. Wraps with the component FontMetrics so
 * CJK breaks per character and copy is never rewritten with hard line breaks.
 */
internal class WrapRunPane(
    atoms: List<InlineAtom>,
    private val onNavigate: (String) -> Boolean,
    private val bottomGap: Int = JBUI.scale(8),
    private val alignment: CookedAlignment = CookedAlignment.Start,
) : JComponent(), FlowBlock {
    private val atoms: List<InlineAtom> = atoms.filter { it.isPainted() }
    private val revealedSpoilers = mutableSetOf<Int>()
    private var units: List<GlyphUnit> = toUnits(this.atoms, revealedSpoilers)
    private val layoutText: String get() = units.joinToString("") { it.layout }
    private val copyText: String get() = units.joinToString("") { it.copy }
    private val length: Int get() = units.size
    private var draws: List<DrawRun> = emptyList()
    private var lines: List<VisualLine> = emptyList()
    private var laidWidth: Int = -1
    private var laidHeight: Int = 0
    private var laidFromPaint: Boolean = false
    private var selAnchor: Int = -1
    private var selIndex: Int = -1

    init {
        isOpaque = false
        isFocusable = true
        alignmentX = LEFT_ALIGNMENT
        cursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)
        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return
                }
                requestFocusInWindow()
                val index = hitIndex(e.x, e.y)
                selAnchor = index
                selIndex = index
                repaint()
            }

            override fun mouseDragged(e: MouseEvent) {
                if (selAnchor < 0) {
                    return
                }
                selIndex = hitIndex(e.x, e.y)
                repaint()
            }

            override fun mouseReleased(e: MouseEvent) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return
                }
                val index = hitIndex(e.x, e.y)
                if (selAnchor >= 0 && selAnchor == selIndex) {
                    val spoiler = spoilerAt(index)
                    if (spoiler != null) {
                        revealSpoiler(spoiler)
                    } else {
                        hrefAt(index)?.let { open(it) }
                    }
                }
                selIndex = index
                repaint()
            }

            override fun mouseMoved(e: MouseEvent) {
                val index = hitIndex(e.x, e.y)
                val href = hrefAt(index)
                cursor = if (href != null || spoilerAt(index) != null) {
                    Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                } else {
                    Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)
                }
            }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
    }

    fun layoutText(): String = layoutText

    fun copyText(): String = copyText

    fun lineCount(): Int = lines.size

    fun lineAdvances(): List<Float> = lines.map { it.width }

    fun inlineImageCount(): Int = units.count { it.image != null }

    fun charBox(index: Int): Rectangle2D? {
        ensureLayout()
        if (draws.isEmpty() || length <= 0) {
            return null
        }
        val at = index.coerceIn(0, (length - 1).coerceAtLeast(0))
        val run = draws.firstOrNull { at < it.end } ?: draws.last()
        val local = (at - run.start).coerceAtLeast(0)
        val fm = getFontMetrics(run.font)
        val prefix = run.text.take(local.coerceAtMost(run.text.length))
        val x = run.x + if (run.image != null) 0 else fm.stringWidth(prefix)
        val w = when {
            run.image != null -> run.width
            local < run.text.length -> fm.charWidth(run.text[local])
            else -> 1
        }
        return Rectangle2D.Double(x.toDouble(), run.top.toDouble(), w.toDouble(), run.height.toDouble())
    }

    fun clickText(needle: String): Boolean {
        val at = layoutText.indexOf(needle)
        if (at < 0) {
            return false
        }
        val box = charBox(at) ?: return false
        val x = box.centerX.toInt()
        val y = box.centerY.toInt()
        mouseListeners.forEach { listener ->
            listener.mousePressed(MouseEvent(this, MouseEvent.MOUSE_PRESSED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1))
            listener.mouseReleased(MouseEvent(this, MouseEvent.MOUSE_RELEASED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1))
        }
        return true
    }

    fun selectedCopy(): String {
        if (selAnchor < 0 || selIndex < 0 || selAnchor == selIndex) {
            return ""
        }
        val start = minOf(selAnchor, selIndex).coerceIn(0, length)
        val end = maxOf(selAnchor, selIndex).coerceIn(0, length)
        if (end <= start) {
            return ""
        }
        return units.subList(start, end).joinToString("") { it.copy }
    }

    override fun measureHeight(widthPx: Int): Int {
        relayout(widthPx)
        return laidHeight
    }

    override fun getPreferredSize(): Dimension {
        val w = when {
            width > 1 -> width
            parent != null && parent.width > 1 -> {
                val insetX = (parent as? JComponent)?.insets?.let { it.left + it.right } ?: 0
                (parent.width - insetX).coerceAtLeast(1)
            }
            else -> JBUI.scale(PostBodyPane.CONTENT_WIDTH)
        }
        return Dimension(w, measureHeight(w))
    }

    override fun getMaximumSize(): Dimension = Dimension(Integer.MAX_VALUE, preferredSize.height)

    override fun getMinimumSize(): Dimension = Dimension(0, preferredSize.height)

    override fun setBounds(x: Int, y: Int, w: Int, h: Int) {
        super.setBounds(x, y, w, h)
        relayout(w)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        GraphicsUtil.setupAAPainting(g2)
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        val before = laidHeight
        relayout(width, g2)
        if (laidHeight != before) {
            SwingUtilities.invokeLater {
                revalidate()
                (parent as? JComponent)?.revalidate()
            }
        }
        val selStart = minOf(selAnchor, selIndex)
        val selEnd = maxOf(selAnchor, selIndex)
        val hasSel = selAnchor >= 0 && selIndex >= 0 && selEnd > selStart
        draws.forEach { run ->
            if (hasSel) {
                val from = maxOf(selStart, run.start)
                val to = minOf(selEnd, run.end)
                if (to > from) {
                    val fm = getFontMetrics(run.font)
                    val prefix = run.text.take((from - run.start).coerceAtLeast(0))
                    val piece = run.text.take((to - run.start).coerceAtLeast(0)).drop(prefix.length)
                    val x = run.x + if (run.image != null) 0 else fm.stringWidth(prefix)
                    val w = if (run.image != null) run.imageW else fm.stringWidth(piece).coerceAtLeast(1)
                    g2.color = UIUtil.getTreeSelectionBackground(true)
                    g2.fillRect(x, run.baseline - fm.ascent, w, fm.ascent + fm.descent)
                }
            }
            if (run.image != null) {
                val iy = run.baseline - (run.imageH * 85 / 100)
                UIUtil.drawImage(
                    g2,
                    run.image,
                    java.awt.Rectangle(run.x, iy, run.imageW, run.imageH),
                    null,
                    null,
                )
            } else if (run.text.isNotEmpty()) {
                val fm = getFontMetrics(run.font)
                if (run.fill != null) {
                    g2.color = run.fill
                    g2.fillRect(run.x, run.baseline - fm.ascent, run.width, fm.ascent + fm.descent)
                }
                g2.color = run.color
                TextLayout(run.text, run.font, g2.fontRenderContext)
                    .draw(g2, run.x.toFloat(), run.baseline.toFloat())
                if (run.underline) {
                    g2.drawLine(run.x, run.baseline + 1, run.x + run.width, run.baseline + 1)
                }
                if (run.strike) {
                    val mid = run.baseline - fm.ascent / 3
                    g2.drawLine(run.x, mid, run.x + run.width, mid)
                }
            }
        }
    }

    private fun ensureLayout() {
        if (width > 0) {
            relayout(width)
        } else if (lines.isEmpty() && length > 0) {
            relayout(preferredSize.width)
        }
    }

    private fun relayout(widthPx: Int, g2: Graphics2D? = null) {
        val allocated = (widthPx - insets.left - insets.right).coerceAtLeast(1)
        val inner = if (width > 40) {
            minOf(allocated, onScreenTextWidth(this, allocated))
        } else {
            onScreenTextWidth(this, allocated)
        }
        val frc = g2?.fontRenderContext ?: FontRenderContext(null, true, true)
        fun advance(text: String, font: Font): Float {
            if (text.isEmpty()) {
                return 0f
            }
            return TextLayout(text, font, frc).advance
        }
        if (g2 == null && inner == laidWidth && draws.isNotEmpty()) {
            return
        }
        laidWidth = inner
        laidFromPaint = g2 != null
        if (units.isEmpty()) {
            draws = emptyList()
            lines = emptyList()
            laidHeight = bottomGap.coerceAtLeast(1)
            return
        }
        val limit = (inner - 4).coerceAtLeast(1)
        val factor = ReadingAppearance.current.lineHeight.coerceIn(ReadingStyle.MIN_LINE, ReadingStyle.MAX_LINE)
        val nextDraws = mutableListOf<DrawRun>()
        val nextLines = mutableListOf<VisualLine>()
        var x = insets.left
        var y = insets.top
        var lineStart = 0
        var lineAscent = 0
        var lineDescent = 0
        var lineWidth = 0
        var runStart = 0
        var runX = x
        val runText = StringBuilder()
        var runFont: Font? = null
        var runColor: Color? = null
        var runHref: String? = null
        var runFill: Color? = null
        var runUnderline = false
        var runStrike = false
        var runImage: java.awt.Image? = null
        var runImageW = 0
        var runImageH = 0
        var runBaselineShift = 0
        var lineDrawStart = 0

        fun contentHeight(): Int = (lineAscent + lineDescent).coerceAtLeast(1)

        fun lineHeight(): Int {
            val content = contentHeight()
            val extra = (content * (factor - 1f)).toInt().coerceAtLeast(0)
            return content + extra
        }

        fun resetRun() {
            runText.setLength(0)
            runFont = null
            runColor = null
            runHref = null
            runFill = null
            runUnderline = false
            runStrike = false
            runImage = null
            runImageW = 0
            runImageH = 0
            runBaselineShift = 0
        }

        fun flushRun() {
            val font = runFont ?: return
            if (runImage == null && runText.isEmpty()) {
                resetRun()
                return
            }
            val text = runText.toString()
            val w = if (runImage != null) runImageW else advance(text, font).roundToInt()
            val h = lineHeight()
            val top = y
            val baseline = y + lineAscent.coerceAtLeast(1) - runBaselineShift
            nextDraws += DrawRun(
                text = text,
                font = font,
                color = runColor ?: UIUtil.getLabelForeground(),
                x = runX,
                top = top,
                baseline = baseline,
                width = w,
                height = h,
                start = runStart,
                end = runStart + if (runImage != null) 1 else text.length,
                href = runHref,
                fill = runFill,
                underline = runUnderline,
                strike = runStrike,
                image = runImage,
                imageW = runImageW,
                imageH = runImageH,
            )
            resetRun()
        }

        fun alignLine() {
            val free = (limit - lineWidth).coerceAtLeast(0)
            val shift = when (alignment) {
                CookedAlignment.Start -> 0
                CookedAlignment.Center -> free / 2
                CookedAlignment.End -> free
            }
            if (shift > 0) {
                for (i in lineDrawStart until nextDraws.size) {
                    nextDraws[i] = nextDraws[i].copy(x = nextDraws[i].x + shift)
                }
            }
            lineDrawStart = nextDraws.size
        }

        fun newLine() {
            flushRun()
            alignLine()
            val h = lineHeight().coerceAtLeast(1)
            nextLines += VisualLine(width = lineWidth.toFloat(), start = lineStart, end = units.size.coerceAtLeast(lineStart))
            y += h
            x = insets.left
            lineStart = runStart
            lineAscent = 0
            lineDescent = 0
            lineWidth = 0
            runX = x
            runFont = null
        }

        fun sameStyle(unit: GlyphUnit): Boolean =
            runImage == null &&
                unit.image == null &&
                runFont == unit.font &&
                runColor == unit.color &&
                runHref == unit.href &&
                runFill == unit.fill &&
                runUnderline == unit.underline &&
                runStrike == unit.strike &&
                runBaselineShift == unit.baselineShift

        units.forEachIndexed { index, unit ->
            val layout = if (unit.image != null || unit.layout.isEmpty() || unit.layout == "\n") {
                null
            } else {
                TextLayout(unit.layout, unit.font, frc)
            }
            val ascent = if (unit.image != null) {
                unit.imageH * 85 / 100
            } else {
                layout?.ascent?.roundToInt() ?: getFontMetrics(unit.font).ascent
            }
            val descent = if (unit.image != null) {
                (unit.imageH - ascent).coerceAtLeast(0)
            } else {
                layout?.descent?.roundToInt() ?: getFontMetrics(unit.font).descent
            }
            if (unit.layout == "\n") {
                runStart = index
                newLine()
                runStart = index + 1
                return@forEachIndexed
            }
            fun appendedWidth(): Int {
                if (unit.image != null) {
                    return unit.imageW
                }
                if (runFont != null && sameStyle(unit) && runText.isNotEmpty()) {
                    return (advance(runText.toString() + unit.layout, unit.font) -
                        advance(runText.toString(), unit.font)).roundToInt()
                }
                return advance(unit.layout, unit.font).roundToInt()
            }
            if (x + appendedWidth() > insets.left + limit && x > insets.left) {
                newLine()
                runStart = index
            }
            if (runFont != null && !sameStyle(unit)) {
                flushRun()
                runStart = index
                runX = x
            }
            if (runFont == null) {
                runStart = index
                runX = x
                runFont = unit.font
                runColor = unit.color
                runHref = unit.href
                runFill = unit.fill
                runUnderline = unit.underline
                runStrike = unit.strike
                runBaselineShift = unit.baselineShift
            }
            if (unit.image != null) {
                runImage = unit.image
                runImageW = unit.imageW
                runImageH = unit.imageH
                runFont = unit.font
                x += unit.imageW
            } else {
                runText.append(unit.layout)
                x = runX + advance(runText.toString(), unit.font).roundToInt()
            }
            lineAscent = max(lineAscent, ascent + unit.baselineShift.coerceAtLeast(0))
            lineDescent = max(lineDescent, descent + (-unit.baselineShift).coerceAtLeast(0))
            lineWidth = x - insets.left
        }
        if (runFont != null) {
            flushRun()
            alignLine()
            nextLines += VisualLine(width = lineWidth.toFloat(), start = lineStart, end = units.size)
            y += lineHeight()
        }
        draws = nextDraws
        lines = nextLines
        laidHeight = ceil(y + insets.bottom + bottomGap.toFloat()).toInt().coerceAtLeast(1)
    }

    private fun hitIndex(x: Int, y: Int): Int {
        ensureLayout()
        if (draws.isEmpty()) {
            return 0
        }
        val run = draws.lastOrNull { y >= it.top && y < it.top + it.height && x >= it.x } ?:
            draws.lastOrNull { y >= it.top && y < it.top + it.height } ?:
            draws.last()
        if (run.image != null) {
            return if (x < run.x + run.width / 2) run.start else run.end
        }
        val fm = getFontMetrics(run.font)
        var cx = run.x
        run.text.forEachIndexed { i, ch ->
            val w = fm.charWidth(ch)
            if (x < cx + w / 2) {
                return run.start + i
            }
            cx += w
        }
        return run.end
    }

    private fun hrefAt(index: Int): String? {
        if (units.isEmpty()) {
            return null
        }
        return units[index.coerceIn(0, units.lastIndex)].href
    }

    private fun spoilerAt(index: Int): Int? {
        if (units.isEmpty()) return null
        return units[index.coerceIn(0, units.lastIndex)].spoilerId?.takeIf { it !in revealedSpoilers }
    }

    private fun revealSpoiler(id: Int) {
        if (!revealedSpoilers.add(id)) return
        units = toUnits(atoms, revealedSpoilers)
        selAnchor = -1
        selIndex = -1
        laidWidth = -1
        draws = emptyList()
        lines = emptyList()
        revalidate()
        repaint()
    }

    private fun open(raw: String) {
        val absolute = if (raw.startsWith("/")) "https://linux.do$raw" else raw
        if (onNavigate(absolute)) {
            return
        }
        if (absolute.startsWith("https://")) {
            BrowserUtil.browse(absolute)
        }
    }

    private data class GlyphUnit(
        val layout: String,
        val copy: String,
        val font: Font,
        val color: Color,
        val href: String?,
        val fill: Color?,
        val underline: Boolean,
        val strike: Boolean,
        val image: java.awt.Image?,
        val imageW: Int,
        val imageH: Int,
        val baselineShift: Int,
        val spoilerId: Int?,
    )

    private data class DrawRun(
        val text: String,
        val font: Font,
        val color: Color,
        val x: Int,
        val top: Int,
        val baseline: Int,
        val width: Int,
        val height: Int,
        val start: Int,
        val end: Int,
        val href: String?,
        val fill: Color?,
        val underline: Boolean,
        val strike: Boolean,
        val image: java.awt.Image?,
        val imageW: Int = 0,
        val imageH: Int = 0,
    )

    private data class VisualLine(
        val width: Float,
        val start: Int,
        val end: Int,
    )

    companion object {
        private fun toUnits(atoms: List<InlineAtom>, revealedSpoilers: Set<Int>): List<GlyphUnit> {
            val out = mutableListOf<GlyphUnit>()
            atoms.forEach { atom ->
                val hidden = atom.spoilerId != null && atom.spoilerId !in revealedSpoilers
                val shift = when {
                    atom.superscript -> atom.font.size.coerceAtLeast(1) / 3
                    atom.subscript -> -(atom.font.size.coerceAtLeast(1) / 5)
                    else -> 0
                }
                if (atom.image != null && !hidden) {
                    out += GlyphUnit(
                        layout = "￼",
                        copy = atom.copy,
                        font = atom.font,
                        color = atom.color,
                        href = atom.href,
                        fill = null,
                        underline = atom.underline,
                        strike = atom.strike,
                        image = atom.image,
                        imageW = atom.imageW,
                        imageH = atom.imageH,
                        baselineShift = shift,
                        spoilerId = atom.spoilerId,
                    )
                } else {
                    val visibleText = if (hidden && atom.image != null) "●" else atom.text
                    visibleText.forEach { ch ->
                        out += GlyphUnit(
                            layout = if (hidden && !ch.isWhitespace()) "●" else ch.toString(),
                            copy = if (hidden) "" else ch.toString(),
                            font = atom.font,
                            color = if (hidden) SPOILER_MASK else atom.color,
                            href = if (hidden) null else atom.href,
                            fill = if (hidden) SPOILER_MASK else if (atom.code || atom.highlight || atom.background) atom.fill else null,
                            underline = atom.underline,
                            strike = atom.strike,
                            image = null,
                            imageW = 0,
                            imageH = 0,
                            baselineShift = shift,
                            spoilerId = atom.spoilerId,
                        )
                    }
                }
            }
            return out
        }
    }
}

internal data class InlineAtom(
    val text: String = "",
    val font: Font,
    val color: Color,
    val href: String? = null,
    val image: java.awt.Image? = null,
    val imageW: Int = 0,
    val imageH: Int = 0,
    val copy: String = text,
    val code: Boolean = false,
    val highlight: Boolean = false,
    val strike: Boolean = false,
    val underline: Boolean = false,
    val superscript: Boolean = false,
    val subscript: Boolean = false,
    val fill: Color = GuestUi.muted,
    val breakLine: Boolean = false,
    val background: Boolean = false,
    val spoilerId: Int? = null,
) {
    fun isPainted(): Boolean = image != null || text.isNotEmpty() || breakLine
}

private val SPOILER_MASK: Color = com.intellij.ui.JBColor(Color(0x55, 0x55, 0x55), Color(0xB8, 0xB8, 0xB8))
