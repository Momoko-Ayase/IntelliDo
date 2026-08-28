package moe.momokko.intellido.ui.profile

import moe.momokko.intellido.domain.catalog.ProfileBadge
import moe.momokko.intellido.domain.catalog.ProfileCategoryStat
import moe.momokko.intellido.domain.catalog.ProfileField
import moe.momokko.intellido.domain.catalog.ProfileLink
import moe.momokko.intellido.domain.catalog.ProfilePeer
import moe.momokko.intellido.domain.catalog.ProfileTopicItem
import moe.momokko.intellido.domain.catalog.PublicProfile
import moe.momokko.intellido.domain.catalog.PublicProfileSummary
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.time.Instant
import java.util.Locale
import moe.momokko.intellido.ui.content.PostBodyPane
import javax.swing.JEditorPane
import javax.swing.JLabel

class UserPanelTest {
    @Test
    fun `public profile shows identity and trust level`() {
        val panel = UserPanel(sampleProfile(), Locale.SIMPLIFIED_CHINESE)
        val texts = labels(panel)
        assertTrue(texts.contains("助手"), texts.toString())
        assertTrue(texts.contains("使用向导"), texts.toString())
        assertTrue(texts.any { it.contains("信任级别") && it.contains("成员") }, texts.toString())
        assertTrue(texts.any { it.contains("徽章") }, texts.toString())
    }

    @Test
    fun `public profile shows guest-visible linux do card`() {
        val opened = mutableListOf<String>()
        val users = mutableListOf<String>()
        val panel = UserPanel(
            sampleProfile(),
            Locale.SIMPLIFIED_CHINESE,
            onNavigate = { url -> opened += url; true },
            onOpenUser = { users += it },
        )
        val texts = labels(panel)
        assertTrue(texts.contains("helper"), texts.toString())
        assertTrue(texts.contains("测试城"), texts.toString())
        assertTrue(texts.any { it.contains("所属机构") && it.contains("IntelliDo 测试") }, texts.toString())
        assertTrue(texts.any { it.contains("加入日期") }, texts.toString())
        assertTrue(texts.contains("48") && texts.any { it.contains("浏览量") }, texts.toString())
        assertTrue(texts.contains("3") && texts.any { it.contains("关注者") }, texts.toString())
        assertTrue(texts.any { it.contains("120") } && texts.any { it.contains("点数") }, texts.toString())
        assertTrue(texts.contains("统计信息"), texts.toString())
        assertTrue(texts.contains("9") && texts.any { it.contains("访问天数") }, texts.toString())
        assertTrue(texts.any { it.contains("阅读时间") }, texts.toString())
        assertTrue(texts.any { it.contains("已送出") }, texts.toString())
        assertTrue(texts.any { it.contains("已收到") }, texts.toString())
        assertTrue(texts.any { it.contains("解决方案") }, texts.toString())
        assertTrue(texts.contains("热门回复"), texts.toString())
        assertTrue(texts.contains("热门话题"), texts.toString())
        assertTrue(texts.contains("欢迎使用 IntelliDo"), texts.toString())
        assertTrue(texts.contains("如何阅读话题"), texts.toString())
        assertTrue(texts.contains("热门链接"), texts.toString())
        assertTrue(texts.contains("example.test") || texts.any { it.contains("docs") }, texts.toString())
        assertTrue(texts.contains("公告"), texts.toString())
        assertTrue(texts.contains("系统") || texts.contains("system"), texts.toString())
        assertTrue(texts.contains("首次发帖"), texts.toString())
        val english = UserPanel(
            sampleProfile().copy(
                featuredBadges = listOf(
                    ProfileBadge(4, "Leader", "Granted global edit, pin, close, archive, split and merge, more likes"),
                    ProfileBadge(28, "Popular Link", "Posted an external link with 50 clicks", count = 216),
                    ProfileBadge(112, "Solution Institution", "Have 150 replies marked as Solutions"),
                ),
                summary = sampleProfile().summary?.copy(
                    badges = listOf(
                        ProfileBadge(4, "Leader", "Granted global edit, pin, close, archive, split and merge, more likes"),
                        ProfileBadge(28, "Popular Link", "Posted an external link with 50 clicks", count = 216),
                        ProfileBadge(112, "Solution Institution", "Have 150 replies marked as Solutions"),
                    ),
                ),
            ),
            Locale.SIMPLIFIED_CHINESE,
        )
        val localized = labels(english)
        assertTrue(localized.contains("领导者"), localized.toString())
        assertTrue(localized.contains("热门链接"), localized.toString())
        assertTrue(localized.contains("解决方案机构"), localized.toString())
        assertTrue(localized.any { it.contains("50") && it.contains("次") }, localized.toString())
        assertTrue(localized.none { it.contains("Leader") }, localized.toString())
        assertFalse(texts.contains("私信"), texts.toString())
        assertFalse(texts.any { it == "关注" }, texts.toString())
        val body = editors(panel).joinToString().replace("​", "")
        assertTrue(body.contains("本地假用户") || labels(panel).any { it.replace("​", "").contains("本地假用户") }, body)

        click(panel, "欢迎使用 IntelliDo")
        assertTrue(opened.any { it.contains("/t/") && it.contains("101") }, opened.toString())
        click(panel, "system")
        if (users.isEmpty()) {
            click(panel, "系统")
        }
        assertTrue(users.contains("system"), users.toString())
    }

