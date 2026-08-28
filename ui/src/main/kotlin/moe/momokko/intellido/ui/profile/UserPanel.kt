package moe.momokko.intellido.ui.profile

import com.intellij.ide.BrowserUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import moe.momokko.intellido.domain.catalog.ProfileBadge
import moe.momokko.intellido.domain.catalog.ProfileCategoryStat
import moe.momokko.intellido.domain.catalog.ProfileLink
import moe.momokko.intellido.domain.catalog.ProfilePeer
import moe.momokko.intellido.domain.catalog.ProfileTopicItem
import moe.momokko.intellido.domain.catalog.PublicProfile
import moe.momokko.intellido.domain.content.CookedHtmlParser
import moe.momokko.intellido.domain.icon.FaGlyphs
import moe.momokko.intellido.platform.i18n.IntelliDoStrings
import moe.momokko.intellido.platform.topic.DiscourseBadgeLabels
import moe.momokko.intellido.platform.time.RelativeTime
import moe.momokko.intellido.platform.topic.DiscourseNumber
import moe.momokko.intellido.ui.content.PostBodyPane
import moe.momokko.intellido.ui.guest.ChipWrap
import moe.momokko.intellido.ui.guest.EmojiMark
import moe.momokko.intellido.ui.guest.FaMark
import moe.momokko.intellido.ui.guest.GuestAvatar
import moe.momokko.intellido.ui.guest.GuestUi
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.text.NumberFormat
import java.time.Instant
import java.util.Locale
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.SwingConstants

