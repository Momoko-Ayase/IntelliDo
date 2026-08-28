package moe.momokko.intellido.ui.topic

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.domain.live.LivePresenceUser
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.ui.guest.GuestAvatar
import moe.momokko.intellido.ui.guest.GuestUi
import java.awt.FlowLayout
import java.util.Locale

/**
 * Guest-visible "is replying" row for public topic presence.
 */
class TopicPresenceBar(
    private val locale: Locale,
) : JBPanel<TopicPresenceBar>(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(2))) {
    init {
        isOpaque = false
        isVisible = false
        border = JBUI.Borders.empty(4, 0, 8, 0)
    }

    fun showUsers(users: List<LivePresenceUser>, avatars: Map<String, ByteArray> = emptyMap()) {
        removeAll()
        if (users.isEmpty()) {
            isVisible = false
            revalidate()
            repaint()
            return
        }
        users.take(5).forEach { user ->
            val avatar = GuestAvatar(user.username, user.avatarUrl(), 24)
            avatar.apply(avatars)
            add(avatar)
        }
        val label = JBLabel(IntelliDoStrings.message("topic.replying", locale))
        label.foreground = GuestUi.muted
        label.font = GuestUi.metaFont(label.font)
        add(label)
        isVisible = true
        revalidate()
        repaint()
    }

    private fun LivePresenceUser.avatarUrl(): String? =
        moe.momokko.intellido.domain.topic.LinuxDoAvatar.url(avatarTemplate, 48)
}
