package moe.momokko.intellido.domain.icon

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
}
