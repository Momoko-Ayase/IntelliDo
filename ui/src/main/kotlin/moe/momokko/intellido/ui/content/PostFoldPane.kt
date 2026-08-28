package moe.momokko.intellido.ui.content

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.ui.guest.GuestUi
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

class PostFoldPane(
    closedLabel: String,
    openLabel: String,
    body: JComponent,
    startOpen: Boolean = false,
) : JBPanel<PostFoldPane>(BorderLayout()) {
    private var open: Boolean = startOpen
    private val toggle = JBLabel()

    init {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        border = JBUI.Borders.empty(4, 0, 8, 0)
        toggle.foreground = GuestUi.signal
        toggle.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toggle.border = JBUI.Borders.empty(2, 0, 6, 0)
        body.isVisible = startOpen
        body.alignmentX = LEFT_ALIGNMENT
        refresh(closedLabel, openLabel)
        toggle.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                open = !open
                body.isVisible = open
                refresh(closedLabel, openLabel)
                revalidate()
                repaint()
            }
        })
        add(toggle, BorderLayout.NORTH)
        add(body, BorderLayout.CENTER)
    }

    override fun getMaximumSize(): Dimension = Dimension(Integer.MAX_VALUE, preferredSize.height)

    private fun refresh(closedLabel: String, openLabel: String) {
        toggle.text = if (open) "▾ $openLabel" else "▸ $closedLabel"
    }
}

class PostPollPane(
    title: String,
    options: List<String>,
    multiple: Boolean = false,
    status: String? = null,
) : FlowColumn() {
    init {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(GuestUi.signal, 0, 3, 0, 0),
            JBUI.Borders.empty(4, 10, 8, 0),
        )
        val baseFont = JBLabel().font
        add(
            WrapRunPane(
                listOf(InlineAtom(text = title, font = baseFont.deriveFont(java.awt.Font.BOLD), color = GuestUi.muted)),
                { false },
                bottomGap = JBUI.scale(5),
            ),
        )
        options.forEach { option ->
            val marker = if (multiple) "☐" else "○"
            add(
                WrapRunPane(
                    listOf(InlineAtom(text = "$marker  $option", font = baseFont, color = JBLabel().foreground)),
                    { false },
                    bottomGap = JBUI.scale(4),
                ),
            )
        }
        status?.takeIf { it.isNotBlank() }?.let { value ->
            add(
                WrapRunPane(
                    listOf(InlineAtom(text = value, font = GuestUi.metaFont(baseFont), color = GuestUi.muted)),
                    { false },
                    bottomGap = JBUI.scale(2),
                ),
            )
        }
    }
}
