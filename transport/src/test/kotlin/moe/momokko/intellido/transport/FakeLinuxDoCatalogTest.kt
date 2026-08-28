package moe.momokko.intellido.transport

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FakeLinuxDoCatalogTest {
    private val client = FakeLinuxDoCommunityClient()

    @Test
    fun `anonymous catalogs expose public directories without linux do hosts`() {
        assertEquals(listOf("公告", "使用指南"), client.loadCategories().map { it.name })
        assertTrue(client.loadCategories().none { it.readRestricted })
        assertTrue(client.loadTags().map { it.name }.containsAll(listOf("intellido", "faq", "人工智能", "公告")))
        assertEquals(listOf("everyone"), client.loadGroups().map { it.name })
        assertFalse(client.loadGroups().any { it.name == "staff" })
        assertEquals("首次发帖", client.loadBadges().first().name)
        assertEquals(listOf("system", "helper"), client.loadMembers().map { it.username })
        assertEquals("LINUX DO", client.loadAbout().title)
        assertFalse(client.loadAbout().description.contains("linux.do"))
    }

    @Test
    fun `category tag and search filters stay on the local fake corpus`() {
        assertEquals(listOf(101L, 103L), client.loadCategoryTopics(1).map { it.id })
        assertEquals(listOf(102L), client.loadTagTopics("faq").map { it.id })
        assertEquals(listOf("欢迎使用 IntelliDo"), client.searchPublic("欢迎").map { it.title })
        assertTrue(client.searchPublic("").isEmpty())
    }
}
