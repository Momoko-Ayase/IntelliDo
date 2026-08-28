package moe.momokko.intellido.platform.nav

import moe.momokko.intellido.domain.catalog.CommunityCategory
import moe.momokko.intellido.domain.catalog.CommunityTag
import moe.momokko.intellido.domain.icon.LinuxDoSidebarCategories
import moe.momokko.intellido.domain.icon.LinuxDoSidebarTags
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommunityNavModelTest {
    @Test
    fun `guest nav starts with topics and keeps public categories`() {
        val entries = CommunityNavModel.guest(
            listOf(
                CommunityCategory(1, "开发调优", "dev", null, 3, readRestricted = false, color = "00a8ff"),
                CommunityCategory(2, "内部", "staff", null, 1, readRestricted = true),
            ),
        )
        val actions = entries.filterIsInstance<CommunityNavEntry.Action>()
        assertEquals(CommunityNavAction.TOPICS, actions.first().action)
        assertTrue(actions.none { it.action == CommunityNavAction.MY_POSTS || it.action == CommunityNavAction.MY_MESSAGES })
        assertTrue(actions.none { it.needsSignIn })
        assertTrue(entries.any { it is CommunityNavEntry.Category && it.category.name == "开发调优" })
        assertFalse(entries.any { it is CommunityNavEntry.Category && it.category.name == "内部" })
        assertEquals("LINUX DO", CommunityNavModel.TOOL_WINDOW_ID)
    }

    @Test
    fun `guest nav follows LINUX DO sidebar and ignores extra live categories`() {
        val entries = CommunityNavModel.guest(
            listOf(
                CommunityCategory(14, "资源荟萃", "resource", null, 2, false, icon = "square-share-nodes"),
                CommunityCategory(94, "网盘资源", "cloud", null, 1, false, icon = "hard-drive", parentId = 14),
                CommunityCategory(80, "跳蚤市场", "market", null, 1, false),
            ),
        )
        val names = entries.filterIsInstance<CommunityNavEntry.Category>().map { it.category.name }
        assertEquals(LinuxDoSidebarCategories.GUEST.map { it.name }, names)
        assertFalse(names.contains("网盘资源"))
        assertFalse(names.contains("跳蚤市场"))
    }

    @Test
    fun `guest nav lists tags under categories like LINUX DO`() {
        val entries = CommunityNavModel.guest(
            categories = listOf(
                CommunityCategory(1, "开发调优", "dev", null, 3, readRestricted = false, icon = "code"),
            ),
            tags = listOf(
                CommunityTag("intellido", 2),
                CommunityTag("人工智能", 10),
                CommunityTag("公告", 4),
                CommunityTag("faq", 1),
            ),
        )
        val texts = flatten(entries)
        assertEquals("nav.section.categories", texts[texts.indexOf("c:开发调优") - 1])
        assertTrue(texts.contains("ALL_CATEGORIES"))
        val tagHeader = texts.indexOf("nav.section.tags")
        assertTrue(tagHeader > texts.indexOf("c:开发调优"))
        assertEquals("t:人工智能", texts[tagHeader + 1])
        assertEquals("t:公告", texts[tagHeader + 2])
        assertTrue(texts.contains("ALL_TAGS"))
        assertFalse(texts.contains("t:faq"))
        assertTrue(texts.contains("GROUPS"))
        assertTrue(texts.contains("BADGES"))
        assertTrue(texts.contains("ABOUT"))
        assertFalse(texts.contains("MEMBERS"))
        assertFalse(texts.contains("MY_POSTS"))
        assertFalse(texts.contains("MY_MESSAGES"))
        assertFalse(texts.contains("nav.section.directories"))
        assertTrue(texts.contains("nav.section.resources"))
        assertEquals(LinuxDoSidebarTags.GUEST.first(), "人工智能")
    }

    @Test
    fun `guest nav lists Discourse more items above resources`() {
        val texts = flatten(CommunityNavModel.guest(emptyList()))
        assertEquals("TOPICS", texts[0])
        assertEquals("nav.recent", texts[1])
        assertEquals("g:nav.section.more", texts[2])
        assertEquals(listOf("ABOUT", "GROUPS", "BADGES", "nav.leaderboard", "nav.filter"), texts.subList(3, 8))
        assertEquals("nav.section.resources", texts[8])
        assertFalse(texts.contains("MY_POSTS"))
        assertFalse(texts.contains("MY_MESSAGES"))
        val more = CommunityNavModel.guest(emptyList()).filterIsInstance<CommunityNavEntry.Group>().single()
        assertEquals("ellipsis-vertical", more.icon)
        assertFalse(more.expandedByDefault)
    }

    @Test
    fun `guest nav shows sidebar categories before the live catalog arrives`() {
        val entries = CommunityNavModel.guest(emptyList())
        val names = entries.filterIsInstance<CommunityNavEntry.Category>().map { it.category.name }
        assertEquals(LinuxDoSidebarCategories.GUEST.map { it.name }, names)
        assertEquals("nav.section.categories", entries.filterIsInstance<CommunityNavEntry.Header>().first { it.titleKey == "nav.section.categories" }.titleKey)
        assertTrue(entries.any { it is CommunityNavEntry.Tag && it.tag.name == "人工智能" })
    }

    private fun flatten(entries: List<CommunityNavEntry>): List<String> = entries.flatMap { entry ->
        when (entry) {
            is CommunityNavEntry.Header -> listOf(entry.titleKey)
            is CommunityNavEntry.Action -> listOf(entry.action.name)
            is CommunityNavEntry.Category -> listOf("c:${entry.category.name}")
            is CommunityNavEntry.Tag -> listOf("t:${entry.tag.name}")
            is CommunityNavEntry.Link -> listOf(entry.titleKey)
            is CommunityNavEntry.Group -> listOf("g:${entry.titleKey}") + flatten(entry.children)
        }
    }
}