    private fun sampleProfile(): PublicProfile = PublicProfile(
        id = 2,
        username = "helper",
        displayName = "助手",
        title = "使用向导",
        bioHtml = "<p>IntelliDo 本地假用户，负责公开指南。</p>",
        trustLevel = 2,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        badgeCount = 2,
        location = "测试城",
        website = "https://example.test",
        lastPostedAt = Instant.parse("2026-08-22T02:00:00Z"),
        lastSeenAt = Instant.parse("2026-08-22T03:00:00Z"),
        profileViews = 48,
        publicFields = listOf(ProfileField("所属机构", "IntelliDo 测试")),
        followerCount = 3,
        gamificationScore = 120,
        statusEmoji = "slight_smile",
        statusDescription = "在读指南",
        featuredBadges = listOf(ProfileBadge(1, "首次发帖", "发布第一篇帖子", "pencil")),
        summary = PublicProfileSummary(
            daysVisited = 9,
            timeReadSeconds = 3600,
            recentTimeReadSeconds = 600,
            topicsEntered = 12,
            postsRead = 40,
            likesGiven = 6,
            likesReceived = 4,
            topicCount = 1,
            postCount = 3,
            solvedCount = 1,
            replies = listOf(
                ProfileTopicItem(102, "如何阅读话题", 8, postNumber = 2),
            ),
            topics = listOf(
                ProfileTopicItem(101, "欢迎使用 IntelliDo", 120),
            ),
            links = listOf(ProfileLink("https://example.test/docs", "IntelliDo 说明", 3)),
            topCategories = listOf(ProfileCategoryStat(1, "公告", "F6C344", 2, 4, "announcements")),
            badges = listOf(ProfileBadge(1, "首次发帖", "发布第一篇帖子", "pencil")),
            mostLikedBy = listOf(ProfilePeer(1, "system", "系统", count = 4)),
            mostLiked = listOf(ProfilePeer(1, "system", "系统", count = 2)),
            mostRepliedTo = listOf(ProfilePeer(1, "system", "系统", count = 1)),
        ),
    )

    private fun labels(root: Component): List<String> {
        val out = mutableListOf<String>()
        fun walk(component: Component) {
            if (component is JLabel && component.text.isNotBlank()) {
                out += component.text
            }
            if (component is Container) {
                component.components.forEach(::walk)
            }
        }
        walk(root)
        return out
    }

    private fun editors(root: Component): List<String> {
        val out = mutableListOf<String>()
        fun walk(component: Component) {
            if (component is JEditorPane) {
                val doc = component.document
                val visible = runCatching { doc.getText(0, doc.length) }.getOrDefault(component.text)
                if (visible.isNotBlank()) {
                    out += visible
                }
            }
            if (component is PostBodyPane) {
                val visible = component.visibleText()
                if (visible.isNotBlank()) {
                    out += visible
                }
            }
            if (component is Container) {
                component.components.forEach(::walk)
            }
        }
        walk(root)
        return out
    }

    private fun click(root: Component, text: String) {
        fun walk(component: Component): Boolean {
            if (component is JLabel && component.text == text) {
                component.mouseListeners.forEach { it.mouseClicked(
                    java.awt.event.MouseEvent(component, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false)
                ) }
                return true
            }
            if (component is Container) {
                return component.components.any(::walk)
            }
            return false
        }
        walk(root)
    }
}
