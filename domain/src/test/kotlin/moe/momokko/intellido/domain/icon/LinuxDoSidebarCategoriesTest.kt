package moe.momokko.intellido.domain.icon

import moe.momokko.intellido.domain.catalog.CommunityCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class LinuxDoSidebarCategoriesTest {
    @Test
    fun `guest sidebar matches LINUX DO default navigation menu`() {
        assertEquals(
            listOf(
                "开发调优",
                "国产替代",
                "资源荟萃",
                "文档共建",
                "非我莫属",
                "读书成诗",
                "前沿快讯",
                "网络记忆",
                "福利羊毛",
                "搞七捻三",
                "虫洞广场",
                "运营反馈",
            ),
            LinuxDoSidebarCategories.GUEST.map { it.name },
        )
        assertFalse(LinuxDoSidebarCategories.GUEST.any { it.name == "网盘资源" })
        assertFalse(LinuxDoSidebarCategories.GUEST.any { it.name == "跳蚤市场" })
        assertFalse(LinuxDoSidebarCategories.GUEST.any { it.readRestricted })
        assertEquals(4L, LinuxDoSidebarCategories.GUEST.first { it.name == "开发调优" }.id)
        assertEquals(98L, LinuxDoSidebarCategories.GUEST.first { it.name == "国产替代" }.id)
        assertEquals(14L, LinuxDoSidebarCategories.GUEST.first { it.name == "资源荟萃" }.id)
        assertEquals(34L, LinuxDoSidebarCategories.GUEST.first { it.name == "前沿快讯" }.id)
        assertEquals(92L, LinuxDoSidebarCategories.GUEST.first { it.name == "网络记忆" }.id)
        assertEquals(110L, LinuxDoSidebarCategories.GUEST.first { it.name == "虫洞广场" }.id)
    }

    @Test
    fun `live overlay matches slug or name so a stale seed id cannot swap icons`() {
        val live = listOf(
            category(11, "搞七捻三", "gossip", "43A047", "droplet"),
            category(42, "文档共建", "wiki", "78909C", "book"),
            category(99, "运营反馈", "feedback", "90A4AE", "comments"),
            category(4, "开发调优", "develop", "00AEFF", "code"),
        )
        val overlaid = LinuxDoSidebarCategories.overlay(live).associateBy { it.name }
        assertEquals(11L, overlaid.getValue("搞七捻三").id)
        assertEquals("droplet", overlaid.getValue("搞七捻三").icon)
        assertEquals(99L, overlaid.getValue("运营反馈").id)
        assertEquals("comments", overlaid.getValue("运营反馈").icon)
        assertEquals(42L, overlaid.getValue("文档共建").id)
        assertEquals("book", overlaid.getValue("文档共建").icon)
        assertEquals("code", overlaid.getValue("开发调优").icon)
        assertEquals(
            LinuxDoSidebarCategories.GUEST.map { it.name },
            LinuxDoSidebarCategories.overlay(live).map { it.name },
        )
    }

    @Test
    fun `live overlay prefers the visible name over a colliding slug`() {
        val live = listOf(
            category(77, "搞七捻三", "unrelated", "000000", "droplet"),
            category(88, "闲聊", "gossip", "43A047", "book"),
        )
        val gossip = LinuxDoSidebarCategories.overlay(live).first { it.name == "搞七捻三" }
        assertEquals(77L, gossip.id)
        assertEquals("droplet", gossip.icon)
        assertEquals("unrelated", gossip.slug)
    }

    @Test
    fun `blank live icon does not erase the seed icon`() {
        val live = listOf(
            CommunityCategory(
                id = 42,
                name = "搞七捻三",
                slug = "gossip",
                description = null,
                topicCount = 1,
                readRestricted = false,
                color = "43A047",
                icon = "",
            ),
        )
        val gossip = LinuxDoSidebarCategories.overlay(live).first { it.name == "搞七捻三" }
        assertEquals("droplet", gossip.icon)
        assertEquals(42L, gossip.id)
    }

    private fun category(
        id: Long,
        name: String,
        slug: String,
        color: String,
        icon: String,
    ): CommunityCategory = CommunityCategory(
        id = id,
        name = name,
        slug = slug,
        description = null,
        topicCount = 0,
        readRestricted = false,
        color = color,
        icon = icon,
    )
}
