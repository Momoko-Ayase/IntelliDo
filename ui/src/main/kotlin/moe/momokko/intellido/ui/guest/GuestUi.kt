package moe.momokko.intellido.ui.guest

import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.domain.icon.FaGlyphs
import moe.momokko.intellido.domain.icon.LinuxDoTagIcons
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.InputEvent
import javax.swing.JComponent

/**
 * Guest reading chrome: IntelliJ New UI surfaces plus LINUX DO signal yellow.
 * The yellow rail is the one signature mark; everything else stays quiet.
 */
object GuestUi {
    val signal: JBColor = JBColor(0xF6C344, 0xF6C344)
    val muted: JBColor = JBColor(0x6B6458, 0x9A958C)

    fun avatarFill(username: String): Color {
        val index = (username.hashCode() and 0x7fffffff) % AVATAR_FILLS.size
        val raw = AVATAR_FILLS[index]
        return JBColor(Color(raw), Color(raw))
    }

    fun titleFont(base: Font): Font = base.deriveFont(Font.BOLD, (base.size + 1).toFloat())

    fun metaFont(base: Font): Font = base.deriveFont(Font.PLAIN, (base.size - 1).coerceAtLeast(11).toFloat())

    fun tagBadge(name: String): JComponent {
        val row = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 4, 0))
        row.isOpaque = true
        row.background = JBColor(0xF2F2F2, 0x3A3A3A)
        row.border = JBUI.Borders.empty(0, 4, 0, 6)
        LinuxDoTagIcons.style(name)?.let { style ->
            if (FaGlyphs.get(style.icon) != null) {
                val color = parseHex(style.color) ?: muted
                row.add(FaMark(style.icon, JBColor(color, color)))
            }
        }
        val label = JBLabel(name)
        label.foreground = muted
        label.font = label.font.deriveFont(11f)
        row.add(label)
        return row
    }

    fun categoryBadge(
        name: String,
        hex: String?,
        icon: String? = null,
        restricted: Boolean = false,
    ): JComponent {
        val color = parseHex(hex) ?: Color(0x88, 0x88, 0x88)
        val row = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 4, 0))
        row.isOpaque = false
        val fa = icon?.takeIf { FaGlyphs.get(it) != null }
        if (fa != null) {
            row.add(FaMark(fa, JBColor(color, color)))
        } else {
            row.border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor(color, color), 0, 3, 0, 0),
                JBUI.Borders.empty(0, 6, 0, 0),
            )
        }
        if (restricted) {
            row.add(FaMark("lock", muted, 11))
        }
        val label = JBLabel(name)
        label.foreground = muted
        label.font = label.font.deriveFont(11f)
        row.add(label)
        return row
    }

    /** Mac meta / elsewhere ctrl. Avoids HeadlessToolkit.menuShortcutKeyMaskEx. */
    fun menuShortcutMask(): Int =
        if (SystemInfo.isMac) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK

    fun parseHex(hex: String?): Color? {
        val raw = hex?.trim()?.removePrefix("#") ?: return null
        if (raw.length != 6) {
            return null
        }
        return runCatching { Color(raw.toInt(16)) }.getOrNull()
    }

    private val AVATAR_FILLS: IntArray = intArrayOf(
        0x3D6B8A,
        0x5B8C5A,
        0x8A5A3D,
        0x6B4E8A,
        0x3D8A7A,
        0x8A6B3D,
        0x4E6B8A,
        0x7A3D5B,
    )
}