class UserPanel(
    private val profile: PublicProfile,
    private val locale: Locale,
    private val onNavigate: (String) -> Boolean = { false },
    private val onOpenUser: (String) -> Unit = {},
    private val now: Instant = Instant.now(),
) : JBPanel<UserPanel>(BorderLayout()) {
    private val avatars = mutableListOf<GuestAvatar>()
    private val numbers: NumberFormat = NumberFormat.getIntegerInstance(locale)

    init {
        border = JBUI.Borders.empty(16, 24, 28, 24)
        val column = JBPanel<JBPanel<*>>()
        column.layout = BoxLayout(column, BoxLayout.Y_AXIS)
        column.isOpaque = false
        column.add(header())
        column.add(metaBar())
        profile.summary?.takeIf { it.canSeeStats }?.let { summary ->
            column.add(sectionTitle(t("profile.stats")))
            column.add(stats(summary))
            column.add(pair(topicList(t("profile.replies"), summary.replies), topicList(t("profile.topics"), summary.topics)))
            column.add(pair(linkList(summary.links), peerList(t("profile.repliedTo"), summary.mostRepliedTo)))
            column.add(pair(peerList(t("profile.likedBy"), summary.mostLikedBy), peerList(t("profile.liked"), summary.mostLiked)))
            if (summary.topCategories.isNotEmpty()) {
                column.add(categories(summary.topCategories))
            }
            val badges = summary.badges.ifEmpty { profile.featuredBadges }
            if (badges.isNotEmpty()) {
                column.add(badgeList(badges))
            }
        } ?: run {
            if (profile.featuredBadges.isNotEmpty()) {
                column.add(badgeList(profile.featuredBadges))
            }
        }
        column.add(Box.createVerticalGlue())
        val scroll = JBScrollPane(column)
        scroll.border = JBUI.Borders.empty()
        add(scroll, BorderLayout.CENTER)
    }

    fun applyMedia(bytesByUrl: Map<String, ByteArray>) {
        avatars.forEach { it.apply(bytesByUrl) }
    }

    private fun header(): JComponent {
        val header = JBPanel<JBPanel<*>>()
        header.layout = BoxLayout(header, BoxLayout.X_AXIS)
        header.isOpaque = false
        header.alignmentX = LEFT_ALIGNMENT
        val avatar = GuestAvatar(profile.username, profile.avatarUrl(120), 96, 16)
        avatars += avatar
        header.add(avatar)
        val names = JBPanel<JBPanel<*>>()
        names.layout = BoxLayout(names, BoxLayout.Y_AXIS)
        names.isOpaque = false
        names.alignmentY = TOP_ALIGNMENT
        val titleRow = flow()
        val primary = profile.displayName?.takeIf { it.isNotBlank() } ?: profile.username
        titleRow.add(bold(primary, 22f))
        if (profile.admin) {
            titleRow.add(FaMark("shield-halved", GuestUi.muted, 14).also { it.toolTipText = t("profile.admin") })
        } else if (profile.moderator) {
            titleRow.add(FaMark("shield", GuestUi.muted, 14).also { it.toolTipText = t("profile.moderator") })
        }
        flairMark()?.let { titleRow.add(it) }
        profile.statusEmoji?.let { emoji ->
            titleRow.add(EmojiMark(emoji).also { it.toolTipText = profile.statusDescription ?: emoji })
        }
        names.add(titleRow)
        if (profile.displayName != null) {
            names.add(muted(profile.username))
        }
        profile.title?.takeIf { it.isNotBlank() }?.let { names.add(muted(it)) }
        profile.location?.takeIf { it.isNotBlank() }?.let { location ->
            val row = flow()
            row.add(FaMark("location-dot", GuestUi.muted, 12))
            row.add(muted(location))
            names.add(row)
        }
        val bio = profile.bioHtml?.trim().orEmpty()
        if (bio.isNotEmpty()) {
            // `bio_cooked` is community HTML: it must go through the same allowlist
            // parser as post bodies so no remote <img> reaches the Swing HTML pane.
            val document = CookedHtmlParser().parse(bio)
            if (document.blocks.isNotEmpty()) {
                val pane = PostBodyPane(document, onNavigate)
                pane.border = JBUI.Borders.empty(8, 0, 4, 0)
                names.add(pane.wrapped())
            }
        }
        profile.publicFields.forEach { field ->
            names.add(muted(t("profile.field", field.name, field.value)))
        }
        header.add(names)
        header.alignmentX = LEFT_ALIGNMENT
        return header
    }

    private fun metaBar(): JComponent {
        val wrap = ChipWrap(JBUI.scale(18), JBUI.scale(6))
        wrap.alignmentX = LEFT_ALIGNMENT
        wrap.border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1, 0, 1, 0),
            JBUI.Borders.empty(10, 0, 12, 0),
        )
        profile.createdAt?.let { wrap.add(meta(t("profile.joined"), RelativeTime.calendarDate(it, locale))) }
        profile.lastPostedAt?.let { wrap.add(meta(t("profile.lastPosted"), RelativeTime.format(it, now, locale))) }
        profile.lastSeenAt?.let { wrap.add(meta(t("profile.lastSeen"), RelativeTime.format(it, now, locale))) }
        if (profile.profileViews > 0) {
            wrap.add(meta(t("profile.views"), profile.profileViews.toString()))
        }
        wrap.add(meta(t("profile.trustLevelNamed", trustName())))
        if (profile.followerCount > 0) {
            wrap.add(meta(t("profile.followers"), profile.followerCount.toString()))
        }
        if (profile.gamificationScore > 0) {
            wrap.add(meta(t("profile.score"), numbers.format(profile.gamificationScore)))
        }
        return wrap
    }

    private fun stats(summary: moe.momokko.intellido.domain.catalog.PublicProfileSummary): JComponent {
        val wrap = ChipWrap(JBUI.scale(22), JBUI.scale(10))
        wrap.alignmentX = LEFT_ALIGNMENT
        wrap.border = JBUI.Borders.empty(4, 0, 8, 0)
        wrap.add(stat(DiscourseNumber.compact(summary.daysVisited), t("profile.stat.daysVisited")))
        wrap.add(stat(RelativeTime.durationTiny(summary.timeReadSeconds.toLong(), locale), t("profile.stat.timeRead")))
        if (summary.recentTimeReadSeconds > 0) {
            wrap.add(stat(RelativeTime.durationTiny(summary.recentTimeReadSeconds.toLong(), locale), t("profile.stat.recentTimeRead")))
        }
        wrap.add(stat(DiscourseNumber.compact(summary.topicsEntered), t("profile.stat.topicsEntered")))
        wrap.add(stat(DiscourseNumber.compact(summary.postsRead), t("profile.stat.postsRead")))
        wrap.add(stat(DiscourseNumber.compact(summary.likesGiven), t("profile.stat.likesGiven"), "heart"))
        wrap.add(stat(DiscourseNumber.compact(summary.likesReceived), t("profile.stat.likesReceived"), "heart"))
        wrap.add(stat(DiscourseNumber.compact(summary.topicCount), t("profile.stat.topicCount")))
        wrap.add(stat(DiscourseNumber.compact(summary.postCount), t("profile.stat.postCount")))
        if (summary.solvedCount > 0) {
            wrap.add(stat(DiscourseNumber.compact(summary.solvedCount), t("profile.stat.solved"), "square-check"))
        }
        return wrap
    }

    private fun topicList(title: String, items: List<ProfileTopicItem>): JComponent {
        if (items.isEmpty()) {
            return stack()
        }
        val column = stack()
        column.add(sectionTitle(title))
        items.forEach { item ->
            val row = stack()
            val meta = flow()
            item.createdAt?.let { meta.add(muted(RelativeTime.monthYear(it, locale))) }
            if (item.likeCount > 0) {
                meta.add(muted("·"))
                meta.add(FaMark("heart", HEART, 11))
                meta.add(muted(DiscourseNumber.compact(item.likeCount)))
            }
            row.add(meta)
            row.add(link(item.title) { go("https://linux.do${item.path()}") })
            row.border = JBUI.Borders.empty(0, 0, 10, 12)
            column.add(row)
        }
        return column
    }

    private fun linkList(items: List<ProfileLink>): JComponent {
        if (items.isEmpty()) {
            return stack()
        }
        val column = stack()
        column.add(sectionTitle(t("profile.links")))
        items.forEach { item ->
            val row = stack()
            row.add(link(hostOf(item.url)) { go(item.url) })
            item.title?.takeIf { it.isNotBlank() }?.let { row.add(muted(it)) }
            row.border = JBUI.Borders.empty(0, 0, 10, 12)
            column.add(row)
        }
        return column
    }

    private fun peerList(title: String, items: List<ProfilePeer>): JComponent {
        if (items.isEmpty()) {
            return stack()
        }
        val column = stack()
        column.add(sectionTitle(title))
        items.forEach { peer ->
            val row = JBPanel<JBPanel<*>>(BorderLayout())
            row.isOpaque = false
            row.alignmentX = LEFT_ALIGNMENT
            row.border = JBUI.Borders.empty(0, 0, 8, 12)
            val avatar = GuestAvatar(peer.username, peer.avatarUrl(48), 28, 8)
            avatar.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            avatar.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    onOpenUser(peer.username)
                }
            })
            avatars += avatar
            row.add(avatar, BorderLayout.WEST)
            val names = stack()
            val shown = peer.displayName?.takeIf { it.isNotBlank() } ?: peer.username
            names.add(link(shown) { onOpenUser(peer.username) })
            if (peer.displayName != null && !peer.displayName.equals(peer.username, ignoreCase = true)) {
                names.add(link(peer.username) { onOpenUser(peer.username) }.also { it.foreground = GuestUi.muted })
            }
            row.add(names, BorderLayout.CENTER)
            val count = JBLabel(DiscourseNumber.compact(peer.count))
            count.foreground = GuestUi.muted
            count.horizontalAlignment = SwingConstants.RIGHT
            row.add(count, BorderLayout.EAST)
            column.add(row)
        }
        return column
    }

    private fun categories(items: List<ProfileCategoryStat>): JComponent {
        val column = stack()
        column.add(sectionTitle(t("profile.categories")))
        val head = JBPanel<JBPanel<*>>(BorderLayout())
        head.isOpaque = false
        head.alignmentX = LEFT_ALIGNMENT
        head.border = JBUI.Borders.empty(0, 0, 6, 0)
        val east = flow()
        east.add(muted(t("profile.category.topics")))
        east.add(Box.createHorizontalStrut(JBUI.scale(24)))
        east.add(muted(t("profile.category.replies")))
        head.add(east, BorderLayout.EAST)
        column.add(head)
        items.forEach { category ->
            val row = JBPanel<JBPanel<*>>(BorderLayout())
            row.isOpaque = false
            row.alignmentX = LEFT_ALIGNMENT
            row.border = JBUI.Borders.empty(0, 0, 8, 0)
            val badge = GuestUi.categoryBadge(category.name, category.color, category.icon)
            badge.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            badge.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    go("https://linux.do${category.path()}")
                }
            })
            row.add(badge, BorderLayout.WEST)
            val counts = flow()
            counts.add(muted(category.topicCount.toString()))
            counts.add(Box.createHorizontalStrut(JBUI.scale(28)))
            counts.add(muted(category.postCount.toString()))
            row.add(counts, BorderLayout.EAST)
            column.add(row)
        }
        stretch(column)
        return column
    }

    private fun badgeList(items: List<ProfileBadge>): JComponent {
        val column = stack()
        column.add(sectionTitle(t("profile.badgeList")))
        val wrap = ChipWrap(JBUI.scale(12), JBUI.scale(12))
        items.forEach { badge ->
            val card = stack()
            card.border = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 1),
                JBUI.Borders.empty(10, 12, 10, 12),
            )
            val title = flow()
            badge.icon?.let { icon ->
                if (FaGlyphs.get(icon) != null) {
                    title.add(FaMark(icon, GuestUi.signal, 16))
                }
            }
            title.add(bold(DiscourseBadgeLabels.name(badge.id, badge.name, locale), 13f))
            if (badge.count > 1) {
                title.add(muted("×${badge.count}"))
            }
            card.add(title)
            val shownDesc = DiscourseBadgeLabels.description(badge.description, locale)
            if (shownDesc.isNotBlank()) {
                val desc = muted(shownDesc)
                desc.setAllowAutoWrapping(true)
                card.add(desc)
            }
            wrap.add(card)
        }
        column.add(wrap)
        stretch(column)
        return column
    }

    private fun pair(left: JComponent, right: JComponent): JComponent {
        val row = JBPanel<JBPanel<*>>(GridLayout(1, 2, JBUI.scale(24), 0))
        row.isOpaque = false
        row.alignmentX = LEFT_ALIGNMENT
        row.border = JBUI.Borders.empty(4, 0, 8, 0)
        row.add(left)
        row.add(right)
        stretch(row)
        return row
    }

    private fun stat(number: String, label: String, icon: String? = null): JComponent {
        val cell = flow()
        val n = JBLabel(number)
        n.font = n.font.deriveFont(Font.BOLD, 18f)
        cell.add(n)
        if (icon != null && FaGlyphs.get(icon) != null) {
            cell.add(FaMark(icon, if (icon == "heart") HEART else GuestUi.muted, 12))
        }
        cell.add(muted(label))
        return cell
    }

    private fun meta(label: String, value: String = ""): JComponent {
        val row = flow()
        row.add(muted(label))
        if (value.isNotBlank()) {
            val v = JBLabel(value)
            v.font = v.font.deriveFont(Font.BOLD, (v.font.size).toFloat())
            row.add(v)
        }
        return row
    }

    private fun sectionTitle(text: String): JBLabel {
        val label = JBLabel(text)
        label.font = label.font.deriveFont(Font.BOLD, 15f)
        label.alignmentX = LEFT_ALIGNMENT
        label.border = JBUI.Borders.empty(16, 0, 8, 0)
        return label
    }

    private fun bold(text: String, size: Float): JBLabel {
        val label = JBLabel(text)
        label.font = label.font.deriveFont(Font.BOLD, size)
        label.alignmentX = LEFT_ALIGNMENT
        return label
    }

    private fun muted(text: String): JBLabel {
        val label = JBLabel(text)
        label.foreground = GuestUi.muted
        label.font = GuestUi.metaFont(label.font)
        label.alignmentX = LEFT_ALIGNMENT
        return label
    }

    private fun link(text: String, onClick: () -> Unit): JBLabel {
        val label = JBLabel(text)
        label.foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
        label.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        label.alignmentX = LEFT_ALIGNMENT
        label.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1) {
                    onClick()
                }
            }
        })
        return label
    }

    private fun flow(): JBPanel<*> {
        val row = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0))
        row.isOpaque = false
        row.alignmentX = LEFT_ALIGNMENT
        return row
    }

    private fun stack(): JBPanel<*> {
        val column = JBPanel<JBPanel<*>>()
        column.layout = BoxLayout(column, BoxLayout.Y_AXIS)
        column.isOpaque = false
        column.alignmentX = LEFT_ALIGNMENT
        column.alignmentY = TOP_ALIGNMENT
        return column
    }

    private fun stretch(component: JComponent) {
        component.alignmentX = LEFT_ALIGNMENT
        component.maximumSize = Dimension(Integer.MAX_VALUE, component.preferredSize.height)
    }

    private fun flairMark(): JComponent? {
        val icon = profile.flairUrl?.takeIf { !it.startsWith("http") } ?: return null
        if (FaGlyphs.get(icon) == null) {
            return null
        }
        val color = hexColor(profile.flairColor) ?: hexColor(profile.flairBgColor) ?: GuestUi.muted
        return FaMark(icon, JBColor(color, color), 14)
    }

    private fun hexColor(raw: String?): Color? {
        val hex = raw?.trim()?.removePrefix("#") ?: return null
        val full = when (hex.length) {
            3 -> hex.map { "$it$it" }.joinToString("")
            6 -> hex
            else -> return null
        }
        return GuestUi.parseHex(full)
    }

    private fun trustName(): String =
        IntelliDoStrings.messageOrNull("profile.trustLevel.name.${profile.trustLevel}", locale)
            ?: profile.trustLevel.toString()

    private fun go(url: String) {
        if (onNavigate(url)) {
            return
        }
        if (url.startsWith("https://") || url.startsWith("http://")) {
            BrowserUtil.browse(url)
        }
    }

    private fun hostOf(url: String): String {
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.removePrefix("www.")
        return host?.takeIf { it.isNotBlank() } ?: url
    }

    private fun t(key: String, vararg args: Any): String = IntelliDoStrings.message(key, locale, *args)

    companion object {
        val HEART: JBColor = JBColor(0xE45735, 0xFF6B6B)
    }
}
